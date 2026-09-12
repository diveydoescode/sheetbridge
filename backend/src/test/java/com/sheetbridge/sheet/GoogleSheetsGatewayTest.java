package com.sheetbridge.sheet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sheetbridge.config.SheetBridgeProperties;
import com.sheetbridge.domain.Mapping;
import com.sheetbridge.domain.SourceType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleSheetsGatewayTest {

    @Test
    void parseValuesUsesHeaderRowAndStableKey() throws Exception {
        GoogleSheetsGateway gateway = new GoogleSheetsGateway(
                new SheetBridgeProperties(), new ObjectMapper(), null);
        Mapping mapping = new Mapping();
        mapping.setRowKeyColumn("sku");
        mapping.setSheetName("Inventory");
        mapping.setSourceType(SourceType.GOOGLE_SHEETS);
        String body = """
                {"values":[
                  ["sku","quantity","status"],
                  ["CB-100","12","IN_STOCK"],
                  ["","",""],
                  ["ML-200","3","LOW"]
                ]}
                """;
        Map<String, LinkedHashMap<String, String>> rows = gateway.parseValues(body, mapping);
        assertEquals(2, rows.size());
        assertEquals("12", rows.get("CB-100").get("quantity"));
        assertEquals("LOW", rows.get("ML-200").get("status"));
        assertTrue(!rows.containsKey(""));
    }
}
