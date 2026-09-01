package com.eltano.ecommerce.procurement.draft.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class PurchaseWorkbookParserTest {
    private final PurchaseWorkbookParser parser = new PurchaseWorkbookParser(new SupplierProductNameNormalizer());

    @Test
    void parsesSupportedDatesUnitsAndExactQuantities() throws Exception {
        var result = parser.parse(file(workbook(List.of(
                row("2026-08-29", "Cafe", "1.125", "kg"),
                row("29/8/2026", "Galletas", "2", "unidad"))), "compra.xlsx", xlsxMime()));
        assertEquals(LocalDate.of(2026, 8, 29), result.purchaseDate());
        assertEquals(2, result.lines().size());
        assertTrue(result.lines().stream().allMatch(line -> line.errors().isEmpty()));
        assertEquals(new BigDecimal("112.50"), result.lines().getFirst().lineTotal());
    }

    @Test
    void keepsInvalidDecimalsScientificAndAmbiguousValuesAsRowErrors() throws Exception {
        var result = parser.parse(file(workbook(List.of(
                row("2026-08-29", "A", "1.0001", "kg"),
                row("2026-08-29", "B", "1e3", "unidad"),
                row("2026-08-29", "C", "1,5", "kg"))), "compra.xlsx", xlsxMime()));
        assertTrue(result.lines().stream().allMatch(line -> !line.errors().isEmpty()));
    }

    @Test
    void marksMixedDatesAndExactNormalizedDuplicatesAsBlockingErrors() throws Exception {
        var result = parser.parse(file(workbook(List.of(
                row("2026-08-29", "Cafe-premium", "1.5", "kg"),
                row("2026-08-29", "CAFE premium", "1.500", "kg"),
                row("2026-08-30", "Otro", "1", "unidad"))), "compra.xlsx", xlsxMime()));
        assertTrue(result.lines().get(0).errors().stream().anyMatch(value -> value.contains("duplicada")));
        assertTrue(result.lines().stream().allMatch(line -> line.errors().stream().anyMatch(value -> value.contains("misma fecha"))));
    }

    @Test
    void rejectsFormulaDuplicateHeadersAndAdditionalNonEmptySheet() throws Exception {
        byte[] formula = custom(workbook -> {
            addHeaders(workbook.createSheet("Compra"));
            var row = workbook.getSheetAt(0).createRow(1);
            row.createCell(0).setCellValue("2026-08-29"); row.createCell(1).setCellValue("Cafe");
            row.createCell(2, CellType.FORMULA).setCellFormula("1+1"); row.createCell(3).setCellValue("unidad");
        });
        assertCode("INVALID_XLSX", () -> parser.parse(file(formula, "formula.xlsx", xlsxMime())));

        byte[] duplicateHeader = custom(workbook -> {
            var sheet = workbook.createSheet("Compra");
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("fecha"); row.createCell(1).setCellValue("producto");
            row.createCell(2).setCellValue("cantidad"); row.createCell(3).setCellValue("unidad"); row.createCell(4).setCellValue("unidad");
        });
        assertCode("INVALID_XLSX", () -> parser.parse(file(duplicateHeader, "headers.xlsx", xlsxMime())));

        byte[] sheets = custom(workbook -> { addHeaders(workbook.createSheet("Uno")); addHeaders(workbook.createSheet("Dos")); });
        assertCode("INVALID_XLSX", () -> parser.parse(file(sheets, "sheets.xlsx", xlsxMime())));
    }

    @Test
    void rejectsMoreThanOneThousandRowsRatherThanTruncating() throws Exception {
        byte[] content = custom(workbook -> {
            var sheet = workbook.createSheet("Compra"); addHeaders(sheet);
            for (int index = 1; index <= 1001; index++) {
                var row = sheet.createRow(index); row.createCell(0).setCellValue("2026-08-29");
                row.createCell(1).setCellValue("Producto " + index); row.createCell(2).setCellValue("1"); row.createCell(3).setCellValue("unidad"); row.createCell(4).setCellValue("100");
            }
        });
        assertCode("INVALID_XLSX", () -> parser.parse(file(content, "large.xlsx", xlsxMime())));
    }

    @Test
    void rejectsWrongExtensionMimeAndInvalidSignature() {
        assertCode("INVALID_XLSX", () -> parser.parse(file(new byte[] {1, 2, 3}, "compra.xls", "application/vnd.ms-excel")));
        assertCode("INVALID_XLSX", () -> parser.parse(file(new byte[] {1, 2, 3}, "compra.xlsx", xlsxMime())));
        assertCode("INVALID_XLSX", () -> parser.parse(file(new byte[] {1, 2, 3}, "compra.xlsm", xlsxMime())));
    }

    @Test
    void acceptsOctetStreamButRejectsUnknownMime() throws Exception {
        byte[] valid = workbook(List.<String[]>of(row("2026-08-29", "Cafe", "1", "kg")));
        assertFalse(parser.parse(file(valid, "compra.xlsx", "application/octet-stream")).lines().isEmpty());
        assertCode("INVALID_XLSX", () -> parser.parse(file(valid, "compra.xlsx", "text/plain")));
    }

    @Test
    void acceptsNativeExcelDateCell() throws Exception {
        byte[] content = custom(workbook -> {
            var sheet = workbook.createSheet("Compra"); addHeaders(sheet);
            var style = workbook.createCellStyle(); style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            var row = sheet.createRow(1); var date = row.createCell(0); date.setCellValue(LocalDate.of(2026, 8, 29)); date.setCellStyle(style);
            row.createCell(1).setCellValue("Cafe"); row.createCell(2).setCellValue(1); row.createCell(3).setCellValue("unidad"); row.createCell(4).setCellValue(100.50);
        });
        assertEquals(LocalDate.of(2026, 8, 29), parser.parse(file(content, "fecha.xlsx", xlsxMime())).purchaseDate());
    }

    @Test
    void treatsNativeIsoAndLocalFormsOfTheSameDateAsDuplicates() throws Exception {
        byte[] content = custom(workbook -> {
            var sheet = workbook.createSheet("Compra"); addHeaders(sheet);
            var style = workbook.createCellStyle(); style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            var nativeRow = sheet.createRow(1); var nativeDate = nativeRow.createCell(0);
            nativeDate.setCellValue(LocalDate.of(2026, 8, 29)); nativeDate.setCellStyle(style);
            nativeRow.createCell(1).setCellValue("Cafe"); nativeRow.createCell(2).setCellValue(1); nativeRow.createCell(3).setCellValue("unidad"); nativeRow.createCell(4).setCellValue("100");
            for (int index = 2; index <= 3; index++) {
                var row = sheet.createRow(index); row.createCell(0).setCellValue(index == 2 ? "2026-08-29" : "29/8/2026");
                row.createCell(1).setCellValue("Cafe"); row.createCell(2).setCellValue(1); row.createCell(3).setCellValue("unidad"); row.createCell(4).setCellValue("100");
            }
        });
        var result = parser.parse(file(content, "fechas.xlsx", xlsxMime()));
        assertTrue(result.lines().stream().allMatch(line -> line.errors().stream().anyMatch(error -> error.contains("duplicada"))));
    }

    @Test
    void acceptsPlainDotCommaAndNativePricesAndRejectsInvalidPriceRepresentations() throws Exception {
        byte[] content = custom(workbook -> {
            var sheet = workbook.createSheet("Compra"); addHeaders(sheet);
            String[] prices = {"10.25", "10,25", "10.25", "", "0", "-1", "1.234", "1e3", "$10", "1,000.00", "100000000000000000"};
            for (int index = 0; index < prices.length; index++) {
                var row = sheet.createRow(index + 1); row.createCell(0).setCellValue("2026-08-29"); row.createCell(1).setCellValue("P" + index);
                row.createCell(2).setCellValue("2"); row.createCell(3).setCellValue("unidad"); row.createCell(4).setCellValue(prices[index]);
            }
            sheet.getRow(3).getCell(4).setCellValue(10.25);
        });
        var lines = parser.parse(file(content, "precios.xlsx", xlsxMime())).lines();
        assertTrue(lines.get(0).errors().isEmpty());
        assertTrue(lines.get(1).errors().isEmpty());
        assertEquals(new BigDecimal("20.50"), lines.get(1).lineTotal());
        assertTrue(lines.get(2).errors().isEmpty());
        assertTrue(lines.subList(3, lines.size()).stream().allMatch(line -> !line.errors().isEmpty()));
    }

    @Test
    void requiresTheExactPriceHeader() throws Exception {
        byte[] content = custom(workbook -> {
            var sheet = workbook.createSheet("Compra"); var header = sheet.createRow(0);
            String[] headers = {"fecha", "producto", "cantidad", "unidad", "precio_total"};
            for (int index = 0; index < headers.length; index++) header.createCell(index).setCellValue(headers[index]);
        });
        assertCode("INVALID_XLSX", () -> parser.parse(file(content, "encabezado.xlsx", xlsxMime())));
    }

    private static String[] row(String date, String product, String quantity, String unit) { return new String[] {date, product, quantity, unit, "100"}; }
    private static byte[] workbook(List<String[]> rows) throws Exception { return custom(workbook -> { var sheet = workbook.createSheet("Compra"); addHeaders(sheet); int index = 1; for (String[] values : rows) { var row = sheet.createRow(index++); for (int column = 0; column < values.length; column++) row.createCell(column).setCellValue(values[column]); } }); }
    private static void addHeaders(org.apache.poi.ss.usermodel.Sheet sheet) { var row = sheet.createRow(0); String[] headers = {"fecha", "producto", "cantidad", "unidad", "precio_unitario"}; for (int index = 0; index < headers.length; index++) row.createCell(index).setCellValue(headers[index]); }
    private static byte[] custom(WorkbookWriter writer) throws Exception { try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) { writer.write(workbook); workbook.write(output); return output.toByteArray(); } }
    private static MockMultipartFile file(byte[] value, String name, String mime) { return new MockMultipartFile("file", name, mime, value); }
    private static String xlsxMime() { return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; }
    private static void assertCode(String code, org.junit.jupiter.api.function.Executable executable) { PurchaseDraftException exception = assertThrows(PurchaseDraftException.class, executable); assertEquals(code, exception.getCode()); }
    @FunctionalInterface private interface WorkbookWriter { void write(XSSFWorkbook workbook) throws Exception; }
}
