package com.sheetbridge.reconcile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class Payloads {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, String>> MAP_TYPE = new TypeReference<>() {
    };

    private Payloads() {
    }

    public static Map<String, String> parse(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            LinkedHashMap<String, String> parsed = MAPPER.readValue(json, MAP_TYPE);
            return parsed == null ? new LinkedHashMap<>() : parsed;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid payload JSON", e);
        }
    }

    public static String stringify(Map<String, String> payload) {
        try {
            return MAPPER.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize payload", e);
        }
    }

    public static List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON list", e);
        }
    }

    public static String stringifyList(List<String> values) {
        try {
            return MAPPER.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize list", e);
        }
    }

    public static String nv(String value) {
        return value == null ? "" : value;
    }

    public static boolean equal(Map<String, String> left, Map<String, String> right, List<String> columns) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        for (String column : columns) {
            if (!nv(left.get(column)).equals(nv(right.get(column)))) {
                return false;
            }
        }
        return true;
    }

    public static Map<String, String> copy(Map<String, String> payload) {
        return payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    }

    public static Map<String, String> project(Map<String, String> payload, List<String> columns) {
        Map<String, String> projected = new LinkedHashMap<>();
        Map<String, String> source = payload == null ? Collections.emptyMap() : payload;
        for (String column : columns) {
            projected.put(column, nv(source.get(column)));
        }
        return projected;
    }

    public static String hash(Map<String, String> payload) {
        TreeMap<String, String> canonical = new TreeMap<>();
        if (payload != null) {
            payload.forEach((k, v) -> canonical.put(k, nv(v)));
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(stringify(canonical).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Map<String, String> mergePicks(
            Map<String, String> snapshot,
            Map<String, String> sheet,
            Map<String, String> db,
            List<String> columns,
            Map<String, String> fieldPicks
    ) {
        Map<String, String> merged = new LinkedHashMap<>();
        for (String column : columns) {
            String pick = fieldPicks == null ? null : fieldPicks.get(column);
            if ("SHEET".equalsIgnoreCase(pick)) {
                merged.put(column, nv(sheet == null ? null : sheet.get(column)));
            } else if ("DB".equalsIgnoreCase(pick)) {
                merged.put(column, nv(db == null ? null : db.get(column)));
            } else if ("SNAPSHOT".equalsIgnoreCase(pick)) {
                merged.put(column, nv(snapshot == null ? null : snapshot.get(column)));
            } else if (fieldPicks != null && fieldPicks.containsKey(column)
                    && !"SHEET".equalsIgnoreCase(pick)
                    && !"DB".equalsIgnoreCase(pick)
                    && !"SNAPSHOT".equalsIgnoreCase(pick)) {
                merged.put(column, nv(fieldPicks.get(column)));
            } else {
                String sheetVal = nv(sheet == null ? null : sheet.get(column));
                String dbVal = nv(db == null ? null : db.get(column));
                String snapVal = nv(snapshot == null ? null : snapshot.get(column));
                if (sheetVal.equals(dbVal) || dbVal.equals(snapVal)) {
                    merged.put(column, sheetVal);
                } else if (sheetVal.equals(snapVal)) {
                    merged.put(column, dbVal);
                } else {
                    merged.put(column, sheetVal);
                }
            }
        }
        return merged;
    }

    public static boolean payloadEquals(String leftJson, String rightJson) {
        return Objects.equals(parse(leftJson), parse(rightJson));
    }
}
