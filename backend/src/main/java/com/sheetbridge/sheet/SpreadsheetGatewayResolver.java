package com.sheetbridge.sheet;

import com.sheetbridge.domain.Mapping;
import com.sheetbridge.domain.SourceType;
import org.springframework.stereotype.Component;

@Component
public class SpreadsheetGatewayResolver {

    private final LocalSpreadsheetGateway local;
    private final GoogleSheetsGateway google;

    public SpreadsheetGatewayResolver(LocalSpreadsheetGateway local, GoogleSheetsGateway google) {
        this.local = local;
        this.google = google;
    }

    public SpreadsheetGateway resolve(Mapping mapping) {
        if (mapping.getSourceType() == SourceType.GOOGLE_SHEETS && google.isConfigured()) {
            return google;
        }
        return local;
    }
}
