package com.eltano.ecommerce.catalog.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.eltano.ecommerce.common.api.UnprocessableEntityException;

class CatalogSalePriceWorkbookParserTest {
    private final CatalogSalePriceWorkbookParser parser = new CatalogSalePriceWorkbookParser();

    @Test
    void parsesPositivePricesWithCentsAndReportsDuplicateKeysWithoutMutatingAnything() throws Exception {
        byte[] workbook = workbook(false,
                new String[] { "SKU", "ABC-1", "Almendra", "250g", "2500.00", "2750.50" },
                new String[] { "SKU", "abc-1", "Almendra", "250g", "2500.00", "2800.00" });

        var parsed = parser.parse(upload(workbook));

        assertThat(parsed.rows()).hasSize(2);
        assertThat(parsed.rows().get(0).newPrice()).isEqualByComparingTo("2750.50");
        assertThat(parsed.rows().get(1).errors()).contains("La clave esta duplicada dentro del archivo.");
    }

    @Test
    void rejectsFormulaCells() throws Exception {
        assertThatThrownBy(() -> parser.parse(upload(workbook(true,
                new String[] { "SKU", "ABC-1", "Almendra", "250g", "2500.00", "2750.00" }))))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("formulas");
    }

    @Test
    void reportsZeroNegativeMalformedAndUnsupportedRows() throws Exception {
        var parsed = parser.parse(upload(workbook(false,
                new String[] { "OTRO", "ABC-1", "Almendra", "250g", "2500", "0" },
                new String[] { "SKU", "ABC-2", "Nuez", "500g", "2500", "1e3" })));

        assertThat(parsed.rows().get(0).errors()).contains("El tipo de clave no es compatible.");
        assertThat(parsed.rows().get(0).errors()).anyMatch(error -> error.contains("precio_nuevo"));
        assertThat(parsed.rows().get(1).errors()).anyMatch(error -> error.contains("precio_nuevo"));
    }

    @Test
    void rejectsUnexpectedHeaders() throws Exception {
        byte[] content;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var row = workbook.createSheet("Precios").createRow(0);
            row.createCell(0).setCellValue("sku");
            workbook.write(output);
            content = output.toByteArray();
        }
        assertThatThrownBy(() -> parser.parse(upload(content)))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("encabezados");
    }

    private byte[] workbook(boolean formula, String[]... values) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Precios");
            var header = sheet.createRow(0);
            String[] headers = { "tipo_clave", "clave", "producto", "presentacion", "precio_actual", "precio_nuevo" };
            for (int column = 0; column < headers.length; column++) header.createCell(column).setCellValue(headers[column]);
            for (int index = 0; index < values.length; index++) {
                var row = sheet.createRow(index + 1);
                for (int column = 0; column < values[index].length; column++) row.createCell(column).setCellValue(values[index][column]);
                if (formula) row.getCell(5).setCellFormula("1+1");
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private MockMultipartFile upload(byte[] content) {
        return new MockMultipartFile("file", "precios.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
    }
}
