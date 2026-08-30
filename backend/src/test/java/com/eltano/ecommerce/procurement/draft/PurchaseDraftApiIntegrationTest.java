package com.eltano.ecommerce.procurement.draft;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.UUID;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.eltano.ecommerce.procurement.domain.Supplier;
import com.eltano.ecommerce.procurement.repository.SupplierRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseDraftApiIntegrationTest {
    private static final String BASE = "/api/admin/procurement/purchase-drafts";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SupplierRepository suppliers;
    UUID supplierId;
    byte[] workbook;

    @BeforeEach
    void setUp() throws Exception {
        Supplier supplier = new Supplier();
        supplier.setName("Proveedor " + UUID.randomUUID()); supplier.setActive(true);
        supplierId = suppliers.saveAndFlush(supplier).getId();
        workbook = workbook();
    }

    @Test
    void templateAndSourceDownloadRequireAdminAuthentication() throws Exception {
        mvc.perform(get(BASE + "/template")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Se requiere autenticacion"));
        mvc.perform(get(BASE + "/template").with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", XLSX));

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

    private String upload(String key, byte[] content, boolean created) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "compra.xlsx", XLSX, content);
        String body = mvc.perform(multipart(BASE + "/imports").file(file).param("supplierId", supplierId.toString())
                        .header("Idempotency-Key", key).with(httpBasic("admin-user", "admin-pass")).with(csrf()))
                .andExpect(created ? status().isCreated() : status().isOk())
                .andExpect(jsonPath("$.lines[0].productName").value("Cafe"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    private byte[] workbook() throws Exception { return workbook("Cafe"); }
    private byte[] workbook(String product) throws Exception {
        try (XSSFWorkbook value = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = value.createSheet("Compra");
            var header = sheet.createRow(0);
            String[] headers = {"fecha", "producto", "cantidad", "unidad"};
            for (int index = 0; index < headers.length; index++) header.createCell(index).setCellValue(headers[index]);
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("2026-08-29"); row.createCell(1).setCellValue(product);
            row.createCell(2).setCellValue("1.250"); row.createCell(3).setCellValue("kg");
            value.write(output); return output.toByteArray();
        }
    }
}
