package com.eltano.ecommerce.catalog.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eltano.ecommerce.catalog.domain.Category;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJob;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobRow;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobRowOutcome;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobStatus;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobType;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogSourceFormat;
import com.eltano.ecommerce.catalog.jobs.repository.AdminCatalogJobRepository;
import com.eltano.ecommerce.catalog.jobs.repository.AdminCatalogJobRowRepository;
import com.eltano.ecommerce.catalog.repository.CategoryRepository;
import com.eltano.ecommerce.catalog.repository.ProductRepository;
import com.eltano.ecommerce.common.api.UnprocessableEntityException;

@ExtendWith(MockitoExtension.class)
class AdminCatalogJobServiceTest {

    @Mock
    private AdminCatalogJobRepository jobRepository;

    @Mock
    private AdminCatalogJobRowRepository jobRowRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    private AdminCatalogJobService service;

    @BeforeEach
    void setUp() {
        service = new AdminCatalogJobService(jobRepository, jobRowRepository, categoryRepository, productRepository);
    }

    @Test
    void csvImportEnqueuesQueuedJobWithoutInlineProcessing() {
        List<String> savedStatuses = new ArrayList<>();
        when(jobRepository.save(any(AdminCatalogJob.class))).thenAnswer(invocation -> {
            AdminCatalogJob saved = invocation.getArgument(0);
            savedStatuses.add(saved.getStatus().name());
            return saved;
        });
        String csv = "categorySlug,name,slug,description\nfrutos-secos,Almendra,almendra-1,Desc";
        AdminCatalogJob job = service.startCsvImport("admin-user", csv);

        assertEquals(AdminCatalogJobType.IMPORT, job.getJobType());
        assertEquals(AdminCatalogSourceFormat.CSV, job.getSourceFormat());
        assertEquals(AdminCatalogJobStatus.QUEUED, job.getStatus());
        assertEquals("admin-user", job.getCreatedBy());
        assertEquals(null, job.getCompletedAt());
        assertEquals(null, job.getSummary());

        verify(jobRepository, atLeast(1)).save(any(AdminCatalogJob.class));
        verify(jobRowRepository, never()).save(any(AdminCatalogJobRow.class));
        verify(categoryRepository, never()).findBySlugIgnoreCase(any());
        verify(productRepository, never()).save(any());
        assertEquals(List.of("QUEUED"), savedStatuses);
    }

    @Test
    void csvExportEnqueuesQueuedJobWithoutInlineProcessing() {
        when(jobRepository.save(any(AdminCatalogJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AdminCatalogJob job = service.startCsvExport("admin-user");

        assertEquals(AdminCatalogJobType.EXPORT, job.getJobType());
        assertEquals(AdminCatalogSourceFormat.CSV, job.getSourceFormat());
        assertEquals(AdminCatalogJobStatus.QUEUED, job.getStatus());
        assertNotNull(job.getCreatedBy());
        assertEquals(null, job.getCompletedAt());

        verify(productRepository, never()).findAllWithRelations();
        verify(jobRowRepository, never()).save(any(AdminCatalogJobRow.class));
    }

    @Test
    void csvImportDefaultsActorToSystemWhenAuthenticationNameIsBlank() {
        when(jobRepository.save(any(AdminCatalogJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminCatalogJob job = service.startCsvImport(" ", "categorySlug,name,slug,description\na,b,c,d");

        assertEquals("system", job.getCreatedBy());
        assertEquals(AdminCatalogJobStatus.QUEUED, job.getStatus());
    }

    @Test
    void enqueueCsvImportRejectsBlankPayloadWithoutCreatingJob() {
        assertThrows(UnprocessableEntityException.class, () -> service.enqueueCsvImport("admin-user", "   "));

        verify(jobRepository, never()).save(any(AdminCatalogJob.class));
        verify(jobRowRepository, never()).save(any(AdminCatalogJobRow.class));
    }
}
