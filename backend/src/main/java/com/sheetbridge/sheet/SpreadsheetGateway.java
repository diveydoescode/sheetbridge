package com.sheetbridge.sheet;

import com.sheetbridge.domain.Mapping;

import java.util.LinkedHashMap;
import java.util.Map;

public interface SpreadsheetGateway {

    Map<String, LinkedHashMap<String, String>> readRows(Mapping mapping);

    void upsertRow(Mapping mapping, String rowKey, Map<String, String> payload, long revision);

    void deleteRow(Mapping mapping, String rowKey, long revision);
}
