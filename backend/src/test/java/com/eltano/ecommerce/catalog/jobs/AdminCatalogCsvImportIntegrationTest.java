package com.eltano.ecommerce.catalog.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.eltano.ecommerce.catalog.domain.Category;
import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductType;
import com.eltano.ecommerce.catalog.jobs.repository.AdminCatalogJobRepository;
import com.eltano.ecommerce.catalog.jobs.repository.AdminCatalogJobRowRepository;
import com.eltano.ecommerce.catalog.repository.CategoryRepository;
import com.eltano.ecommerce.catalog.repository.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "app.catalog.seed-on-empty=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCatalogCsvImportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminCatalogJobRepository jobRepository;

    @Autowired
    private AdminCatalogJobRowRepository rowRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @BeforeEach
    void setup() {
        rowRepository.deleteAll();
        jobRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void mixedValidityCsvImportProcessesValidRowsAndProducesValidationReport() throws Exception {
        Category category = new Category();
        category.setName("Frutos secos");
        category.setSlug("frutos-secos");
        category.setActive(true);
        categoryRepository.save(category);

        String csv = "categorySlug,name,slug,description\n"
                + "frutos-secos,Almendra,almendra-importada,Producto valido\n"
                + "categoria-inexistente,Nuez,nuez-importada,Producto invalido";

        MvcResult importResult = mockMvc.perform(post("/api/admin/catalog/jobs/import")
                .param("format", "csv")
                .with(httpBasic("admin-user", "admin-pass"))
                .with(csrf())
                .contentType(MediaType.TEXT_PLAIN)
                .content(csv))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.summary").value("processed=2,succeeded=1,failed=1"))
                .andReturn();

        JsonNode importJson = objectMapper.readTree(importResult.getResponse().getContentAsString());
        UUID jobId = UUID.fromString(importJson.get("id").asText());

        mockMvc.perform(get("/api/admin/catalog/jobs/{id}/rows", jobId)
                .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rowNumber").value(2))
                .andExpect(jsonPath("$[0].outcome").value("SUCCESS"))
                .andExpect(jsonPath("$[1].rowNumber").value(3))
                .andExpect(jsonPath("$[1].outcome").value("FAILED"))
                .andExpect(jsonPath("$[1].errorCode").value("CATEGORY_NOT_FOUND"));

        mockMvc.perform(get("/api/admin/catalog/jobs/{id}/report", jobId)
                .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("rowNumber,errorCode,errorMessage,payload")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CATEGORY_NOT_FOUND")));

        assertTrue(productRepository.existsBySlugIgnoreCase("almendra-importada"));
    }

    @Test
    void excelImportReturnsNotSupportedWithoutDataChanges() throws Exception {
        long productsBefore = productRepository.count();

        mockMvc.perform(post("/api/admin/catalog/jobs/import")
                .param("format", "excel")
                .with(httpBasic("admin-user", "admin-pass"))
                .with(csrf())
                .contentType(MediaType.TEXT_PLAIN)
                .content("anything"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("NOT_SUPPORTED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("CSV only")))
                .andExpect(jsonPath("$.correlationId").isString())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        assertEquals(productsBefore, productRepository.count());
    }

    @Test
    void csvExportCreatesJobAndProvidesDownloadArtifact() throws Exception {
        Category category = new Category();
        category.setName("Frutos secos");
        category.setSlug("frutos-secos");
        category.setActive(true);
        Category savedCategory = categoryRepository.save(category);

        Product product = new Product();
        product.setCategory(savedCategory);
        product.setName("Avellana");
        product.setSlug("avellana-export");
        product.setDescription("Producto para export");
        product.setActive(true);
        product.setProductType(ProductType.ENVASADO);
        product.setInventoryPolicy(InventoryPolicy.PER_VARIANT);
        productRepository.save(product);

        MvcResult exportResult = mockMvc.perform(post("/api/admin/catalog/jobs/export")
                .param("format", "csv")
                .with(httpBasic("admin-user", "admin-pass"))
                .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobType").value("EXPORT"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn();

        JsonNode exportJson = objectMapper.readTree(exportResult.getResponse().getContentAsString());
        UUID exportJobId = UUID.fromString(exportJson.get("id").asText());

        mockMvc.perform(get("/api/admin/catalog/jobs/{id}/export-file", exportJobId)
                .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("categorySlug,name,slug,description")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("avellana-export")));
    }
}
