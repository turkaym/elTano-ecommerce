package com.eltano.ecommerce.catalog.jobs.api.dto;

import java.util.List;

public record AdminCatalogJobReportDiagnosticsResponse(
        String summary,
        long failedRows,
        List<Row> rows) {

    public record Row(
            int rowNumber,
            String outcome,
            String errorCode,
            String errorMessage,
            String payload) {
    }
}
