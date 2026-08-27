package com.eltano.ecommerce.catalog.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductImage;
import com.eltano.ecommerce.catalog.domain.ProductType;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.catalog.domain.UnitType;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJob;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobInput;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobRow;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobRowOutcome;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobStatus;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobType;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogSourceFormat;
import com.eltano.ecommerce.catalog.jobs.repository.AdminCatalogJobInputRepository;
import com.eltano.ecommerce.catalog.jobs.repository.AdminCatalogJobRepository;
import com.eltano.ecommerce.catalog.jobs.repository.AdminCatalogJobRowRepository;
import com.eltano.ecommerce.catalog.repository.CategoryRepository;
import com.eltano.ecommerce.catalog.repository.ProductRepository;
import com.eltano.ecommerce.catalog.repository.ProductVariantRepository;
import com.eltano.ecommerce.procurement.domain.InventoryTargetType;
import com.eltano.ecommerce.procurement.domain.Purchase;
import com.eltano.ecommerce.procurement.domain.PurchaseReceipt;
import com.eltano.ecommerce.procurement.domain.ReceiptKind;
import com.eltano.ecommerce.procurement.domain.StockMovement;
import com.eltano.ecommerce.procurement.domain.Supplier;
import com.eltano.ecommerce.procurement.repository.PurchaseReceiptRepository;
import com.eltano.ecommerce.procurement.repository.PurchaseRepository;
import com.eltano.ecommerce.procurement.repository.StockMovementRepository;
import com.eltano.ecommerce.procurement.repository.SupplierRepository;

@SpringBootTest(properties = "app.catalog.seed-on-empty=false")
@ActiveProfiles("test")
@Transactional
class AlegraProductImportProcessorIntegrationTest {

    @Autowired
    private AdminCatalogJobService jobService;

    @Autowired
    private AdminCatalogJobRepository jobRepository;

    @Autowired
    private AdminCatalogJobInputRepository jobInputRepository;

    @Autowired
    private AdminCatalogJobRowRepository rowRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private PurchaseReceiptRepository purchaseReceiptRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @BeforeEach
    void clean() {
        stockMovementRepository.deleteAll();
        purchaseReceiptRepository.deleteAll();
        purchaseRepository.deleteAll();
        supplierRepository.deleteAll();
        rowRepository.deleteAll();
        jobInputRepository.deleteAll();
        jobRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void excelJobImportsUnitAndKilogramProductsWithZeroStockAndProportionalPrices() {
        AdminCatalogJob job = excelJob(jsonRow(2, "Frutos Secos", "Almendra", "Almendra entera", "ALM-001", "Unidad", "123.45")
                + "\n"
                + jsonRow(3, "Quesos", "Queso Azul", "Hormita", "QAZ-1", "Kilogramo", "1000.00"));

        String summary = jobService.executeClaimedJob(job.getId());

        assertEquals("processed=2,created=2,updated=0,skipped=0,errors=0", summary);
        assertEquals(2, categoryRepository.count());
        assertEquals(2, productRepository.count());
        assertEquals(5, productVariantRepository.count());

        Product unitProduct = productBySlug("almendra-alm-001");
        assertEquals(ProductType.UNIDAD, unitProduct.getProductType());
        assertEquals(InventoryPolicy.PER_VARIANT, unitProduct.getInventoryPolicy());
        ProductVariant unitVariant = unitProduct.getVariants().getFirst();
        assertEquals("ALM-001", unitVariant.getSku());
        assertEquals(UnitType.UNIT, unitVariant.getUnitType());
        assertEquals("unidad", unitVariant.getUnitLabel());
        assertEquals(new BigDecimal("123.45"), unitVariant.getPrice());
        assertEquals(0, unitVariant.getStockAvailable());
        assertEquals(0, unitVariant.getStockReserved());
        assertEquals("/placeholder-product.svg", unitProduct.getImages().getFirst().getUrl());

        Product kilogramProduct = productBySlug("queso-azul-qaz-1");
        assertEquals(ProductType.GRANEL, kilogramProduct.getProductType());
        assertEquals(InventoryPolicy.BULK_WEIGHT, kilogramProduct.getInventoryPolicy());
        List<ProductVariant> variants = kilogramProduct.getVariants().stream()
                .sorted(Comparator.comparing(ProductVariant::getSku))
                .toList();
        assertEquals(List.of("QAZ-1-100G", "QAZ-1-1KG", "QAZ-1-250G", "QAZ-1-500G"), variants.stream().map(ProductVariant::getSku).toList());
        assertEquals(new BigDecimal("100.00"), variant(kilogramProduct, "QAZ-1-100G").getPrice());
        assertEquals(new BigDecimal("250.00"), variant(kilogramProduct, "QAZ-1-250G").getPrice());
        assertEquals(new BigDecimal("500.00"), variant(kilogramProduct, "QAZ-1-500G").getPrice());
        assertEquals(new BigDecimal("1000.00"), variant(kilogramProduct, "QAZ-1-1KG").getPrice());
        assertTrue(kilogramProduct.getVariants().stream().allMatch(variant -> variant.getStockAvailable() == 0));
    }

    @Test
    void reimportUpdatesCatalogFieldsAndPricesWithoutOverwritingLedgerBackedStockOrImages() {
        AdminCatalogJob firstJob = excelJob(jsonRow(2, "Frutos Secos", "Almendra", "Original", "ALM-001", "Unidad", "100.00"));
        assertEquals("processed=1,created=1,updated=0,skipped=0,errors=0", jobService.executeClaimedJob(firstJob.getId()));

        Product existingProduct = productBySlug("almendra-alm-001");
        ProductVariant existingVariant = existingProduct.getVariants().getFirst();
        existingVariant.setStockAvailable(7);
        existingVariant.setStockReserved(2);
        existingProduct.getImages().getFirst().setUrl("/manual/almendra.jpg");
        existingProduct.getImages().getFirst().setAltText("Manual image");
        productRepository.saveAndFlush(existingProduct);
        recordLedgerMovement(existingVariant, 7);

        AdminCatalogJob secondJob = excelJob(jsonRow(2, "Snacks", "Almendra Premium", "Updated", "ALM-001", "Unidad", "150.00"));
        String summary = jobService.executeClaimedJob(secondJob.getId());

        assertEquals("processed=1,created=0,updated=1,skipped=0,errors=0", summary);
        assertEquals(1, productRepository.count());
        assertEquals(1, productVariantRepository.count());

        Product updatedProduct = productBySlug("almendra-alm-001");
        ProductVariant updatedVariant = updatedProduct.getVariants().getFirst();
        assertEquals("Almendra Premium", updatedProduct.getName());
        assertEquals("Updated", updatedProduct.getDescription());
        assertEquals("snacks", updatedProduct.getCategory().getSlug());
        assertEquals(new BigDecimal("150.00"), updatedVariant.getPrice());
        assertEquals(7, updatedVariant.getStockAvailable());
        assertEquals(2, updatedVariant.getStockReserved());
        assertEquals("/manual/almendra.jpg", updatedProduct.getImages().getFirst().getUrl());
        assertEquals("Manual image", updatedProduct.getImages().getFirst().getAltText());
        assertTrue(stockMovementRepository.existsByTargetTypeAndTargetId(
                InventoryTargetType.VARIANT_UNIT,
                updatedVariant.getId()));
    }

    @Test
    void invalidRowsAreSkippedWithDiagnosticsWithoutBlockingValidRows() {
        AdminCatalogJob job = excelJob(jsonRow(2, "Frutos Secos", "Sin Codigo", "Sin Codigo", "", "Unidad", "10.00")
                + "\n"
                + jsonRow(3, "Frutos Secos", "Caja", "Caja", "CAJA-1", "Caja", "10.00")
                + "\n"
                + jsonRow(4, "Frutos Secos", "Precio Malo", "Precio Malo", "BAD-1", "Unidad", "precio")
                + "\n"
                + jsonRow(5, "Frutos Secos", "Nuez", "Nuez", "NUEZ-1", "Unidad", "20.00"));

        String summary = jobService.executeClaimedJob(job.getId());

        assertEquals("processed=4,created=1,updated=0,skipped=3,errors=3", summary);
        assertEquals(1, productRepository.count());
        List<AdminCatalogJobRow> rows = rowRepository.findByJobIdOrderByRowNumberAsc(job.getId());
        assertEquals(4, rows.size());
        assertEquals(List.of("MISSING_KEY", "UNSUPPORTED_UNIT", "INVALID_PRICE"), rows.stream()
                .filter(row -> row.getOutcome() == AdminCatalogJobRowOutcome.FAILED)
                .map(AdminCatalogJobRow::getErrorCode)
                .toList());
        assertEquals(1, rows.stream().filter(row -> row.getOutcome() == AdminCatalogJobRowOutcome.SUCCESS).count());
    }

    private AdminCatalogJob excelJob(String payload) {
        AdminCatalogJob job = new AdminCatalogJob();
        job.setJobType(AdminCatalogJobType.IMPORT);
        job.setStatus(AdminCatalogJobStatus.QUEUED);
        job.setCreatedBy("admin-user");
        job.setSourceFormat(AdminCatalogSourceFormat.EXCEL);
        AdminCatalogJob savedJob = jobRepository.save(job);

        AdminCatalogJobInput input = new AdminCatalogJobInput();
        input.setJobId(savedJob.getId());
        input.setPayloadText(payload);
        jobInputRepository.save(input);
        return savedJob;
    }

    private Product productBySlug(String slug) {
        return productRepository.findAllWithRelations().stream()
                .filter(product -> product.getSlug().equals(slug))
                .findFirst()
                .orElseThrow();
    }

    private ProductVariant variant(Product product, String sku) {
        return product.getVariants().stream()
                .filter(variant -> variant.getSku().equals(sku))
                .findFirst()
                .orElseThrow();
    }

    private void recordLedgerMovement(ProductVariant variant, int balance) {
        Supplier supplier = new Supplier();
        supplier.setName("Import protection supplier");
        supplier = supplierRepository.save(supplier);

        Purchase purchase = new Purchase();
        purchase.setSupplier(supplier);
        purchase.setDocumentType("TEST");
        purchase.setDocumentNumber(UUID.randomUUID().toString());
        purchase.setNormalizedDocumentType("test");
        purchase.setNormalizedDocumentNumber(purchase.getDocumentNumber().toLowerCase());
        purchase.setPurchasedAt(LocalDate.now());
        purchase.setCreatedBy("test");
        purchase = purchaseRepository.save(purchase);

        PurchaseReceipt receipt = new PurchaseReceipt();
        receipt.setPurchase(purchase);
        receipt.setKind(ReceiptKind.RECEIPT);
        receipt.setIdempotencyKey(UUID.randomUUID().toString());
        receipt.setRequestHash("0".repeat(64));
        receipt.setActor("test");
        receipt.setCorrelationId(UUID.randomUUID().toString());
        receipt = purchaseReceiptRepository.save(receipt);

        StockMovement movement = new StockMovement();
        movement.setSourceType("TEST_IMPORT_PROTECTION");
        movement.setSourceId(UUID.randomUUID());
        movement.setPurchase(purchase);
        movement.setReceipt(receipt);
        movement.setTargetType(InventoryTargetType.VARIANT_UNIT);
        movement.setTargetId(variant.getId());
        movement.setQuantity(BigDecimal.valueOf(balance));
        movement.setConversion(BigDecimal.ONE);
        movement.setCanonicalDelta(balance);
        movement.setBeforeBalance(0);
        movement.setAfterBalance(balance);
        movement.setActor("test");
        movement.setCorrelationId(UUID.randomUUID().toString());
        stockMovementRepository.save(movement);
    }

    private String jsonRow(int rowNumber, String category, String name, String description, String code, String unit, String price) {
        return "{"
                + "\"rowNumber\":" + rowNumber
                + ",\"category\":\"" + category + "\""
                + ",\"name\":\"" + name + "\""
                + ",\"description\":\"" + description + "\""
                + ",\"code\":\"" + code + "\""
                + ",\"unitOfMeasure\":\"" + unit + "\""
                + ",\"generalPrice\":\"" + price + "\""
                + "}";
    }
}
