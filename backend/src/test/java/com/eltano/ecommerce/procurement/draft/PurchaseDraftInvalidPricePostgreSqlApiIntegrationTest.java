package com.eltano.ecommerce.procurement.draft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.eltano.ecommerce.procurement.PostgreSqlIntegrationSupport;
import com.eltano.ecommerce.procurement.domain.Supplier;
import com.eltano.ecommerce.procurement.repository.SupplierRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseDraftInvalidPricePostgreSqlApiIntegrationTest extends PostgreSqlIntegrationSupport {
    private static final String BASE = "/api/admin/procurement/purchase-drafts";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SupplierRepository suppliers;
    @Autowired JdbcTemplate jdbc;

    @Test
    void invalidUnitPriceImportPersistsRawValueAndErrorsWithNullCostGroup() throws Exception {
        Supplier supplier = new Supplier(); supplier.setName("Proveedor " + UUID.randomUUID()); supplier.setActive(true);
        UUID supplierId = suppliers.saveAndFlush(supplier).getId();
        String body = mvc.perform(multipart(BASE + "/imports")
                        .file(new MockMultipartFile("file", "precio-invalido.xlsx", XLSX, workbook()))
                        .param("supplierId", supplierId.toString()).header("Idempotency-Key", "invalid-price")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.lines[0].matchStatus").value("INVALID"))
                .andExpect(jsonPath("$.lines[0].sourceUnitPrice").value("1e3"))
                .andExpect(jsonPath("$.lines[0].unitPrice").doesNotExist())
                .andExpect(jsonPath("$.lines[0].lineTotal").doesNotExist())
                .andExpect(jsonPath("$.lines[0].pricingUnit").doesNotExist())
                .andExpect(jsonPath("$.lines[0].currency").doesNotExist())
                .andExpect(jsonPath("$.lines[0].errors[0]").value(org.hamcrest.Matchers.containsString("precio unitario")))
                .andReturn().getResponse().getContentAsString();
        UUID draftId = UUID.fromString(mapper.readTree(body).get("id").asText());
        Integer populatedCosts = jdbc.queryForObject("select count(*) from purchase_draft_lines where draft_id=? and "
                + "(unit_price is not null or line_total is not null or pricing_unit is not null or currency is not null)", Integer.class, draftId);
        assertEquals(0, populatedCosts);
    }

    private byte[] workbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Compra"); var header = sheet.createRow(0);
            String[] headers = {"fecha", "producto", "cantidad", "unidad", "precio_unitario"};
            for (int index = 0; index < headers.length; index++) header.createCell(index).setCellValue(headers[index]);
            var row = sheet.createRow(1); row.createCell(0).setCellValue("2026-08-29"); row.createCell(1).setCellValue("Cafe invalido");
            row.createCell(2).setCellValue("1.250"); row.createCell(3).setCellValue("kg"); row.createCell(4).setCellValue("1e3");
            workbook.write(output); return output.toByteArray();
        }
    }
}
