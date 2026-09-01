package com.eltano.ecommerce.inventory.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.eltano.ecommerce.catalog.domain.Category;
import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductType;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.catalog.domain.UnitType;
import com.eltano.ecommerce.catalog.repository.CategoryRepository;
import com.eltano.ecommerce.catalog.repository.ProductRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminInventoryExportIntegrationTest {
    private static final String PATH = "/api/admin/inventory/export.xlsx";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    @Autowired MockMvc mvc;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    String categoryName;
    String bulkName;
    String packagedName;
    String firstSku;
    String secondSku;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        categoryName = "=Categoría exportable " + suffix;
        bulkName = "+Producto granel " + suffix;
        packagedName = "-Producto variantes " + suffix;
        firstSku = "+SKU-A-" + suffix;
        secondSku = "@SKU-B-" + suffix;
        Category category = new Category(); category.setName(categoryName); category.setSlug("export-" + suffix); category.setActive(false); categories.save(category);

        Product bulk = product(category, bulkName, ProductType.GRANEL, InventoryPolicy.BULK_WEIGHT);
        bulk.setActive(false); bulk.setDeletedAt(Instant.parse("2026-08-01T10:00:00Z")); bulk.setDeletedBy("admin"); bulk.setDeleteReason("fixture");
        bulk.setStockBaseGrams(1250); bulk.setStockReservedBaseGrams(250); bulk.setLatestUnitCost(new BigDecimal("1234.56")); bulk.setLatestCostUnit("KG");
        bulk.setLatestCostAt(Instant.parse("2026-08-30T15:45:30Z"));
        ProductVariant presentation = variant("=PRESENTACION-" + suffix, 99, 11, true); bulk.addVariant(presentation); products.save(bulk);

        Product packaged = product(category, packagedName, ProductType.ENVASADO, InventoryPolicy.PER_VARIANT);
        ProductVariant first = variant(firstSku, 7, 2, false);
        ProductVariant second = variant(secondSku, 4, 1, true); second.setLatestUnitCost(new BigDecimal("88.75")); second.setLatestCostUnit("UNIDAD");
        second.setLatestCostAt(Instant.parse("2026-08-31T09:10:11Z"));
        packaged.addVariant(second); packaged.addVariant(first); products.save(packaged);

        Product shell = product(category, "@Producto sin inventario " + suffix, ProductType.UNIDAD, InventoryPolicy.PER_VARIANT);
        products.save(shell);
        products.flush();
    }

    @Test
    void requiresAuthenticationAndDoesNotRequireCsrfForAuthenticatedGet() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).with(httpBasic("admin-user", "admin-pass"))).andExpect(status().isOk());
    }

    @Test
    void exportsDeterministicInventoryTargetsWithNativeSafeCellTypes() throws Exception {
        byte[] content = mvc.perform(get(PATH).with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", XLSX))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.matchesPattern(
                        ".*inventario-completo-[0-9]{8}-[0-9]{6}\\.xlsx.*")))
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            var sheet = workbook.getSheetAt(0);
            assertArrayEquals(new String[] {"categoría", "producto", "variante/SKU", "tipo", "stock total",
                    "reservado", "disponible", "unidad", "último costo unitario", "fecha último costo"}, strings(sheet.getRow(0)));
            List<Row> rows = new ArrayList<>();
            sheet.forEach(row -> { if (row.getRowNum() > 0 && categoryName.equals(row.getCell(0).getStringCellValue())) rows.add(row); });
            assertEquals(3, rows.size());
            assertEquals(List.of(bulkName + "|", packagedName + "|" + firstSku, packagedName + "|" + secondSku),
                    rows.stream().map(row -> row.getCell(1).getStringCellValue() + "|" + row.getCell(2).getStringCellValue()).toList());
            assertFalse(rows.stream().anyMatch(row -> row.getCell(1).getStringCellValue().contains("sin inventario")));

            Row bulk = rows.get(0);
            assertEquals("Granel", bulk.getCell(3).getStringCellValue()); assertNumbers(bulk, 1.25, 0.25, 1.0);
            assertEquals("kg", bulk.getCell(7).getStringCellValue()); assertEquals(1234.56, bulk.getCell(8).getNumericCellValue());
            assertEquals(CellType.NUMERIC, bulk.getCell(9).getCellType()); assertEquals("dd/mm/yyyy hh:mm:ss", bulk.getCell(9).getCellStyle().getDataFormatString());

            Row legacyVariant = rows.get(1);
            assertEquals("Variante", legacyVariant.getCell(3).getStringCellValue()); assertNumbers(legacyVariant, 9, 2, 7);
            assertEquals("unidad", legacyVariant.getCell(7).getStringCellValue()); assertEquals(CellType.BLANK, legacyVariant.getCell(8).getCellType());
            assertEquals(CellType.BLANK, legacyVariant.getCell(9).getCellType());

            Row costedVariant = rows.get(2); assertNumbers(costedVariant, 5, 1, 4); assertEquals(88.75, costedVariant.getCell(8).getNumericCellValue());
            for (Row row : rows) for (int index : List.of(0, 1, 2, 3, 7)) assertEquals(CellType.STRING, row.getCell(index).getCellType());
        }
    }

    @Test
    void exposesContentDispositionToTheConfiguredAdminOrigin() throws Exception {
        mvc.perform(options(PATH).header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Expose-Headers", "Content-Disposition"));
    }

    private Product product(Category category, String name, ProductType type, InventoryPolicy policy) {
        Product value = new Product(); value.setName(name); value.setSlug("product-" + UUID.randomUUID()); value.setDescription(name);
        value.setActive(true); value.setCategory(category); value.setProductType(type); value.setInventoryPolicy(policy); value.setStockReservedBaseGrams(0); return value;
    }

    private ProductVariant variant(String sku, int available, int reserved, boolean active) {
        ProductVariant value = new ProductVariant(); value.setSku(sku); value.setUnitType(UnitType.UNIT); value.setUnitLabel("unidad");
        value.setPrice(BigDecimal.ONE); value.setStockAvailable(available); value.setStockReserved(reserved); value.setActive(active); return value;
    }

    private String[] strings(Row row) {
        String[] result = new String[row.getLastCellNum()];
        for (int index = 0; index < result.length; index++) { assertEquals(CellType.STRING, row.getCell(index).getCellType()); result[index] = row.getCell(index).getStringCellValue(); }
        return result;
    }

    private void assertNumbers(Row row, double total, double reserved, double available) {
        for (int index = 4; index <= 6; index++) assertEquals(CellType.NUMERIC, row.getCell(index).getCellType());
        assertEquals(total, row.getCell(4).getNumericCellValue()); assertEquals(reserved, row.getCell(5).getNumericCellValue()); assertEquals(available, row.getCell(6).getNumericCellValue());
    }
}
