package com.sheetbridge.sheet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.sheetbridge.config.SheetBridgeProperties;
import com.sheetbridge.domain.Mapping;
import com.sheetbridge.reconcile.Payloads;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Sheets API v4 client. Reads/writes by header row + stable row key.
 * 429/5xx responses retry through {@link BackoffPolicy}.
 */
@Component
public class GoogleSheetsGateway implements SpreadsheetGateway {

    private static final String SHEETS = "https://sheets.googleapis.com/v4/spreadsheets/";

    private final SheetBridgeProperties properties;
    private final ObjectMapper mapper;
    private final BackoffPolicy backoff;
    private final HttpClient http;
    private final LocalSpreadsheetGateway localMirror;

    public GoogleSheetsGateway(
            SheetBridgeProperties properties,
            ObjectMapper mapper,
            LocalSpreadsheetGateway localMirror
    ) {
        this.properties = properties;
        this.mapper = mapper;
        this.localMirror = localMirror;
        this.backoff = new BackoffPolicy(
                properties.getSync().getBackoffBaseMs(),
                properties.getSync().getBackoffMaxMs()
        );
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public boolean isConfigured() {
        return (properties.getGoogle().getAccessToken() != null
                && !properties.getGoogle().getAccessToken().isBlank())
                || (properties.getGoogle().getApplicationCredentials() != null
                && !properties.getGoogle().getApplicationCredentials().isBlank());
    }

    @Override
    public Map<String, LinkedHashMap<String, String>> readRows(Mapping mapping) {
        String range = encodedRange(mapping.getSheetName());
        String body = get(SHEETS + mapping.getSpreadsheetId() + "/values/" + range);
        Map<String, LinkedHashMap<String, String>> rows = parseValues(body, mapping);
        rows.forEach((key, payload) -> localMirror.upsertRow(mapping, key, payload, 1));
        return rows;
    }

    @Override
    public void upsertRow(Mapping mapping, String rowKey, Map<String, String> payload, long revision) {
        List<String> columns = Payloads.parseList(mapping.getColumnsJson());
        Map<String, LinkedHashMap<String, String>> current = readRowsWithoutMirror(mapping);
        List<Object> values = new ArrayList<>();
        for (String column : columns) {
            values.add(Payloads.nv(payload.get(column)));
        }
        try {
            if (current.containsKey(rowKey)) {
                int rowIndex = rowIndexOf(mapping, rowKey);
                String range = encodedRange(mapping.getSheetName() + "!A" + rowIndex + ":" + colLetter(columns.size()) + rowIndex);
                putValues(mapping.getSpreadsheetId(), range, List.of(values));
            } else {
                postAppend(mapping.getSpreadsheetId(), encodedRange(mapping.getSheetName()), List.of(values));
            }
            localMirror.upsertRow(mapping, rowKey, payload, revision);
        } catch (IOException e) {
            throw new SheetsQuotaException("Sheets write failed for " + rowKey, e);
        }
    }

    @Override
    public void deleteRow(Mapping mapping, String rowKey, long revision) {
        // Sheets has no first-class delete-by-key; clear the data row in place.
        List<String> columns = Payloads.parseList(mapping.getColumnsJson());
        try {
            int rowIndex = rowIndexOf(mapping, rowKey);
            List<Object> blank = new ArrayList<>();
            for (int i = 0; i < columns.size(); i++) {
                blank.add("");
            }
            String range = encodedRange(mapping.getSheetName() + "!A" + rowIndex + ":" + colLetter(columns.size()) + rowIndex);
            putValues(mapping.getSpreadsheetId(), range, List.of(blank));
            localMirror.deleteRow(mapping, rowKey, revision);
        } catch (IOException e) {
            throw new SheetsQuotaException("Sheets delete failed for " + rowKey, e);
        }
    }

    Map<String, LinkedHashMap<String, String>> parseValues(String body, Mapping mapping) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode values = root.path("values");
            Map<String, LinkedHashMap<String, String>> rows = new LinkedHashMap<>();
            if (!values.isArray() || values.isEmpty()) {
                return rows;
            }
            List<String> headers = new ArrayList<>();
            JsonNode headerRow = values.get(0);
            headerRow.forEach(cell -> headers.add(cell.asText()));
            String keyCol = mapping.getRowKeyColumn();
            int keyIndex = headers.indexOf(keyCol);
            if (keyIndex < 0) {
                throw new SheetsQuotaException("Row key column '" + keyCol + "' missing from sheet header");
            }
            for (int i = 1; i < values.size(); i++) {
                JsonNode row = values.get(i);
                LinkedHashMap<String, String> payload = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    String cell = c < row.size() ? row.get(c).asText() : "";
                    payload.put(headers.get(c), cell);
                }
                String key = Payloads.nv(payload.get(keyCol));
                if (!key.isBlank()) {
                    rows.put(key, payload);
                }
            }
            return rows;
        } catch (IOException e) {
            throw new SheetsQuotaException("Cannot parse Sheets response", e);
        }
    }

    private Map<String, LinkedHashMap<String, String>> readRowsWithoutMirror(Mapping mapping) {
        String range = encodedRange(mapping.getSheetName());
        String body = get(SHEETS + mapping.getSpreadsheetId() + "/values/" + range);
        return parseValues(body, mapping);
    }

    private int rowIndexOf(Mapping mapping, String rowKey) {
        String range = encodedRange(mapping.getSheetName());
        String body = get(SHEETS + mapping.getSpreadsheetId() + "/values/" + range);
        try {
            JsonNode values = mapper.readTree(body).path("values");
            JsonNode headerRow = values.get(0);
            int keyIndex = -1;
            for (int i = 0; i < headerRow.size(); i++) {
                if (mapping.getRowKeyColumn().equals(headerRow.get(i).asText())) {
                    keyIndex = i;
                    break;
                }
            }
            for (int r = 1; r < values.size(); r++) {
                JsonNode row = values.get(r);
                if (keyIndex >= 0 && keyIndex < row.size() && rowKey.equals(row.get(keyIndex).asText())) {
                    return r + 1;
                }
            }
        } catch (IOException e) {
            throw new SheetsQuotaException("Cannot locate row " + rowKey, e);
        }
        throw new SheetsQuotaException("Row " + rowKey + " not found in sheet");
    }

    private String get(String url) {
        int max = properties.getSync().getSheetsMaxAttempts();
        return backoff.execute(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .header("Authorization", "Bearer " + accessToken())
                        .GET()
                        .timeout(Duration.ofSeconds(20))
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                return handle(response);
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new SheetsQuotaException("Sheets GET failed", e);
            }
        }, GoogleSheetsGateway::retryable, max);
    }

    private void putValues(String spreadsheetId, String range, List<List<Object>> values) throws IOException {
        String url = SHEETS + spreadsheetId + "/values/" + range + "?valueInputOption=RAW";
        String json = mapper.writeValueAsString(Map.of("range", java.net.URLDecoder.decode(range, StandardCharsets.UTF_8), "values", values));
        mutate("PUT", url, json);
    }

    private void postAppend(String spreadsheetId, String range, List<List<Object>> values) throws IOException {
        String url = SHEETS + spreadsheetId + "/values/" + range + ":append?valueInputOption=RAW&insertDataOption=INSERT_ROWS";
        String json = mapper.writeValueAsString(Map.of("values", values));
        mutate("POST", url, json);
    }

    private void mutate(String method, String url, String json) {
        int max = properties.getSync().getSheetsMaxAttempts();
        backoff.execute(() -> {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                        .header("Authorization", "Bearer " + accessToken())
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(20));
                HttpRequest request = "PUT".equals(method)
                        ? builder.PUT(HttpRequest.BodyPublishers.ofString(json)).build()
                        : builder.POST(HttpRequest.BodyPublishers.ofString(json)).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                handle(response);
                return Boolean.TRUE;
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new SheetsQuotaException("Sheets " + method + " failed", e);
            }
        }, GoogleSheetsGateway::retryable, max);
    }

    private String handle(HttpResponse<String> response) {
        int code = response.statusCode();
        if (code == 429 || code == 500 || code == 502 || code == 503) {
            throw new SheetsQuotaException("Sheets HTTP " + code + ": " + response.body());
        }
        if (code >= 400) {
            throw new SheetsQuotaException("Sheets HTTP " + code + ": " + response.body());
        }
        return response.body();
    }

    private static boolean retryable(Exception ex) {
        if (ex instanceof SheetsQuotaException quota) {
            String msg = quota.getMessage() == null ? "" : quota.getMessage();
            return msg.contains("HTTP 429") || msg.contains("HTTP 500")
                    || msg.contains("HTTP 502") || msg.contains("HTTP 503");
        }
        return false;
    }

    private String accessToken() {
        String configured = properties.getGoogle().getAccessToken();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String file = properties.getGoogle().getApplicationCredentials();
        if (file == null || file.isBlank()) {
            throw new SheetsQuotaException("Google Sheets credentials are not configured");
        }
        try (FileInputStream in = new FileInputStream(file)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(in)
                    .createScoped(List.of("https://www.googleapis.com/auth/spreadsheets"));
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (IOException e) {
            throw new SheetsQuotaException("Cannot load Google credentials", e);
        }
    }

    private static String encodedRange(String range) {
        return URLEncoder.encode(range, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String colLetter(int size) {
        int n = size;
        StringBuilder sb = new StringBuilder();
        while (n > 0) {
            n--;
            sb.insert(0, (char) ('A' + (n % 26)));
            n /= 26;
        }
        return sb.toString();
    }
}
