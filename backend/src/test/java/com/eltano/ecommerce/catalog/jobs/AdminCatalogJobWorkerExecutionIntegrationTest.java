package com.eltano.ecommerce.catalog.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.eltano.ecommerce.catalog.domain.Category;
import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductType;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJob;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobStatus;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobType;
import com.eltano.ecommerce.catalog.jobs.repository.AdminCatalogJobInputRepository;
import com.eltano.ecommerce.catalog.jobs.repository.AdminCatalogJobRepository;
import com.eltano.ecommerce.catalog.jobs.repository.AdminCatalogJobRowRepository;
import com.eltano.ecommerce.catalog.jobs.worker.AdminCatalogJobWorker;
import com.eltano.ecommerce.catalog.repository.CategoryRepository;
import com.eltano.ecommerce.catalog.repository.ProductRepository;

@SpringBootTest(properties = {
        "app.catalog.seed-on-empty=false",
        "app.catalog.jobs.worker.enabled=true",
        "app.catalog.jobs.worker.initial-delay=3600000"
})
@ActiveProfiles("test")
class AdminCatalogJobWorkerExecutionIntegrationTest {

    @Autowired
    private AdminCatalogJobWorker worker;

    @Autowired
    private AdminCatalogJobService jobService;

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
    void clean() {
        rowRepository.deleteAll();
        jobInputRepository.deleteAll();
        jobRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void claimedImportExecutesOnceAndCompletesWithSummaryMetadata() {
        Category category = new Category();
        category.setName("Frutos secos");
        category.setSlug("frutos-secos");
        category.setActive(true);
        categoryRepository.save(category);

        String csv = "categorySlug,name,slug,description\n"
                + "frutos-secos,Almendra,almendra-importada,Producto valido\n"
                + "categoria-inexistente,Nuez,nuez-importada,Producto invalido";

        AdminCatalogJob job = jobService.enqueueCsvImport("admin-user", csv);
        worker.runOnce();

        AdminCatalogJob updated = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(AdminCatalogJobStatus.COMPLETED, updated.getStatus());
        assertEquals(1, updated.getAttemptCount());
        assertNotNull(updated.getCompletedAt());
        assertEquals("processed=2,succeeded=1,failed=1", updated.getSummary());
        assertEquals(1, productRepository.count());
        assertEquals(2, rowRepository.findByJobIdOrderByRowNumberAsc(job.getId()).size());
    }

    @Test
    void claimedExportExecutesOnceAndStoresTerminalSummaryMetadata() {
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

        AdminCatalogJob job = jobService.enqueueCsvExport("admin-user");
        worker.runOnce();

        AdminCatalogJob updated = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(AdminCatalogJobType.EXPORT, updated.getJobType());
        assertEquals(AdminCatalogJobStatus.COMPLETED, updated.getStatus());
        assertEquals(1, updated.getAttemptCount());
        assertTrue(updated.getSummary().contains("processed=1"));
        assertTrue(updated.getSummary().contains("succeeded=1"));
        assertTrue(updated.getSummary().contains("failed=0"));
    }
}
