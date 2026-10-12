package com.nexus.core.system;

import java.util.List;

/** Response body of {@code GET /api/v1/system/info}. */
public record SystemInfo(String application, String service, List<String> supportedCurrencies) {
}
