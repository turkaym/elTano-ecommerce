package com.eltano.ecommerce.catalog.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJob;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobStatus;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobType;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogSourceFormat;
import com.eltano.ecommerce.catalog.jobs.repository.AdminCatalogJobRepository;
import com.eltano.ecommerce.catalog.jobs.worker.AdminCatalogJobWorker;

@SpringBootTest(properties = {
        "app.catalog.seed-on-empty=false",
        "app.catalog.jobs.worker.enabled=false"
})
@ActiveProfiles("test")
class AdminCatalogJobWorkerDisabledIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private AdminCatalogJobRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void queuedJobsStayUnclaimedWhenWorkerIsDisabled() {
        AdminCatalogJob queued = new AdminCatalogJob();
        queued.setJobType(AdminCatalogJobType.IMPORT);
        queued.setStatus(AdminCatalogJobStatus.QUEUED);
        queued.setCreatedBy("admin-user");
        queued.setSourceFormat(AdminCatalogSourceFormat.CSV);
        repository.saveAndFlush(queued);

        assertEquals(0, context.getBeansOfType(AdminCatalogJobWorker.class).size());

        AdminCatalogJob unchanged = repository.findById(queued.getId()).orElseThrow();
        assertEquals(AdminCatalogJobStatus.QUEUED, unchanged.getStatus());
        assertEquals(0, unchanged.getAttemptCount());
    }
}
