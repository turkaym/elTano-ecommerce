package com.eltano.ecommerce.procurement.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.catalog.repository.ProductRepository;
import com.eltano.ecommerce.catalog.repository.ProductVariantRepository;
import com.eltano.ecommerce.common.api.ResourceNotFoundException;
import com.eltano.ecommerce.inventory.service.InventoryDelta;
import com.eltano.ecommerce.inventory.service.InventoryInvariantException;
import com.eltano.ecommerce.inventory.service.InventoryMutation;
import com.eltano.ecommerce.inventory.service.InventoryMutationService;
import com.eltano.ecommerce.procurement.domain.DispositionQuantity;
import com.eltano.ecommerce.procurement.domain.DispositionType;
import com.eltano.ecommerce.procurement.domain.InventoryTargetType;
import com.eltano.ecommerce.procurement.domain.ProcurementRules;
import com.eltano.ecommerce.procurement.domain.Purchase;
import com.eltano.ecommerce.procurement.domain.PurchaseLine;
import com.eltano.ecommerce.procurement.domain.PurchaseReceipt;
import com.eltano.ecommerce.procurement.domain.PurchaseReceiptDisposition;
import com.eltano.ecommerce.procurement.domain.PurchaseReceiptLine;
import com.eltano.ecommerce.procurement.domain.PurchaseStatus;
import com.eltano.ecommerce.procurement.domain.ReceiptKind;
import com.eltano.ecommerce.procurement.domain.StockMovement;
import com.eltano.ecommerce.procurement.domain.Supplier;
import com.eltano.ecommerce.procurement.domain.SupplierItemMapping;
import com.eltano.ecommerce.procurement.repository.PurchaseReceiptRepository;
import com.eltano.ecommerce.procurement.repository.PurchaseRepository;
import com.eltano.ecommerce.procurement.repository.StockMovementRepository;
import com.eltano.ecommerce.procurement.repository.SupplierItemMappingRepository;
import com.eltano.ecommerce.procurement.repository.SupplierRepository;

@Service
public class ProcurementService {
    private final SupplierRepository suppliers;
    private final SupplierItemMappingRepository mappings;
    private final PurchaseRepository purchases;
    private final PurchaseReceiptRepository receipts;
    private final StockMovementRepository movements;
    private final InventoryMutationService inventory;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final MeterRegistry metrics;

    public ProcurementService(SupplierRepository suppliers, SupplierItemMappingRepository mappings,
            PurchaseRepository purchases, PurchaseReceiptRepository receipts, StockMovementRepository movements,
            InventoryMutationService inventory, ProductRepository products, ProductVariantRepository variants, MeterRegistry metrics) {
        this.suppliers = suppliers;
        this.mappings = mappings;
        this.purchases = purchases;
        this.receipts = receipts;
        this.movements = movements;
        this.inventory = inventory;
        this.products = products;
        this.variants = variants;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> listSuppliers() { return suppliers.findAll().stream().map(this::supplierResponse).toList(); }

    @Transactional
    public SupplierResponse createSupplier(SupplierCommand command) {
        requireText(command.name(), "Supplier name is required");
        Supplier supplier = new Supplier();
        supplier.setName(command.name().trim());
        supplier.setTaxIdentity(trim(command.taxIdentity()));
        supplier.setActive(command.active() == null || command.active());
        return supplierResponse(suppliers.save(supplier));
    }

    @Transactional
    public SupplierResponse updateSupplier(UUID id, SupplierCommand command) {
        Supplier supplier = supplier(id);
        if (command.name() != null) { requireText(command.name(), "Supplier name is required"); supplier.setName(command.name().trim()); }
        if (command.taxIdentity() != null) supplier.setTaxIdentity(trim(command.taxIdentity()));
        if (command.active() != null) supplier.setActive(command.active());
        return supplierResponse(supplier);
    }

    @Transactional(readOnly = true)
    public List<MappingResponse> listMappings(UUID supplierId) {
        List<SupplierItemMapping> values = supplierId == null ? mappings.findAll() : mappings.findAllBySupplierIdOrderBySupplierItemCode(supplierId);
        return values.stream().map(this::mappingResponse).toList();
    }

    @Transactional
    public MappingResponse createMapping(MappingCommand command) {
        Supplier supplier = supplier(command.supplierId());
        if (!supplier.isActive()) conflict("INVALID_STATE", "Inactive suppliers cannot receive new mappings");
        validateMapping(command);
        String normalized = normalize(command.supplierItemCode());
        if (mappings.existsBySupplierIdAndNormalizedCode(supplier.getId(), normalized)) conflict("INVALID_STATE", "Supplier item code already exists");
        SupplierItemMapping mapping = new SupplierItemMapping();
        mapping.setSupplier(supplier);
        applyMapping(mapping, command, normalized);
        return mappingResponse(mappings.save(mapping));
    }

    @Transactional
    public MappingResponse updateMapping(UUID id, MappingCommand command) {
        SupplierItemMapping mapping = mapping(id);
        if (command.active() != null) mapping.setActive(command.active());
        if (command.description() != null) mapping.setDescription(command.description().trim());
        if (command.defaultConversion() != null) {
            requirePositive(command.defaultConversion(), "Conversion must be positive");
            mapping.setDefaultConversion(command.defaultConversion());
        }
        return mappingResponse(mapping);
    }

    @Transactional
    public PurchaseResponse createPurchase(PurchaseCommand command, String actor) {
        Supplier supplier = supplier(command.supplierId());
        if (!supplier.isActive()) conflict("INVALID_STATE", "Inactive suppliers cannot receive new purchases");
        requireText(command.documentType(), "Document type is required");
        requireText(command.documentNumber(), "Document number is required");
        if (command.lines() == null || command.lines().isEmpty()) throw new IllegalArgumentException("Purchase lines are required");
        String type = normalize(command.documentType());
        String number = normalize(command.documentNumber());
        if (purchases.existsBySupplierIdAndNormalizedDocumentTypeAndNormalizedDocumentNumber(supplier.getId(), type, number)) {
            conflict("DUPLICATE_DOCUMENT", "A purchase with the same normalized document already exists");
        }
        Purchase purchase = new Purchase();
        purchase.setSupplier(supplier);
        purchase.setDocumentType(command.documentType());
        purchase.setDocumentNumber(command.documentNumber());
        purchase.setNormalizedDocumentType(type);
        purchase.setNormalizedDocumentNumber(number);
        purchase.setPurchasedAt(command.purchasedAt() == null ? LocalDate.now() : command.purchasedAt());
        purchase.setCreatedBy(actor);
        purchase.replaceLines(command.lines().stream().map(line -> snapshotLine(supplier, line)).toList());
        try { return purchaseResponse(purchases.saveAndFlush(purchase)); }
        catch (DataIntegrityViolationException exception) { conflict("DUPLICATE_DOCUMENT", "A purchase with the same normalized document already exists"); return null; }
    }

    @Transactional
    public PurchaseResponse updatePurchase(UUID id, PurchaseCommand command) {
        Purchase purchase = lockedPurchase(id);
        if (purchase.getStatus() != PurchaseStatus.PENDING || !receipts.findAllByPurchaseIdOrderByConfirmedAt(id).isEmpty()) {
            conflict("INVALID_STATE", "Only unconfirmed pending purchases can be edited");
        }
        if (command.purchasedAt() != null) purchase.setPurchasedAt(command.purchasedAt());
        if (command.lines() != null && !command.lines().isEmpty()) {
            purchase.replaceLines(command.lines().stream().map(line -> snapshotLine(purchase.getSupplier(), line)).toList());
        }
        return purchaseResponse(purchase);
    }

    @Transactional(readOnly = true)
    public List<PurchaseResponse> listPurchases(PurchaseStatus status, UUID supplierId) {
        List<Purchase> values;
        if (supplierId == null) {
            values = status == null ? purchases.findAllByOrderByPurchasedAtDesc() : purchases.findAllByStatusOrderByPurchasedAtDesc(status);
        } else {
            values = status == null ? purchases.findAllBySupplierIdOrderByPurchasedAtDesc(supplierId)
                    : purchases.findAllBySupplierIdAndStatusOrderByPurchasedAtDesc(supplierId, status);
        }
        return values.stream().map(this::purchaseResponse).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseResponse getPurchase(UUID id) { return purchaseResponse(purchases.findDetailedById(id).orElseThrow(() -> notFound("Purchase"))); }

    @Transactional(readOnly = true)
    public ReceiptResponse preview(UUID purchaseId, ReceiptCommand command) {
        Purchase purchase = purchases.findDetailedById(purchaseId).orElseThrow(() -> notFound("Purchase"));
        validateReceipt(purchase, command);
        return new ReceiptResponse(null, purchase.getStatus(), false, previewDeltas(purchase, command));
    }

    @Transactional
    public ReceiptResponse confirm(UUID purchaseId, ReceiptCommand command, String key, String actor, String correlationId) {
        requireKey(key);
        Purchase purchase = observedLockedPurchase(purchaseId);
        String hash = hash(command);
        var replay = receipts.findByPurchaseIdAndKindAndIdempotencyKey(purchaseId, ReceiptKind.RECEIPT, key);
        if (replay.isPresent()) {
            ReceiptResponse response = replay(replay.get(), hash, purchase.getStatus());
            metrics.counter("procurement.receipt.replays").increment();
            return response;
        }
        if (purchase.getStatus() != PurchaseStatus.PENDING) conflict("INVALID_STATE", "Only pending purchases can receive stock");
        validateReceipt(purchase, command);
        List<CanonicalDelta> preview = previewDeltas(purchase, command);
        PurchaseReceipt receipt = evidence(purchase, ReceiptKind.RECEIPT, key, hash, null, actor, correlationId, command);
        receipts.saveAndFlush(receipt);
        List<InventoryMutation> applied = inventory.apply(preview.stream().map(this::inventoryDelta).toList());
        saveMovements(purchase, receipt, applied, preview, actor, correlationId, "RECEIPT");
        purchase.setStatus(deriveStatus(purchase));
        metrics.counter("procurement.receipt.confirmations").increment();
        return new ReceiptResponse(receipt.getId(), purchase.getStatus(), false, preview);
    }

    @Transactional
    public ReceiptResponse correct(UUID purchaseId, CorrectionCommand command, String key, String actor, String correlationId) {
        requireKey(key);
        requireText(command.reason(), "Correction reason is required");
        Purchase purchase = observedLockedPurchase(purchaseId);
        String hash = correctionHash(command);
        var replay = receipts.findByPurchaseIdAndKindAndIdempotencyKey(purchaseId, ReceiptKind.CORRECTION, key);
        if (replay.isPresent()) return replay(replay.get(), hash, purchase.getStatus());
        if (purchase.getStatus() == PurchaseStatus.CANCELLED) conflict("INVALID_STATE", "Cancelled purchases cannot be corrected");
        if (command.deltas() == null || command.deltas().isEmpty()) throw new IllegalArgumentException("Correction deltas are required");
        if (command.deltas().stream().anyMatch(delta -> delta.delta() == 0)) throw new IllegalArgumentException("Correction deltas cannot be zero");
        var allowedTargets = purchase.getLines().stream()
                .map(line -> line.getTargetType() + ":" + targetId(line)).collect(Collectors.toSet());
        if (command.deltas().stream().anyMatch(delta -> !allowedTargets.contains(delta.targetType() + ":" + delta.targetId())))
            conflict("INVALID_STATE", "Correction target does not belong to purchase");
        PurchaseReceipt receipt = evidence(purchase, ReceiptKind.CORRECTION, key, hash, command.reason().trim(), actor, correlationId, null);
        receipts.saveAndFlush(receipt);
        List<CanonicalDelta> requested = command.deltas().stream().map(delta -> new CanonicalDelta(delta.targetType(), delta.targetId(), delta.delta(), null, BigDecimal.ONE)).toList();
        try {
            List<InventoryMutation> applied = inventory.apply(requested.stream().map(this::inventoryDelta).toList());
            saveMovements(purchase, receipt, applied, requested, actor, correlationId, "CORRECTION");
            metrics.counter("procurement.receipt.corrections").increment();
        } catch (InventoryInvariantException exception) {
            metrics.counter("procurement.reversal.blocked").increment();
            conflict("REVERSAL_BLOCKED", exception.getMessage());
        }
        return new ReceiptResponse(receipt.getId(), purchase.getStatus(), false, requested);
    }

    @Transactional
    public ReceiptResponse cancel(UUID purchaseId, ReasonCommand command, String key, String actor, String correlationId) {
        requireKey(key);
        requireText(command.reason(), "Cancellation reason is required");
        Purchase purchase = observedLockedPurchase(purchaseId);
        String hash = ReceiptCanonicalizer.hash(List.of(new ReceiptCanonicalizer.Line(purchaseId, DispositionType.NOT_DELIVERABLE_FINAL, BigDecimal.ONE, command.reason())));
        var replay = receipts.findByPurchaseIdAndKindAndIdempotencyKey(purchaseId, ReceiptKind.CANCELLATION, key);
        if (replay.isPresent()) return replay(replay.get(), hash, purchase.getStatus());
        if (purchase.getStatus() == PurchaseStatus.CANCELLED) conflict("INVALID_STATE", "Purchase is already cancelled");
        List<StockMovement> originals = movements.findAllByPurchaseIdOrderByCreatedAt(purchaseId);
        List<CanonicalDelta> inverse = originals.stream().map(movement -> new CanonicalDelta(
                movement.getTargetType(), movement.getTargetId(), -movement.getCanonicalDelta(), movement.getQuantity(), movement.getConversion())).toList();
        PurchaseReceipt receipt = evidence(purchase, ReceiptKind.CANCELLATION, key, hash, command.reason().trim(), actor, correlationId, null);
        receipts.saveAndFlush(receipt);
        List<CanonicalDelta> cancellation = List.of();
        try {
            List<InventoryMutation> applied = inventory.apply(inverse.stream().map(this::inventoryDelta).toList());
            cancellation = applied.stream().map(mutation -> new CanonicalDelta(mutation.targetType(), mutation.targetId(),
                    mutation.delta(), BigDecimal.valueOf(Math.abs((long) mutation.delta())), BigDecimal.ONE)).toList();
            saveCancellationMovements(purchase, receipt, applied, actor, correlationId);
        } catch (InventoryInvariantException exception) {
            metrics.counter("procurement.reversal.blocked").increment();
            conflict("REVERSAL_BLOCKED", exception.getMessage());
        }
        purchase.setStatus(PurchaseStatus.CANCELLED);
        metrics.counter("procurement.receipt.cancellations").increment();
        return new ReceiptResponse(receipt.getId(), purchase.getStatus(), false, cancellation);
    }

    private PurchaseReceipt evidence(Purchase purchase, ReceiptKind kind, String key, String hash, String note,
            String actor, String correlationId, ReceiptCommand command) {
        PurchaseReceipt receipt = new PurchaseReceipt();
        receipt.setPurchase(purchase); receipt.setKind(kind); receipt.setIdempotencyKey(key); receipt.setRequestHash(hash);
        receipt.setNote(note); receipt.setActor(actor); receipt.setCorrelationId(correlationId);
        if (command != null) {
            Map<UUID, PurchaseLine> lines = purchase.getLines().stream().collect(Collectors.toMap(PurchaseLine::getId, Function.identity()));
            for (ReceiptLineCommand lineCommand : command.lines()) {
                PurchaseReceiptLine receiptLine = new PurchaseReceiptLine();
                receiptLine.setPurchaseLine(lines.get(lineCommand.purchaseLineId()));
                for (DispositionCommand dispositionCommand : lineCommand.dispositions()) {
                    PurchaseReceiptDisposition disposition = new PurchaseReceiptDisposition();
                    disposition.setType(dispositionCommand.type()); disposition.setQuantity(dispositionCommand.quantity());
                    disposition.setNote(trim(dispositionCommand.note())); receiptLine.addDisposition(disposition);
                }
                receipt.addLine(receiptLine);
            }
        }
        return receipt;
    }

    private void validateReceipt(Purchase purchase, ReceiptCommand command) {
        if (command == null || command.lines() == null || command.lines().isEmpty()) throw new IllegalArgumentException("Receipt lines are required");
        Map<UUID, PurchaseLine> lines = purchase.getLines().stream().collect(Collectors.toMap(PurchaseLine::getId, Function.identity()));
        for (ReceiptLineCommand line : command.lines()) {
            if (!lines.containsKey(line.purchaseLineId())) throw new IllegalArgumentException("Receipt line does not belong to purchase");
            if (line.dispositions() == null || line.dispositions().isEmpty()) throw new IllegalArgumentException("Dispositions are required");
            line.dispositions().forEach(item -> { requirePositive(item.quantity(), "Disposition quantity must be positive"); ProcurementRules.validateNote(item.type(), item.note()); });
        }
        Map<UUID, List<DispositionQuantity>> prior = finalDispositionProgress(purchase.getId());
        command.lines().forEach(line -> line.dispositions().stream()
                .filter(item -> item.type() != DispositionType.TEMP_MISSING && item.type() != DispositionType.ACCEPTED_EXCESS)
                .forEach(item -> prior.computeIfAbsent(line.purchaseLineId(), ignored -> new ArrayList<>()).add(new DispositionQuantity(item.type(), item.quantity()))));
        purchase.getLines().forEach(line -> ProcurementRules.progress(line.getOrderedQuantity(), prior.getOrDefault(line.getId(), List.of())));
    }

    private List<CanonicalDelta> previewDeltas(Purchase purchase, ReceiptCommand command) {
        Map<UUID, PurchaseLine> lines = purchase.getLines().stream().collect(Collectors.toMap(PurchaseLine::getId, Function.identity()));
        return command.lines().stream().flatMap(lineCommand -> lineCommand.dispositions().stream()
                .filter(item -> item.type() == DispositionType.ACCEPTED_ORDERED || item.type() == DispositionType.ACCEPTED_EXCESS)
                .map(item -> {
                    PurchaseLine line = lines.get(lineCommand.purchaseLineId());
                    return new CanonicalDelta(line.getTargetType(), targetId(line), ProcurementRules.toCanonical(item.quantity(), line.getConversion()), item.quantity(), line.getConversion());
                })).toList();
    }

    private PurchaseStatus deriveStatus(Purchase purchase) {
        Map<UUID, List<DispositionQuantity>> progress = finalDispositionProgress(purchase.getId());
        return purchase.getLines().stream().allMatch(line -> ProcurementRules.progress(line.getOrderedQuantity(), progress.getOrDefault(line.getId(), List.of())).status() == PurchaseStatus.RECEIVED)
                ? PurchaseStatus.RECEIVED : PurchaseStatus.PENDING;
    }

    private Map<UUID, List<DispositionQuantity>> finalDispositionProgress(UUID purchaseId) {
        return receipts.findAllByPurchaseIdOrderByConfirmedAt(purchaseId).stream()
                .filter(receipt -> receipt.getKind() == ReceiptKind.RECEIPT)
                .flatMap(receipt -> receipt.getLines().stream())
                .collect(Collectors.toMap(line -> line.getPurchaseLine().getId(), line -> line.getDispositions().stream()
                        .filter(item -> item.getType() != DispositionType.TEMP_MISSING && item.getType() != DispositionType.ACCEPTED_EXCESS)
                        .map(item -> new DispositionQuantity(item.getType(), item.getQuantity())).collect(Collectors.toCollection(ArrayList::new)),
                        (left, right) -> { left.addAll(right); return left; }));
    }

    private void saveMovements(Purchase purchase, PurchaseReceipt receipt, List<InventoryMutation> applied,
            List<CanonicalDelta> requested, String actor, String correlationId, String sourceType) {
        Map<String, InventoryMutation> byTarget = applied.stream().collect(Collectors.toMap(
                item -> item.targetType() + ":" + item.targetId(), Function.identity()));
        Map<String, Integer> balances = new java.util.HashMap<>();
        List<StockMovement> entities = new ArrayList<>();
        for (CanonicalDelta source : requested) {
            String target = source.targetType() + ":" + source.targetId();
            InventoryMutation mutation = byTarget.get(target);
            if (mutation == null) continue;
            int before = balances.getOrDefault(target, mutation.beforeBalance());
            int after = Math.addExact(before, source.delta());
            StockMovement movement = new StockMovement();
            movement.setSourceType(sourceType); movement.setSourceId(UUID.randomUUID()); movement.setPurchase(purchase); movement.setReceipt(receipt);
            movement.setTargetType(source.targetType()); movement.setTargetId(source.targetId());
            movement.setQuantity(source.quantity() == null ? BigDecimal.valueOf(Math.abs((long) source.delta())) : source.quantity());
            movement.setConversion(source.conversion() == null ? BigDecimal.ONE : source.conversion());
            movement.setCanonicalDelta(source.delta()); movement.setBeforeBalance(before); movement.setAfterBalance(after);
            movement.setActor(actor); movement.setCorrelationId(correlationId); entities.add(movement);
            balances.put(target, after);
        }
        movements.saveAll(entities);
    }

    private void saveCancellationMovements(Purchase purchase, PurchaseReceipt receipt,
            List<InventoryMutation> applied, String actor, String correlationId) {
        List<StockMovement> entities = applied.stream().map(mutation -> {
            StockMovement movement = new StockMovement();
            movement.setSourceType("CANCELLATION"); movement.setSourceId(UUID.randomUUID());
            movement.setPurchase(purchase); movement.setReceipt(receipt);
            movement.setTargetType(mutation.targetType()); movement.setTargetId(mutation.targetId());
            movement.setQuantity(BigDecimal.valueOf(Math.abs((long) mutation.delta()))); movement.setConversion(BigDecimal.ONE);
            movement.setCanonicalDelta(mutation.delta()); movement.setBeforeBalance(mutation.beforeBalance());
            movement.setAfterBalance(mutation.afterBalance()); movement.setActor(actor); movement.setCorrelationId(correlationId);
            return movement;
        }).toList();
        movements.saveAll(entities);
    }

    private ReceiptResponse replay(PurchaseReceipt receipt, String hash, PurchaseStatus status) {
        if (!receipt.getRequestHash().equals(hash)) conflict("IDEMPOTENCY_CONFLICT", "Idempotency key was already used with different content");
        List<CanonicalDelta> deltas = movements.findAllByReceiptIdOrderByCreatedAt(receipt.getId()).stream()
                .map(value -> new CanonicalDelta(value.getTargetType(), value.getTargetId(), value.getCanonicalDelta(), value.getQuantity(), value.getConversion()))
                .toList();
        return new ReceiptResponse(receipt.getId(), status, true, deltas);
    }
    private InventoryDelta inventoryDelta(CanonicalDelta value) { return new InventoryDelta(value.targetType(), value.targetId(), value.delta()); }
    private String hash(ReceiptCommand command) { return ReceiptCanonicalizer.hash(command.lines().stream().flatMap(line -> line.dispositions().stream().map(item -> new ReceiptCanonicalizer.Line(line.purchaseLineId(), item.type(), item.quantity(), item.note()))).toList()); }
    private String correctionHash(CorrectionCommand command) {
        return ReceiptCanonicalizer.hash(command.deltas().stream().map(item -> new ReceiptCanonicalizer.Line(
                item.targetId(), DispositionType.ACCEPTED_ORDERED, BigDecimal.valueOf(Math.abs((long) item.delta())),
                item.targetType() + "|" + item.delta() + "|" + command.reason().trim())).toList());
    }

    private PurchaseLine snapshotLine(Supplier supplier, PurchaseLineCommand command) {
        SupplierItemMapping mapping = mapping(command.mappingId());
        if (!mapping.getSupplier().getId().equals(supplier.getId()) || !mapping.isActive()) conflict("INVALID_STATE", "Purchase mapping is inactive or belongs to another supplier");
        BigDecimal conversion = command.conversion() == null ? mapping.getDefaultConversion() : command.conversion();
        requirePositive(command.orderedQuantity(), "Ordered quantity must be positive"); requirePositive(conversion, "Conversion must be positive");
        ProcurementRules.toCanonical(command.orderedQuantity(), conversion);
        PurchaseLine line = new PurchaseLine(); line.setMappingId(mapping.getId()); line.setSupplierItemCode(mapping.getSupplierItemCode());
        line.setSupplierDescription(mapping.getDescription()); line.setTargetType(mapping.getTargetType()); line.setProductId(mapping.getProductId());
        line.setVariantId(mapping.getVariantId()); line.setOrderedQuantity(command.orderedQuantity()); line.setConversion(conversion); return line;
    }
    private void validateMapping(MappingCommand command) {
        requireText(command.supplierItemCode(), "Supplier item code is required"); requireText(command.description(), "Description is required");
        requirePositive(command.defaultConversion(), "Conversion must be positive");
        boolean valid = command.targetType() == InventoryTargetType.VARIANT_UNIT && command.variantId() != null && command.productId() == null
                || command.targetType() == InventoryTargetType.BULK_GRAM && command.productId() != null && command.variantId() == null;
        if (!valid) throw new IllegalArgumentException("Mapping target must select exactly one compatible target");
        Product product = command.targetType() == InventoryTargetType.VARIANT_UNIT
                ? variants.findById(command.variantId()).map(ProductVariant::getProduct).orElseThrow(() -> notFound("Variant"))
                : products.findById(command.productId()).orElseThrow(() -> notFound("Product"));
        InventoryPolicy required = command.targetType() == InventoryTargetType.VARIANT_UNIT
                ? InventoryPolicy.PER_VARIANT : InventoryPolicy.BULK_WEIGHT;
        if (product.getInventoryPolicy() != required) throw new IllegalArgumentException("Mapping target is incompatible with product inventory policy");
    }
    private void applyMapping(SupplierItemMapping mapping, MappingCommand command, String normalized) {
        mapping.setSupplierItemCode(command.supplierItemCode()); mapping.setNormalizedCode(normalized); mapping.setDescription(command.description().trim());
        mapping.setTargetType(command.targetType()); mapping.setProductId(command.productId()); mapping.setVariantId(command.variantId());
        mapping.setDefaultConversion(command.defaultConversion()); mapping.setActive(command.active() == null || command.active());
    }
    private Supplier supplier(UUID id) { return suppliers.findById(id).orElseThrow(() -> notFound("Supplier")); }
    private SupplierItemMapping mapping(UUID id) { return mappings.findById(id).orElseThrow(() -> notFound("Mapping")); }
    private Purchase lockedPurchase(UUID id) { return purchases.findByIdForUpdate(id).orElseThrow(() -> notFound("Purchase")); }
    private Purchase observedLockedPurchase(UUID id) {
        Timer.Sample sample = Timer.start(metrics);
        try { return lockedPurchase(id); }
        finally { sample.stop(metrics.timer("procurement.inventory.lock.duration")); }
    }
    private UUID targetId(PurchaseLine line) { return line.getTargetType() == InventoryTargetType.BULK_GRAM ? line.getProductId() : line.getVariantId(); }
    private static String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    private static String trim(String value) { return value == null ? null : value.trim(); }
    private static void requireText(String value, String message) { if (value == null || value.isBlank()) throw new IllegalArgumentException(message); }
    private static void requirePositive(BigDecimal value, String message) { if (value == null || value.signum() <= 0) throw new IllegalArgumentException(message); }
    private static void requireKey(String key) { requireText(key, "Idempotency-Key is required"); }
    private static void conflict(String code, String message) { throw new ProcurementConflictException(code, message); }
    private static ResourceNotFoundException notFound(String type) { return new ResourceNotFoundException(type + " not found"); }

    private SupplierResponse supplierResponse(Supplier value) { return new SupplierResponse(value.getId(), value.getName(), value.getTaxIdentity(), value.isActive()); }
    private MappingResponse mappingResponse(SupplierItemMapping value) { return new MappingResponse(value.getId(), value.getSupplier().getId(), value.getSupplierItemCode(), value.getDescription(), value.getTargetType(), value.getProductId(), value.getVariantId(), value.getDefaultConversion(), value.isActive()); }
    private PurchaseResponse purchaseResponse(Purchase value) {
        Map<UUID, List<DispositionQuantity>> dispositionProgress = finalDispositionProgress(value.getId());
        List<PurchaseLineResponse> lineResponses = value.getLines().stream().map(line -> {
            BigDecimal outstanding = ProcurementRules.progress(line.getOrderedQuantity(), dispositionProgress.getOrDefault(line.getId(), List.of())).outstanding();
            return new PurchaseLineResponse(line.getId(), line.getMappingId(), line.getSupplierItemCode(), line.getSupplierDescription(),
                    line.getTargetType(), line.getProductId(), line.getVariantId(), line.getOrderedQuantity(), line.getConversion(), outstanding);
        }).toList();
        BigDecimal ordered = value.getLines().stream().map(PurchaseLine::getOrderedQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = lineResponses.stream().map(PurchaseLineResponse::outstandingQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        String progress = decimal(ordered.subtract(outstanding)) + " / " + decimal(ordered);
        return new PurchaseResponse(value.getId(), value.getSupplier().getId(), value.getSupplier().getName(), value.getDocumentType(),
                value.getDocumentNumber(), value.getPurchasedAt(), value.getStatus(), progress, lineResponses, value.getCreatedAt());
    }
    private static String decimal(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    public record SupplierCommand(String name, String taxIdentity, Boolean active) { }
    public record SupplierResponse(UUID id, String name, String taxIdentity, boolean active) { }
    public record MappingCommand(UUID supplierId, String supplierItemCode, String description, InventoryTargetType targetType, UUID productId, UUID variantId, BigDecimal defaultConversion, Boolean active) { }
    public record MappingResponse(UUID id, UUID supplierId, String supplierItemCode, String description, InventoryTargetType targetType, UUID productId, UUID variantId, BigDecimal defaultConversion, boolean active) { }
    public record PurchaseLineCommand(UUID mappingId, BigDecimal orderedQuantity, BigDecimal conversion) { }
    public record PurchaseCommand(UUID supplierId, String documentType, String documentNumber, LocalDate purchasedAt, List<PurchaseLineCommand> lines) { }
    public record PurchaseLineResponse(UUID id, UUID mappingId, String supplierItemCode, String supplierDescription, InventoryTargetType targetType, UUID productId, UUID variantId, BigDecimal orderedQuantity, BigDecimal conversion, BigDecimal outstandingQuantity) { }
    public record PurchaseResponse(UUID id, UUID supplierId, String supplierName, String documentType, String documentNumber, LocalDate purchasedAt, PurchaseStatus status, String progress, List<PurchaseLineResponse> lines, java.time.Instant createdAt) { }
    public record DispositionCommand(DispositionType type, BigDecimal quantity, String note) { }
    public record ReceiptLineCommand(UUID purchaseLineId, List<DispositionCommand> dispositions) { }
    public record ReceiptCommand(List<ReceiptLineCommand> lines) { }
    public record CanonicalDelta(InventoryTargetType targetType, UUID targetId, int delta, BigDecimal quantity, BigDecimal conversion) { }
    public record ReceiptResponse(UUID receiptId, PurchaseStatus status, boolean replayed, List<CanonicalDelta> canonicalDeltas) { }
    public record ReasonCommand(String reason) { }
    public record CorrectionDelta(InventoryTargetType targetType, UUID targetId, int delta) { }
    public record CorrectionCommand(String reason, List<CorrectionDelta> deltas) { }
}
