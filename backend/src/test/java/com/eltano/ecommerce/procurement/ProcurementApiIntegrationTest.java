package com.eltano.ecommerce.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.eltano.ecommerce.catalog.domain.Category;
import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductType;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.catalog.domain.UnitType;
import com.eltano.ecommerce.catalog.repository.CategoryRepository;
import com.eltano.ecommerce.catalog.repository.ProductRepository;
import com.eltano.ecommerce.catalog.repository.ProductVariantRepository;
import com.eltano.ecommerce.audit.repository.AdminAuditEventRepository;
import com.eltano.ecommerce.procurement.repository.SupplierRepository;
import com.eltano.ecommerce.procurement.repository.PurchaseReceiptRepository;
import com.eltano.ecommerce.procurement.repository.PurchaseRepository;
import com.eltano.ecommerce.procurement.repository.StockMovementRepository;
import com.eltano.ecommerce.procurement.domain.ReceiptKind;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.ServletException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProcurementApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired AdminAuditEventRepository auditEvents;
    @Autowired SupplierRepository supplierRepository;
    @Autowired PurchaseRepository purchaseRepository;
    @Autowired PurchaseReceiptRepository receiptRepository;
    @Autowired StockMovementRepository movementRepository;
    @Autowired MeterRegistry meterRegistry;
    @Autowired JdbcTemplate jdbc;
    UUID variantId;
    UUID otherVariantId;
    UUID productId;

    @BeforeEach
    void setUpTarget() {
        Category category = new Category();
        category.setName("Procurement " + UUID.randomUUID());
        category.setSlug("procurement-" + UUID.randomUUID());
        category.setActive(true);
        categories.save(category);
        Product product = new Product();
        product.setName("Mapped product");
        product.setSlug("mapped-" + UUID.randomUUID());
        product.setDescription("Mapped product");
        product.setActive(true);
        product.setCategory(category);
        product.setProductType(ProductType.ENVASADO);
        product.setInventoryPolicy(InventoryPolicy.PER_VARIANT);
        ProductVariant variant = new ProductVariant();
        variant.setSku("PROC-" + UUID.randomUUID());
        variant.setUnitType(UnitType.UNIT);
        variant.setUnitLabel("unit");
        variant.setPrice(BigDecimal.ONE);
        variant.setStockAvailable(5);
        variant.setStockReserved(2);
        variant.setActive(true);
        product.addVariant(variant);
        ProductVariant other = new ProductVariant();
        other.setSku("OTHER-" + UUID.randomUUID()); other.setUnitType(UnitType.UNIT);
        other.setUnitLabel("unit"); other.setPrice(BigDecimal.ONE);
        other.setStockAvailable(11); other.setActive(true);
        product.addVariant(other);
        products.saveAndFlush(product);
        productId = product.getId();
        variantId = variant.getId();
        otherVariantId = other.getId();
    }

    @Test
    void receiptConfirmationPreflightAllowsIdempotencyKeyHeader() throws Exception {
        mvc.perform(options("/api/admin/procurement/purchases/{id}/receipts/confirm", UUID.randomUUID())
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", "Idempotency-Key"));
    }

    @Test
    void supplierMappingPurchaseReceiptReplayAndSafeCancellationFlow() throws Exception {
        double confirmationsBefore = counter("procurement.receipt.confirmations");
        double replaysBefore = counter("procurement.receipt.replays");
        String supplierId = createSupplier("Supplier One");
        String mappingId = createVariantMapping(supplierId, variantId, "2.000000");
        String purchaseId = createPurchase(supplierId, mappingId, " Invoice ", " A-10 ", "5.000000");

        mvc.perform(post("/api/admin/procurement/purchases")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseBody(supplierId, mappingId, "invoice", "a-10", "1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_DOCUMENT"));
        mvc.perform(get("/api/admin/procurement/purchases/{id}", purchaseId)
                        .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.documentType").value(" Invoice "))
                .andExpect(jsonPath("$.documentNumber").value(" A-10 "));

        String receipt = """
                {"lines":[{"purchaseLineId":"%s","dispositions":[
                  {"type":"ACCEPTED_ORDERED","quantity":"2.000000"},
                  {"type":"TEMP_MISSING","quantity":"3.000000"}]}]}
                """.formatted(purchaseLineId(purchaseId));
        mvc.perform(post("/api/admin/procurement/purchases/{id}/receipts/preview", purchaseId)
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(receipt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canonicalDeltas[0].delta").value(4));
        mvc.perform(post("/api/admin/procurement/purchases/{id}/receipts/confirm", purchaseId)
                        .header("Idempotency-Key", "receipt-1").header("X-Correlation-Id", "corr-1")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(receipt))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(post("/api/admin/procurement/purchases/{id}/receipts/confirm", purchaseId)
                        .header("Idempotency-Key", "receipt-1")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(receipt))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true));
        assertEquals(confirmationsBefore + 1, counter("procurement.receipt.confirmations"));
        assertEquals(replaysBefore + 1, counter("procurement.receipt.replays"));
        mvc.perform(get("/api/admin/procurement/purchases/{id}", purchaseId)
                        .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].outstandingQuantity").value(3.0))
                .andExpect(jsonPath("$.progress").value("2 / 5"));
        mvc.perform(post("/api/admin/procurement/purchases/{id}/receipts/confirm", purchaseId)
                        .header("Idempotency-Key", "receipt-1")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(closeCommand(purchaseLineId(purchaseId), "1.000000")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        String close = closeCommand(purchaseLineId(purchaseId), "3.000000");
        mvc.perform(post("/api/admin/procurement/purchases/{id}/receipts/confirm", purchaseId)
                        .header("Idempotency-Key", "receipt-2")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(close))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RECEIVED"));
        mvc.perform(get("/api/admin/procurement/purchases/{id}", purchaseId)
                        .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].outstandingQuantity").value(0.0))
                .andExpect(jsonPath("$.progress").value("5 / 5"));

        mvc.perform(post("/api/admin/procurement/purchases/{id}/corrections", purchaseId)
                        .header("Idempotency-Key", "correction-zero")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"reason":"Count check","deltas":[{"targetType":"VARIANT_UNIT","targetId":"%s","delta":0}]}
                                """.formatted(variantId)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/admin/procurement/purchases/{id}/corrections", purchaseId)
                        .header("Idempotency-Key", "correction-1")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"reason":"One accepted unit was damaged","deltas":[{"targetType":"VARIANT_UNIT","targetId":"%s","delta":-1}]}
                                """.formatted(variantId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canonicalDeltas[0].delta").value(-1));

        mvc.perform(post("/api/admin/procurement/purchases/{id}/cancel", purchaseId)
                        .header("Idempotency-Key", "cancel-1")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Duplicated supplier invoice\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertEquals(5, variants.findById(variantId).orElseThrow().getStockAvailable());
    }

    @Test
    void inactiveSupplierBlocksNewMappingsButHistoryEndpointsRemainAuthorized() throws Exception {
        String supplierId = createSupplier("Supplier Two");
        String mappingId = createVariantMapping(supplierId, variantId, "1");
        String purchaseId = createPurchase(supplierId, mappingId, "Invoice", "DEACT-1", "1");
        mvc.perform(patch("/api/admin/procurement/suppliers/{id}", supplierId)
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(post("/api/admin/procurement/mappings")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(mappingBody(supplierId, variantId, "1")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
        mvc.perform(post("/api/admin/procurement/purchases")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseBody(supplierId, mappingId, "Invoice", "DEACT-2", "1")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
        mvc.perform(get("/api/admin/procurement/purchases/{id}", purchaseId)
                        .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.documentNumber").value("DEACT-1"));
        String receipt = """
                {"lines":[{"purchaseLineId":"%s","dispositions":[{"type":"ACCEPTED_ORDERED","quantity":"1"}]}]}
                """.formatted(purchaseLineId(purchaseId));
        mvc.perform(post("/api/admin/procurement/purchases/{id}/receipts/confirm", purchaseId)
                        .header("Idempotency-Key", "deactivated-receipt")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(receipt))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RECEIVED"));
        mvc.perform(post("/api/admin/procurement/purchases/{id}/cancel", purchaseId)
                        .header("Idempotency-Key", "deactivated-cancel")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Safe reversal\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/admin/procurement/suppliers")).andExpect(status().isUnauthorized());
    }

    @Test
    void mappingCreationRejectsTargetsIncompatibleWithProductInventoryPolicyBeforePersistence() throws Exception {
        String supplierId = createSupplier("Policy Supplier");
        long mappingsBefore = jdbc.queryForObject("select count(*) from supplier_item_mappings", Long.class);
        Product product = products.findById(productId).orElseThrow();
        product.setInventoryPolicy(InventoryPolicy.BULK_WEIGHT);
        product.setStockBaseGrams(1000);
        products.saveAndFlush(product);

        mvc.perform(post("/api/admin/procurement/mappings")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(mappingBody(supplierId, variantId, "1")))
                .andExpect(status().isBadRequest());

        product.setInventoryPolicy(InventoryPolicy.PER_VARIANT);
        products.saveAndFlush(product);
        mvc.perform(post("/api/admin/procurement/mappings")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"supplierId":"%s","supplierItemCode":"BULK-1","description":"Bulk item","targetType":"BULK_GRAM","productId":"%s","defaultConversion":"1"}
                                """.formatted(supplierId, productId)))
                .andExpect(status().isBadRequest());

        assertEquals(mappingsBefore, jdbc.queryForObject("select count(*) from supplier_item_mappings", Long.class));
    }

    @Test
    void explicitMappingRepairRetargetsAndReactivatesWithoutChangingSourceIdentity() throws Exception {
        String supplierId = createSupplier("Repair Supplier");
        String mappingId = createVariantMapping(supplierId, variantId, "1");
        mvc.perform(patch("/api/admin/procurement/mappings/{id}", mappingId)
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

        mvc.perform(put("/api/admin/procurement/mappings/{id}/repair", mappingId)
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"VARIANT_UNIT\",\"variantId\":\"" + otherVariantId + "\",\"defaultConversion\":\"1\",\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.supplierItemCode").value(org.hamcrest.Matchers.startsWith("SKU-")))
                .andExpect(jsonPath("$.variantId").value(otherVariantId.toString())).andExpect(jsonPath("$.targetLabel").isNotEmpty())
                .andExpect(jsonPath("$.active").value(true));

        mvc.perform(put("/api/admin/procurement/mappings/{id}/repair", mappingId)
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"BULK_GRAM\",\"productId\":\"" + productId + "\",\"defaultConversion\":\"1000\",\"active\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/procurement/mappings").param("supplierId", supplierId).with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].variantId").value(otherVariantId.toString()));
    }

    @Test
    void mappingRepairRejectsCatalogTargetsExcludedByMatcherEligibility() throws Exception {
        String mappingId = createVariantMapping(createSupplier("Eligibility Supplier"), variantId, "1");
        Product product = products.findById(productId).orElseThrow();
        ProductVariant variant = variants.findById(variantId).orElseThrow();

        variant.setActive(false); variants.saveAndFlush(variant);
        repairMapping(mappingId, "VARIANT_UNIT", null, variantId).andExpect(status().isBadRequest());
        variant.setActive(true); variants.saveAndFlush(variant);
        product.setActive(false); products.saveAndFlush(product);
        repairMapping(mappingId, "VARIANT_UNIT", null, variantId).andExpect(status().isBadRequest());
        product.setActive(true); product.setDeletedAt(Instant.now()); products.saveAndFlush(product);
        repairMapping(mappingId, "VARIANT_UNIT", null, variantId).andExpect(status().isBadRequest());

        product.setInventoryPolicy(InventoryPolicy.BULK_WEIGHT); product.setDeletedAt(null); product.setActive(false); products.saveAndFlush(product);
        repairMapping(mappingId, "BULK_GRAM", productId, null).andExpect(status().isBadRequest());
        product.setActive(true); product.setDeletedAt(Instant.now()); products.saveAndFlush(product);
        repairMapping(mappingId, "BULK_GRAM", productId, null).andExpect(status().isBadRequest());
    }

    @Test
    void unauthorizedProcurementMutationIsDeniedAndAuditedWithoutMutation() throws Exception {
        long suppliersBefore = suppliersCount();
        mvc.perform(post("/api/admin/procurement/suppliers")
                        .with(csrf()).header("X-Correlation-Id", "unauthorized-correlation")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Denied supplier\"}"))
                .andExpect(status().isUnauthorized());

        assertEquals(suppliersBefore, suppliersCount());
        var event = auditEvents.findAll().stream()
                .filter(item -> "unauthorized-correlation".equals(item.getCorrelationId()))
                .findFirst().orElseThrow();
        assertEquals("anonymous", event.getActor());
        assertEquals("FAILURE", event.getOutcome());
        assertEquals(401, event.getStatusCode());
    }

    @Test
    void purchaseKeepsMappingSnapshotAndPendingEditDoesNotMutateStock() throws Exception {
        String supplierId = createSupplier("Snapshot Supplier");
        String mappingId = createVariantMapping(supplierId, variantId, "2.000000");
        String purchaseId = createPurchase(supplierId, mappingId, "Invoice", "SNAP-1", "5.000000");

        int stockBefore = variants.findById(variantId).orElseThrow().getStockAvailable();
        mvc.perform(put("/api/admin/procurement/purchases/{id}", purchaseId)
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseBody(supplierId, mappingId, "Invoice", "SNAP-1", "3.000000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].orderedQuantity").value(3.0));
        assertEquals(stockBefore, variants.findById(variantId).orElseThrow().getStockAvailable());

        mvc.perform(patch("/api/admin/procurement/mappings/{id}", mappingId)
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Changed mapping\",\"defaultConversion\":\"9.000000\",\"active\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/procurement/purchases/{id}", purchaseId)
                        .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].supplierDescription").value("Supplier item"))
                .andExpect(jsonPath("$.lines[0].conversion").value(2.0))
                .andExpect(jsonPath("$.lines[0].targetType").value("VARIANT_UNIT"))
                .andExpect(jsonPath("$.lines[0].variantId").value(variantId.toString()));
    }

    @Test
    void confirmedReceiptAttributesActorAndCorrelationAndUnsafeCancellationKeepsOriginalEvidence() throws Exception {
        double blockedBefore = counter("procurement.reversal.blocked");
        String supplierId = createSupplier("Evidence Supplier");
        String mappingId = createVariantMapping(supplierId, variantId, "2.000000");
        String purchaseId = createPurchase(supplierId, mappingId, "Invoice", "EVID-1", "2.000000");
        String receipt = """
                {"lines":[{"purchaseLineId":"%s","dispositions":[
                  {"type":"ACCEPTED_ORDERED","quantity":"2.000000"}]}]}
                """.formatted(purchaseLineId(purchaseId));

        mvc.perform(post("/api/admin/procurement/purchases/{id}/receipts/confirm", purchaseId)
                        .header("Idempotency-Key", "evidence-receipt").header("X-Correlation-Id", "evidence-correlation")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(receipt))
                .andExpect(status().isOk());
        UUID purchaseUuid = UUID.fromString(purchaseId);
        var originalReceipt = receiptRepository.findAllByPurchaseIdOrderByConfirmedAt(purchaseUuid).getFirst();
        var originalMovement = movementRepository.findAllByPurchaseIdOrderByCreatedAt(purchaseUuid).getFirst();
        assertEquals("admin-user", originalReceipt.getActor());
        assertEquals("evidence-correlation", originalReceipt.getCorrelationId());
        assertEquals("admin-user", originalMovement.getActor());
        assertEquals("evidence-correlation", originalMovement.getCorrelationId());

        ProductVariant locked = variants.findById(variantId).orElseThrow();
        locked.setStockAvailable(3);
        variants.saveAndFlush(locked);
        mvc.perform(post("/api/admin/procurement/purchases/{id}/cancel", purchaseId)
                        .header("Idempotency-Key", "unsafe-cancel")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Unsafe reversal probe\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVERSAL_BLOCKED"));

        assertEquals(ReceiptKind.RECEIPT, receiptRepository.findAllByPurchaseIdOrderByConfirmedAt(purchaseUuid).getFirst().getKind());
        assertEquals(1, receiptRepository.findAllByPurchaseIdOrderByConfirmedAt(purchaseUuid).size());
        assertEquals(originalMovement.getId(), movementRepository.findAllByPurchaseIdOrderByCreatedAt(purchaseUuid).getFirst().getId());
        assertEquals(1, movementRepository.findAllByPurchaseIdOrderByCreatedAt(purchaseUuid).size());
        assertEquals("RECEIVED", purchaseRepository.findById(purchaseUuid).orElseThrow().getStatus().name());
        assertEquals(blockedBefore + 1, counter("procurement.reversal.blocked"));
    }

    @Test
    void correctionRejectsTargetOutsidePurchaseWithoutMutation() throws Exception {
        String supplierId = createSupplier("Correction Scope Supplier");
        String mappingId = createVariantMapping(supplierId, variantId, "1");
        String purchaseId = createPurchase(supplierId, mappingId, "Invoice", "SCOPE-1", "1");

        mvc.perform(post("/api/admin/procurement/purchases/{id}/corrections", purchaseId)
                        .header("Idempotency-Key", "wrong-target")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"reason":"Wrong target","deltas":[{"targetType":"VARIANT_UNIT","targetId":"%s","delta":4}]}
                                """.formatted(otherVariantId)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));

        assertEquals(11, variants.findById(otherVariantId).orElseThrow().getStockAvailable());
        assertEquals(0, receiptRepository.findAllByPurchaseIdOrderByConfirmedAt(UUID.fromString(purchaseId)).size());
    }

    @Test
    void receiptKeepsSourceConversionEvidenceWhenLinesShareTarget() throws Exception {
        String supplierId = createSupplier("Conversion Supplier");
        String firstMapping = createVariantMapping(supplierId, variantId, "2");
        String secondMapping = createVariantMapping(supplierId, variantId, "3");
        String json = mvc.perform(post("/api/admin/procurement/purchases")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"supplierId":"%s","documentType":"Invoice","documentNumber":"CONV-1","purchasedAt":"2026-08-21","lines":[
                                {"mappingId":"%s","orderedQuantity":"1"},{"mappingId":"%s","orderedQuantity":"1"}]}
                                """.formatted(supplierId, firstMapping, secondMapping)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode purchase = mapper.readTree(json);
        String purchaseId = purchase.get("id").asText();
        JsonNode lines = purchase.get("lines");

        mvc.perform(post("/api/admin/procurement/purchases/{id}/receipts/confirm", purchaseId)
                        .header("Idempotency-Key", "conversion-evidence")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"lines":[{"purchaseLineId":"%s","dispositions":[{"type":"ACCEPTED_ORDERED","quantity":"1"}]},
                                {"purchaseLineId":"%s","dispositions":[{"type":"ACCEPTED_ORDERED","quantity":"1"}]}]}
                                """.formatted(lines.get(0).get("id").asText(), lines.get(1).get("id").asText())))
                .andExpect(status().isOk());

        var evidence = movementRepository.findAllByPurchaseIdOrderByCreatedAt(UUID.fromString(purchaseId));
        assertEquals(2, evidence.size());
        evidence.forEach(movement -> assertEquals(
                movement.getCanonicalDelta(),
                movement.getQuantity().multiply(movement.getConversion()).intValueExact()));
    }

    @Test
    void previewIncludesOnlyAcceptedOrderedAndExcessAndRequiresExcessNote() throws Exception {
        String supplierId = createSupplier("Discrepancy Supplier");
        String mappingId = createVariantMapping(supplierId, variantId, "2");
        String purchaseId = createPurchase(supplierId, mappingId, "Invoice", "DISC-1", "5");
        String lineId = purchaseLineId(purchaseId);
        String reviewed = """
                {"lines":[{"purchaseLineId":"%s","dispositions":[
                {"type":"ACCEPTED_ORDERED","quantity":"1"},{"type":"TEMP_MISSING","quantity":"2"},
                {"type":"REJECTED_FINAL","quantity":"2","note":"Damaged"},
                {"type":"ACCEPTED_EXCESS","quantity":"1","note":"Approved excess"}]}]}
                """.formatted(lineId);
        mvc.perform(post("/api/admin/procurement/purchases/{id}/receipts/preview", purchaseId)
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(reviewed))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canonicalDeltas.length()").value(2))
                .andExpect(jsonPath("$.canonicalDeltas[0].delta").value(2))
                .andExpect(jsonPath("$.canonicalDeltas[1].delta").value(2));
        mvc.perform(post("/api/admin/procurement/purchases/{id}/receipts/preview", purchaseId)
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(reviewed.replace("Approved excess", "")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertEquals(5, variants.findById(variantId).orElseThrow().getStockAvailable());
    }

    @Test
    void laterReceiptAcceptsTemporaryMissingWithoutDuplicatingEvidence() throws Exception {
        String supplierId = createSupplier("Later Delivery Supplier");
        String mappingId = createVariantMapping(supplierId, variantId, "2");
        String purchaseId = createPurchase(supplierId, mappingId, "Invoice", "LATER-1", "5");
        String lineId = purchaseLineId(purchaseId);
        String first = """
                {"lines":[{"purchaseLineId":"%s","dispositions":[{"type":"ACCEPTED_ORDERED","quantity":"2"},{"type":"TEMP_MISSING","quantity":"3"}]}]}
                """.formatted(lineId);
        String later = """
                {"lines":[{"purchaseLineId":"%s","dispositions":[{"type":"ACCEPTED_ORDERED","quantity":"3"}]}]}
                """.formatted(lineId);
        confirmReceipt(purchaseId, "later-1", first).andExpect(jsonPath("$.status").value("PENDING"));
        confirmReceipt(purchaseId, "later-2", later).andExpect(jsonPath("$.status").value("RECEIVED"));
        mvc.perform(get("/api/admin/procurement/purchases/{id}", purchaseId).with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.progress").value("5 / 5"));
        UUID id = UUID.fromString(purchaseId);
        assertEquals(2, receiptRepository.findAllByPurchaseIdOrderByConfirmedAt(id).size());
        assertEquals(2, movementRepository.findAllByPurchaseIdOrderByCreatedAt(id).size());
        assertEquals(15, variants.findById(variantId).orElseThrow().getStockAvailable());
    }

    @Test
    void receiptConfirmationFailureRollsBackAllEvidenceProgressAndStock() throws Exception {
        String supplierId = createSupplier("Rollback Supplier");
        String mappingId = createVariantMapping(supplierId, variantId, "2");
        String purchaseId = createPurchase(supplierId, mappingId, "Invoice", "ROLLBACK-1", "1");
        ProductVariant target = variants.findById(variantId).orElseThrow();
        target.setStockAvailable(Integer.MAX_VALUE); variants.saveAndFlush(target);
        long dispositionsBefore = jdbc.queryForObject("select count(*) from purchase_receipt_dispositions", Long.class);
        String receipt = """
                {"lines":[{"purchaseLineId":"%s","dispositions":[{"type":"ACCEPTED_ORDERED","quantity":"1"}]}]}
                """.formatted(purchaseLineId(purchaseId));
        assertThrows(ServletException.class, () -> mvc.perform(post("/api/admin/procurement/purchases/{id}/receipts/confirm", purchaseId)
                .header("Idempotency-Key", "rollback-receipt")
                .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(receipt)));
        UUID id = UUID.fromString(purchaseId);
        assertEquals(0, receiptRepository.findAllByPurchaseIdOrderByConfirmedAt(id).size());
        assertEquals(0, movementRepository.findAllByPurchaseIdOrderByCreatedAt(id).size());
        assertEquals(dispositionsBefore, jdbc.queryForObject("select count(*) from purchase_receipt_dispositions", Long.class));
        assertEquals(Integer.MAX_VALUE, variants.findById(variantId).orElseThrow().getStockAvailable());
        mvc.perform(get("/api/admin/procurement/purchases/{id}", purchaseId).with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.progress").value("0 / 1"));
    }

    @Test
    void correctionAppendsCompensationAndPreservesOriginalEvidence() throws Exception {
        String supplierId = createSupplier("Correction Evidence Supplier");
        String mappingId = createVariantMapping(supplierId, variantId, "2");
        String purchaseId = createPurchase(supplierId, mappingId, "Invoice", "CORR-1", "2");
        String receipt = """
                {"lines":[{"purchaseLineId":"%s","dispositions":[{"type":"ACCEPTED_ORDERED","quantity":"2"}]}]}
                """.formatted(purchaseLineId(purchaseId));
        confirmReceipt(purchaseId, "correction-source", receipt).andExpect(status().isOk());
        UUID id = UUID.fromString(purchaseId);
        var originalReceipt = receiptRepository.findAllByPurchaseIdOrderByConfirmedAt(id).getFirst();
        var originalMovement = movementRepository.findAllByPurchaseIdOrderByCreatedAt(id).getFirst();
        mvc.perform(post("/api/admin/procurement/purchases/{id}/corrections", purchaseId)
                        .header("Idempotency-Key", "correction-evidence")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"reason":"One unit damaged","deltas":[{"targetType":"VARIANT_UNIT","targetId":"%s","delta":-1}]}
                                """.formatted(variantId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canonicalDeltas[0].delta").value(-1));
        var receipts = receiptRepository.findAllByPurchaseIdOrderByConfirmedAt(id);
        var movements = movementRepository.findAllByPurchaseIdOrderByCreatedAt(id);
        assertEquals(2, receipts.size()); assertEquals(originalReceipt.getId(), receipts.getFirst().getId());
        assertEquals(2, movements.size()); assertEquals(originalMovement.getId(), movements.getFirst().getId());
        assertEquals(-1, movements.get(1).getCanonicalDelta());
        assertEquals(8, variants.findById(variantId).orElseThrow().getStockAvailable());
    }

    @Test
    void purchaseListFiltersBySupplierAndStatus() throws Exception {
        String firstSupplier = createSupplier("Filtered Supplier");
        String secondSupplier = createSupplier("Other Supplier");
        String firstMapping = createVariantMapping(firstSupplier, variantId, "1");
        String secondMapping = createVariantMapping(secondSupplier, variantId, "1");
        createPurchase(firstSupplier, firstMapping, "Invoice", "FILTER-1", "1");
        createPurchase(secondSupplier, secondMapping, "Invoice", "FILTER-2", "1");
        mvc.perform(get("/api/admin/procurement/purchases")
                        .param("status", "PENDING").param("supplierId", firstSupplier)
                        .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].supplierId").value(firstSupplier));
    }

    private org.springframework.test.web.servlet.ResultActions confirmReceipt(String purchaseId, String key, String body) throws Exception {
        return mvc.perform(post("/api/admin/procurement/purchases/{id}/receipts/confirm", purchaseId)
                .header("Idempotency-Key", key).with(httpBasic("admin-user", "admin-pass")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    private double counter(String name) {
        var counter = meterRegistry.find(name).counter();
        return counter == null ? 0 : counter.count();
    }

    private long suppliersCount() {
        return supplierRepository.count();
    }

    private String createSupplier(String name) throws Exception {
        String json = mvc.perform(post("/api/admin/procurement/suppliers")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\",\"taxIdentity\":\"20-123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("id").asText();
    }

    private String createVariantMapping(String supplierId, UUID targetId, String conversion) throws Exception {
        String json = mvc.perform(post("/api/admin/procurement/mappings")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(mappingBody(supplierId, targetId, conversion)
                                .replace("SKU-1", "SKU-" + UUID.randomUUID())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions repairMapping(String mappingId, String type, UUID product, UUID variant) throws Exception {
        return mvc.perform(put("/api/admin/procurement/mappings/{id}/repair", mappingId)
                .with(httpBasic("admin-user", "admin-pass")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"%s\",\"productId\":%s,\"variantId\":%s,\"defaultConversion\":\"1\",\"active\":true}".formatted(
                        type, product == null ? "null" : "\"" + product + "\"", variant == null ? "null" : "\"" + variant + "\"")));
    }

    private String createPurchase(String supplierId, String mappingId, String type, String number, String quantity) throws Exception {
        String json = mvc.perform(post("/api/admin/procurement/purchases")
                        .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(purchaseBody(supplierId, mappingId, type, number, quantity)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("id").asText();
    }

    private String purchaseLineId(String purchaseId) throws Exception {
        String json = mvc.perform(get("/api/admin/procurement/purchases/{id}", purchaseId)
                        .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode lines = mapper.readTree(json).get("lines");
        return lines.get(0).get("id").asText();
    }

    private String mappingBody(String supplierId, UUID targetId, String conversion) {
        return "{\"supplierId\":\"%s\",\"supplierItemCode\":\"SKU-1\",\"description\":\"Supplier item\",\"targetType\":\"VARIANT_UNIT\",\"variantId\":\"%s\",\"defaultConversion\":\"%s\"}"
                .formatted(supplierId, targetId, conversion);
    }

    private String closeCommand(String lineId, String quantity) {
        return """
                {"lines":[{"purchaseLineId":"%s","dispositions":[
                  {"type":"NOT_DELIVERABLE_FINAL","quantity":"%s","note":"Supplier confirmed unavailable"}]}]}
                """.formatted(lineId, quantity);
    }

    private String purchaseBody(String supplierId, String mappingId, String type, String number, String quantity) {
        return "{\"supplierId\":\"%s\",\"documentType\":\"%s\",\"documentNumber\":\"%s\",\"purchasedAt\":\"2026-08-21\",\"lines\":[{\"mappingId\":\"%s\",\"orderedQuantity\":\"%s\",\"conversion\":\"2.000000\"}]}"
                .formatted(supplierId, type, number, mappingId, quantity);
    }
}
