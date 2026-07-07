package com.eltano.ecommerce.catalog.jobs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.eltano.ecommerce.common.api.UnprocessableEntityException;

@Component
public class AlegraProductWorkbookParser {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final int MAX_ROWS = 2_000;
    private static final List<String> REQUIRED_HEADERS = List.of(
            "Categoria",
            "Nombre",
            "Descripcion",
            "Unidad de medida",
            "Precio: General");

    private final DataFormatter dataFormatter = new DataFormatter(Locale.ROOT);

    public ParseResult parse(MultipartFile file) {
        validateFile(file);
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) {
                throw invalidWorkbook("Workbook has no sheets");
            }
            Map<String, Integer> headers = headers(sheet.getRow(0));
            validateHeaders(headers);

            List<RowPayload> rows = new ArrayList<>();
            int invalidRows = 0;
            int lastRow = Math.min(sheet.getLastRowNum(), MAX_ROWS);
            for (int rowIndex = 1; rowIndex <= lastRow; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || blank(row)) {
                    continue;
                }
                RowPayload payload = rowPayload(row, headers);
                if (payload == null) {
                    invalidRows++;
                    continue;
                }
                rows.add(payload);
            }
            return new ParseResult(rows, invalidRows);
        } catch (NotOfficeXmlFileException | EncryptedDocumentException ex) {
            throw invalidWorkbook("File content must be a valid .xlsx workbook");
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof UnprocessableEntityException validationError) {
                throw validationError;
            }
            throw invalidWorkbook("File content must be a valid .xlsx workbook");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw validationError("Alegra workbook is required", "Workbook file must not be empty");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".csv")) {
            throw validationError(
                    "Alegra inventory valuation imports are out of scope",
                    "This importer only accepts the Alegra Productos de venta .xlsx export. Do not upload Valor de inventario CSV files here.");
        }
        if (!filename.endsWith(".xlsx")) {
            throw validationError("Unsupported Alegra import file", "Alegra import requires a .xlsx workbook");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw validationError("Alegra workbook is too large", "Workbook must be 5MB or smaller");
        }
    }

    private Map<String, Integer> headers(Row headerRow) {
        if (headerRow == null) {
            throw validationError("Alegra workbook is missing required headers", "Header row is required");
        }
        Map<String, Integer> headers = new LinkedHashMap<>();
        for (int column = 0; column < headerRow.getLastCellNum(); column++) {
            String value = text(headerRow, column);
            if (!value.isBlank()) {
                headers.put(normalizeHeader(value), column);
            }
        }
        return headers;
    }

    private void validateHeaders(Map<String, Integer> headers) {
        List<String> missing = new ArrayList<>();
        for (String requiredHeader : REQUIRED_HEADERS) {
            if (!headers.containsKey(normalizeHeader(requiredHeader))) {
                missing.add(requiredHeader);
            }
        }
        if (!headers.containsKey(normalizeHeader("Referencia")) && !headers.containsKey(normalizeHeader("Codigo"))) {
            missing.add("Referencia or Codigo");
        }
        if (!missing.isEmpty()) {
            throw validationError(
                    "Alegra workbook is missing required headers",
                    "Missing required header: " + String.join(", ", missing));
        }
    }

    private RowPayload rowPayload(Row row, Map<String, Integer> headers) {
        String category = text(row, headers.get(normalizeHeader("Categoria")));
        String name = text(row, headers.get(normalizeHeader("Nombre")));
        String description = text(row, headers.get(normalizeHeader("Descripcion")));
        String reference = optionalText(row, headers.get(normalizeHeader("Referencia")));
        String code = reference.isBlank() ? optionalText(row, headers.get(normalizeHeader("Codigo"))) : reference;
        String unit = text(row, headers.get(normalizeHeader("Unidad de medida")));
        String price = text(row, headers.get(normalizeHeader("Precio: General")));
        if (category.isBlank() || name.isBlank()) {
            return null;
        }
        return new RowPayload(row.getRowNum() + 1, category, name, description.isBlank() ? name : description, code, unit, price);
    }

    private boolean blank(Row row) {
        for (int column = 0; column < row.getLastCellNum(); column++) {
            if (!text(row, column).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String text(Row row, Integer column) {
        if (column == null) {
            return "";
        }
        return dataFormatter.formatCellValue(row.getCell(column)).trim();
    }

    private String optionalText(Row row, Integer column) {
        return column == null ? "" : text(row, column);
    }

    private String normalizeHeader(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private UnprocessableEntityException invalidWorkbook(String detail) {
        return validationError("Invalid Alegra workbook", detail);
    }

    private UnprocessableEntityException validationError(String message, String fieldMessage) {
        return new UnprocessableEntityException(
                message,
                List.of(new UnprocessableEntityException.FieldError("file", fieldMessage)));
    }

    public record RowPayload(
            int rowNumber,
            String category,
            String name,
            String description,
            String code,
            String unitOfMeasure,
            String generalPrice) {
    }

    public record ParseResult(List<RowPayload> rows, int invalidRows) {
        public int acceptedRows() {
            return rows.size();
        }

        public String toJsonLines() {
            return rows.stream()
                    .map(this::toJson)
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
        }

        private String toJson(RowPayload row) {
            return "{"
                    + "\"rowNumber\":" + row.rowNumber()
                    + ",\"category\":\"" + escapeJson(row.category()) + "\""
                    + ",\"name\":\"" + escapeJson(row.name()) + "\""
                    + ",\"description\":\"" + escapeJson(row.description()) + "\""
                    + ",\"code\":\"" + escapeJson(row.code()) + "\""
                    + ",\"unitOfMeasure\":\"" + escapeJson(row.unitOfMeasure()) + "\""
                    + ",\"generalPrice\":\"" + escapeJson(row.generalPrice()) + "\""
                    + "}";
        }

        private String escapeJson(String value) {
            StringBuilder escaped = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char current = value.charAt(i);
                switch (current) {
                    case '\\' -> escaped.append("\\\\");
                    case '"' -> escaped.append("\\\"");
                    case '\n' -> escaped.append("\\n");
                    case '\r' -> escaped.append("\\r");
                    case '\t' -> escaped.append("\\t");
                    default -> escaped.append(current);
                }
            }
            return escaped.toString();
        }
    }
}
