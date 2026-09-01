package com.eltano.ecommerce.procurement.draft;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.UUID;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.eltano.ecommerce.procurement.domain.Supplier;
import com.eltano.ecommerce.procurement.repository.SupplierRepository;
import com.eltano.ecommerce.catalog.domain.Category;
import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductType;
import com.eltano.ecommerce.catalog.repository.CategoryRepository;
import com.eltano.ecommerce.catalog.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseDraftApiIntegrationTest {
    private static final String BASE = "/api/admin/procurement/purchase-drafts";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SupplierRepository suppliers;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired JdbcTemplate jdbc;
    UUID supplierId;
    UUID targetId;
    byte[] workbook;

    @BeforeEach
    void setUp() throws Exception {
        Supplier supplier = new Supplier();
        supplier.setName("Proveedor " + UUID.randomUUID()); supplier.setActive(true);
        supplierId = suppliers.saveAndFlush(supplier).getId();
        Category category = new Category(); category.setName("Draft API " + UUID.randomUUID()); category.setSlug("draft-api-" + UUID.randomUUID()); category.setActive(true); categories.save(category);
        Product product = new Product(); product.setName("NUEZ MARIPOSA"); product.setSlug("nuez-mariposa-" + UUID.randomUUID()); product.setDescription("NUEZ MARIPOSA");
        product.setActive(true); product.setCategory(category); product.setProductType(ProductType.GRANEL); product.setInventoryPolicy(InventoryPolicy.BULK_WEIGHT); product.setStockBaseGrams(1000); product.setStockReservedBaseGrams(0);
        targetId = products.saveAndFlush(product).getId();
        workbook = workbook();
    }

    @Test
    void templateAndSourceDownloadRequireAdminAuthentication() throws Exception {
        mvc.perform(get(BASE + "/template")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Se requiere autenticacion"));
        byte[] template = mvc.perform(get(BASE + "/template").with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", XLSX))
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook generated = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            var headerRow = generated.getSheetAt(0).getRow(0);
            assertArrayEquals(new String[] {"fecha", "producto", "cantidad", "unidad", "precio_unitario"},
                    new String[] {headerRow.getCell(0).getStringCellValue(), headerRow.getCell(1).getStringCellValue(),
                            headerRow.getCell(2).getStringCellValue(), headerRow.getCell(3).getStringCellValue(), headerRow.getCell(4).getStringCellValue()});
            org.junit.jupiter.api.Assertions.assertEquals(org.apache.poi.ss.usermodel.CellType.NUMERIC, generated.getSheetAt(0).getRow(1).getCell(4).getCellType());
        }

        String draftId = upload("download-key", workbook, true);
        mvc.perform(get(BASE + "/{id}/source-file", draftId)).andExpect(status().isUnauthorized());
        byte[] downloaded = mvc.perform(get(BASE + "/{id}/source-file", draftId).with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(workbook, downloaded);
    }

    @Test
    void multipartImportRequiresCsrfAndReusesSameUnconfirmedSupplierHash() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "compra.xlsx", XLSX, workbook);
        mvc.perform(multipart(BASE + "/imports").file(file).param("supplierId", supplierId.toString())
                        .header("Idempotency-Key", "csrf-key").with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_FORBIDDEN"));

        String draftId = upload("first-key", workbook, true);
        MockMultipartFile replay = new MockMultipartFile("file", "renamed.xlsx", XLSX, workbook);
        mvc.perform(multipart(BASE + "/imports").file(replay).param("supplierId", supplierId.toString())
                        .header("Idempotency-Key", "another-key").with(httpBasic("admin-user", "admin-pass")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(draftId))
                .andExpect(jsonPath("$.reused").value(true));
        MockMultipartFile conflict = new MockMultipartFile("file", "other.xlsx", XLSX, workbook("Otro"));
        mvc.perform(multipart(BASE + "/imports").file(conflict).param("supplierId", supplierId.toString())
                        .header("Idempotency-Key", "another-key").with(httpBasic("admin-user", "admin-pass")).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void invalidWorkbookReturnsStableSpanishError() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "compra.xlsx", XLSX, new byte[] {1, 2, 3});
        mvc.perform(multipart(BASE + "/imports").file(file).param("supplierId", supplierId.toString())
                        .header("Idempotency-Key", "invalid-key").with(httpBasic("admin-user", "admin-pass")).with(csrf()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_XLSX"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("XLSX")));
    }

    @Test
    void manualDraftRequiresPlainUnitPriceAndReturnsComputedCostEvidence() throws Exception {
        String valid = "{\"supplierId\":\"" + supplierId + "\",\"purchaseDate\":\"2026-08-29\",\"lines\":[{\"productName\":\"Cafe\",\"quantity\":\"2\",\"unit\":\"UNIDAD\",\"unitPrice\":\"125,50\"}]}";
        mvc.perform(post(BASE).with(httpBasic("admin-user", "admin-pass")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.lines[0].sourceUnitPrice").value("125,50"))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(125.50)).andExpect(jsonPath("$.lines[0].lineTotal").value(251.00))
                .andExpect(jsonPath("$.lines[0].pricingUnit").value("UNIDAD")).andExpect(jsonPath("$.lines[0].currency").value("ARS"));

        String invalid = "{\"supplierId\":\"" + supplierId + "\",\"purchaseDate\":\"2026-08-29\",\"lines\":[{\"productName\":\"Cafe\",\"quantity\":\"2\",\"unit\":\"UNIDAD\",\"unitPrice\":\"1e3\"}]}";
        mvc.perform(post(BASE).with(httpBasic("admin-user", "admin-pass")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.lines[0].matchStatus").value("INVALID"))
                .andExpect(jsonPath("$.lines[0].unitPrice").doesNotExist()).andExpect(jsonPath("$.lines[0].errors[0]").value(org.hamcrest.Matchers.containsString("precio unitario")));
    }

    @Test
    void draftDtoKeepsExactTargetLabelAndConfirmedEvidenceIdsAfterReload() throws Exception {
        String draftId = upload("evidence-upload", workbook, true);
        JsonNode imported = mapper.readTree(mvc.perform(get(BASE + "/{id}", draftId).with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String lineId = imported.at("/lines/0/id").asText();
        JsonNode matched = mapper.readTree(mvc.perform(put(BASE + "/{id}/lines/{lineId}/match", draftId, lineId)
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + imported.get("version").asLong() + ",\"targetId\":\"" + targetId + "\",\"remember\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].targetLabel").value("NUEZ MARIPOSA (a granel)"))
                .andExpect(jsonPath("$.lines[0].canonicalDelta").value(1250)).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/admin/procurement/mappings").param("supplierId", supplierId.toString()).with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].supplierItemCode").value("Cafe"))
                .andExpect(jsonPath("$[0].supplierItemName").value("Cafe"));
        JsonNode preview = mapper.readTree(mvc.perform(post(BASE + "/{id}/preview", draftId)
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + matched.get("version").asLong() + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        jdbc.update("update purchase_draft_lines set target_label=null where id=?", UUID.fromString(lineId));
        JsonNode confirmation = mapper.readTree(mvc.perform(post(BASE + "/{id}/confirm", draftId)
                        .header("Idempotency-Key", "evidence-confirm").with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + preview.get("version").asLong() + ",\"previewHash\":\"" + preview.get("previewHash").asText() + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        Product renamed = products.findById(targetId).orElseThrow();
        renamed.setName("NUEZ RENOMBRADA");
        products.saveAndFlush(renamed);

        mvc.perform(get(BASE + "/{id}", draftId).with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedPurchaseId").value(confirmation.get("purchaseId").asText()))
                .andExpect(jsonPath("$.confirmedReceiptId").value(confirmation.get("receiptId").asText()))
                .andExpect(jsonPath("$.lines[0].targetLabel").value("NUEZ MARIPOSA (a granel)"))
                .andExpect(jsonPath("$.lines[0].targetLabelPersisted").value(true));
        jdbc.update("update purchase_draft_lines set target_label=null where id=?", UUID.fromString(lineId));
        mvc.perform(get(BASE + "/{id}", draftId).with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].targetLabel").value("NUEZ RENOMBRADA (a granel)"))
                .andExpect(jsonPath("$.lines[0].targetLabelPersisted").value(false));
    }

    private String upload(String key, byte[] content, boolean created) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "compra.xlsx", XLSX, content);
        String body = mvc.perform(multipart(BASE + "/imports").file(file).param("supplierId", supplierId.toString())
                        .header("Idempotency-Key", key).with(httpBasic("admin-user", "admin-pass")).with(csrf()))
                .andExpect(created ? status().isCreated() : status().isOk())
                .andExpect(jsonPath("$.lines[0].productName").value("Cafe"))
                .andExpect(jsonPath("$.lines[0].sourceUnitPrice").value("2500,50"))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(2500.50))
                .andExpect(jsonPath("$.lines[0].lineTotal").value(3125.63))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    private byte[] workbook() throws Exception { return workbook("Cafe"); }
    private byte[] workbook(String product) throws Exception {
        try (XSSFWorkbook value = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = value.createSheet("Compra");
            var header = sheet.createRow(0);
            String[] headers = {"fecha", "producto", "cantidad", "unidad", "precio_unitario"};
            for (int index = 0; index < headers.length; index++) header.createCell(index).setCellValue(headers[index]);
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("2026-08-29"); row.createCell(1).setCellValue(product);
            row.createCell(2).setCellValue("1.250"); row.createCell(3).setCellValue("kg"); row.createCell(4).setCellValue("2500,50");
            value.write(output); return output.toByteArray();
        }
    }
}
