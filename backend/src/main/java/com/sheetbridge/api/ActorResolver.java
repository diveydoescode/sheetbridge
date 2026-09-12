package com.sheetbridge.api;

import com.sheetbridge.config.SheetBridgeProperties;
import org.springframework.stereotype.Component;

@Component
public class ActorResolver {

    private final SheetBridgeProperties properties;

    public ActorResolver(SheetBridgeProperties properties) {
        this.properties = properties;
    }

    public String resolve(String header) {
        if (header == null || header.isBlank()) {
            return properties.getDefaultActor();
        }
        return header.trim();
    }
}
