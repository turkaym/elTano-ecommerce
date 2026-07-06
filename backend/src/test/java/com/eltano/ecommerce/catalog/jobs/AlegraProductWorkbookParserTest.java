package com.eltano.ecommerce.catalog.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.eltano.ecommerce.common.api.UnprocessableEntityException;

class AlegraProductWorkbookParserTest {

    @Test
    void parsesValidWorkbookIntoNormalizedJsonLines() throws Exception {
        MockMultipartFile file = workbookFile("alegra-productos.xlsx", new String[] {
                "Categoria", "Nombre", "Descripcion", "Referencia", "Codigo", "Unidad de medida", "Precio: General"
        }, new String[][] {
                { "Frutos Secos", "Almendra", "", "ALM-001", "ALT-001", "Unidad", "123.45" },
                { "Quesos", "Queso Azul", "Hormita", "", "QAZ-1", "Kilogramo", "1000" }
        });

        AlegraProductWorkbookParser.ParseResult result = new AlegraProductWorkbookParser().parse(file);

        assertEquals(2, result.acceptedRows());
        assertEquals(0, result.invalidRows());
        assertTrue(result.toJsonLines().contains("\"rowNumber\":2"));
        assertTrue(result.toJsonLines().contains("\"category\":\"Frutos Secos\""));
        assertTrue(result.toJsonLines().contains("\"description\":\"Almendra\""));
        assertTrue(result.toJsonLines().contains("\"code\":\"QAZ-1\""));
    }

    @Test
    void preservesRowLevelValidationCandidatesForProcessorDiagnostics() throws Exception {
        MockMultipartFile file = workbookFile("alegra-productos.xlsx", new String[] {
                "Categoria", "Nombre", "Descripcion", "Referencia", "Codigo", "Unidad de medida", "Precio: General"
        }, new String[][] {
                { "Frutos Secos", "Sin Codigo", "", "", "", "Unidad", "10.00" },
                { "Frutos Secos", "Caja", "", "CAJA-1", "", "Caja", "10.00" },
                { "Frutos Secos", "Precio Malo", "", "BAD-1", "", "Unidad", "precio" }
        });

        AlegraProductWorkbookParser.ParseResult result = new AlegraProductWorkbookParser().parse(file);

        assertEquals(3, result.acceptedRows());
        assertEquals(0, result.invalidRows());
        assertTrue(result.toJsonLines().contains("\"rowNumber\":2"));
        assertTrue(result.toJsonLines().contains("\"code\":\"\""));
        assertTrue(result.toJsonLines().contains("\"unitOfMeasure\":\"Caja\""));
        assertTrue(result.toJsonLines().contains("\"generalPrice\":\"precio\""));
    }

    @Test
    void rejectsWorkbookMissingRequiredHeaderWithoutCreatingRows() throws Exception {
        MockMultipartFile file = workbookFile("alegra-productos.xlsx", new String[] {
                "Categoria", "Nombre", "Descripcion", "Referencia", "Codigo", "Unidad de medida"
        }, new String[][] {
                { "Frutos Secos", "Almendra", "", "ALM-001", "ALT-001", "Unidad" }
        });

        UnprocessableEntityException exception = assertThrows(
                UnprocessableEntityException.class,
                () -> new AlegraProductWorkbookParser().parse(file));

        assertEquals("Alegra workbook is missing required headers", exception.getMessage());
        assertEquals("file", exception.getFieldErrors().getFirst().field());
        assertTrue(exception.getFieldErrors().getFirst().message().contains("Precio: General"));
    }

    @Test
    void rejectsCsvContentEvenWhenNamedXlsx() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "valuation.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "Categoria,Nombre,Stock,Valor\nA,B,1,10".getBytes());

        UnprocessableEntityException exception = assertThrows(
                UnprocessableEntityException.class,
                () -> new AlegraProductWorkbookParser().parse(file));

        assertEquals("Invalid Alegra workbook", exception.getMessage());
        assertEquals("file", exception.getFieldErrors().getFirst().field());
    }

    private MockMultipartFile workbookFile(String filename, String[] headers, String[][] rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Productos");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                for (int columnIndex = 0; columnIndex < rows[rowIndex].length; columnIndex++) {
                    row.createCell(columnIndex).setCellValue(rows[rowIndex][columnIndex]);
                }
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    filename,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray());
        }
    }
}
