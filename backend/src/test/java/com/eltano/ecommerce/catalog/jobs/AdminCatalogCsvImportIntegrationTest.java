package com.eltano.ecommerce.catalog.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.eltano.ecommerce.catalog.domain.Category;
import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductType;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJob;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobRow;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobRowOutcome;
import com.eltano.ecommerce.catalog.jobs.repository.AdminCatalogJobRepository;
import com.eltano.ecommerce.catalog.jobs.repository.AdminCatalogJobInputRepository;
import com.eltano.ecommerce.catalog.jobs.repository.AdminCatalogJobRowRepository;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobStatus;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobType;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogSourceFormat;
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
    private AdminCatalogJobInputRepository jobInputRepository;

    @Autowired
    private AdminCatalogJobRowRepository rowRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @BeforeEach
    void setup() {
        rowRepository.deleteAll();
        jobInputRepository.deleteAll();
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
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.summary").doesNotExist())
                .andReturn();

        JsonNode importJson = objectMapper.readTree(importResult.getResponse().getContentAsString());
        UUID jobId = UUID.fromString(importJson.get("id").asText());

        AdminCatalogJob job = jobRepository.findById(jobId).orElseThrow();
        assertEquals(AdminCatalogJobStatus.QUEUED, job.getStatus());
        assertEquals(csv, jobInputRepository.findById(jobId).orElseThrow().getPayloadText());
        assertEquals(0, rowRepository.count());
        assertTrue(productRepository.findAll().isEmpty());
    }

    @Test
    void excelImportReturnsBadRequestWithoutDataChanges() throws Exception {
        long productsBefore = productRepository.count();

        mockMvc.perform(post("/api/admin/catalog/jobs/import")
                .param("format", "excel")
                .with(httpBasic("admin-user", "admin-pass"))
                .with(csrf())
                .contentType(MediaType.TEXT_PLAIN)
                .content("anything"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("requires format=csv")))
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
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn();

        JsonNode exportJson = objectMapper.readTree(exportResult.getResponse().getContentAsString());
        UUID exportJobId = UUID.fromString(exportJson.get("id").asText());

        AdminCatalogJob queued = jobRepository.findById(exportJobId).orElseThrow();
        assertEquals(AdminCatalogJobStatus.QUEUED, queued.getStatus());
    }

    @Test
    void listJobsReturnsEmptyArrayWhenNoJobsExist() throws Exception {
        mockMvc.perform(get("/api/admin/catalog/jobs")
                .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[]"));
    }

    @Test
    void listJobsReturnsNewestFirstWithRequiredFields() throws Exception {
        AdminCatalogJob older = new AdminCatalogJob();
        older.setJobType(AdminCatalogJobType.IMPORT);
        older.setStatus(AdminCatalogJobStatus.QUEUED);
        older.setCreatedBy("admin-user");
        older.setSourceFormat(AdminCatalogSourceFormat.CSV);
        AdminCatalogJob olderSaved = jobRepository.save(older);

        AdminCatalogJob newer = new AdminCatalogJob();
        newer.setJobType(AdminCatalogJobType.EXPORT);
        newer.setStatus(AdminCatalogJobStatus.PROCESSING);
        newer.setCreatedBy("admin-user");
        newer.setSourceFormat(AdminCatalogSourceFormat.CSV);
        AdminCatalogJob newerSaved = jobRepository.save(newer);

        mockMvc.perform(get("/api/admin/catalog/jobs")
                .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(newerSaved.getId().toString()))
                .andExpect(jsonPath("$[0].type").value("EXPORT"))
                .andExpect(jsonPath("$[0].status").value("PROCESSING"))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[0].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$[1].id").value(olderSaved.getId().toString()))
                .andExpect(jsonPath("$[1].type").value("IMPORT"));
    }

    @Test
    void reportEndpointRemainsCsvAndDiagnosticsEndpointReturnsJsonRows() throws Exception {
        AdminCatalogJob job = new AdminCatalogJob();
        job.setJobType(AdminCatalogJobType.IMPORT);
        job.setStatus(AdminCatalogJobStatus.COMPLETED);
        job.setCreatedBy("admin-user");
        job.setSourceFormat(AdminCatalogSourceFormat.CSV);
        job.setSummary("processed=2,succeeded=1,failed=1");
        AdminCatalogJob savedJob = jobRepository.save(job);

        AdminCatalogJobRow successRow = new AdminCatalogJobRow();
        successRow.setJob(savedJob);
        successRow.setRowNumber(1);
        successRow.setOutcome(AdminCatalogJobRowOutcome.SUCCESS);
        successRow.setErrorCode(null);
        successRow.setErrorMessage(null);
        successRow.setPayloadJson("ok");
        rowRepository.save(successRow);

        AdminCatalogJobRow failedRow = new AdminCatalogJobRow();
        failedRow.setJob(savedJob);
        failedRow.setRowNumber(2);
        failedRow.setOutcome(AdminCatalogJobRowOutcome.FAILED);
        failedRow.setErrorCode("DUPLICATE_SLUG");
        failedRow.setErrorMessage("slug duplicado");
        failedRow.setPayloadJson("bad");
        rowRepository.save(failedRow);

        mockMvc.perform(get("/api/admin/catalog/jobs/{id}/report", savedJob.getId())
                .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("validation-report-" + savedJob.getId())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("rowNumber,errorCode,errorMessage,payload")));

        mockMvc.perform(get("/api/admin/catalog/jobs/{id}/report/diagnostics", savedJob.getId())
                .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.summary").value("processed=2,succeeded=1,failed=1"))
                .andExpect(jsonPath("$.failedRows").value(1))
                .andExpect(jsonPath("$.rows[0].outcome").value("SUCCESS"))
                .andExpect(jsonPath("$.rows[0].errorCode").isEmpty())
                .andExpect(jsonPath("$.rows[0].errorMessage").isEmpty())
                .andExpect(jsonPath("$.rows[0].payload").value("ok"))
                .andExpect(jsonPath("$.rows[1].outcome").value("FAILED"))
                .andExpect(jsonPath("$.rows[1].errorCode").value("DUPLICATE_SLUG"));
    }
}
