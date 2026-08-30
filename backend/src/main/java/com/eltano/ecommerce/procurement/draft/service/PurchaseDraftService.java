package com.eltano.ecommerce.procurement.draft.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.eltano.ecommerce.procurement.domain.InventoryTargetType;
import com.eltano.ecommerce.procurement.domain.Supplier;
import com.eltano.ecommerce.procurement.draft.domain.PurchaseDraft;
import com.eltano.ecommerce.procurement.draft.domain.PurchaseDraftImportKey;
import com.eltano.ecommerce.procurement.draft.domain.PurchaseDraftLine;
import com.eltano.ecommerce.procurement.draft.domain.PurchaseDraftMatchStatus;
import com.eltano.ecommerce.procurement.draft.domain.PurchaseDraftSourceType;
import com.eltano.ecommerce.procurement.draft.domain.PurchaseDraftStatus;
import com.eltano.ecommerce.procurement.draft.domain.PurchaseDraftUnit;
import com.eltano.ecommerce.procurement.draft.repository.PurchaseDraftRepository;
import com.eltano.ecommerce.procurement.draft.repository.PurchaseDraftImportKeyRepository;
import com.eltano.ecommerce.procurement.repository.SupplierRepository;
import com.eltano.ecommerce.procurement.service.ProcurementService;
import com.eltano.ecommerce.procurement.service.ProcurementService.ImportedPurchaseLine;

@Service
public class PurchaseDraftService {
    private static final String DUPLICATE_ROW_ERROR = "La fila esta duplicada exactamente dentro del archivo.";
    private final PurchaseDraftRepository drafts;
    private final PurchaseDraftImportKeyRepository importKeys;
    private final SupplierRepository suppliers;
    private final PurchaseWorkbookParser parser;
    private final SupplierProductNameNormalizer normalizer;
    private final PurchaseDraftProductMatcher matcher;
    private final PrivatePurchaseFileStorage storage;
    private final ProcurementService procurement;

    public PurchaseDraftService(PurchaseDraftRepository drafts, PurchaseDraftImportKeyRepository importKeys, SupplierRepository suppliers,
            PurchaseWorkbookParser parser, SupplierProductNameNormalizer normalizer,
            PurchaseDraftProductMatcher matcher, PrivatePurchaseFileStorage storage, ProcurementService procurement) {
        this.drafts = drafts; this.importKeys = importKeys; this.suppliers = suppliers; this.parser = parser; this.normalizer = normalizer;
        this.matcher = matcher; this.storage = storage; this.procurement = procurement;
    }

    public byte[] template() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Compra");
            Row header = sheet.createRow(0);
            List.of("fecha", "producto", "cantidad", "unidad").forEach(value -> header.createCell(header.getLastCellNum() < 0 ? 0 : header.getLastCellNum()).setCellValue(value));
            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue(LocalDate.now().toString());
            example.createCell(1).setCellValue("Nombre del producto");
            example.createCell(2).setCellValue(1);
            example.createCell(3).setCellValue("unidad");
            for (int i = 0; i < 4; i++) sheet.autoSizeColumn(i);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) { throw new IllegalStateException("No se pudo generar la plantilla.", exception); }
    }

    @Transactional
    public DraftResponse importWorkbook(UUID supplierId, MultipartFile file, String key, String actor) {
        requireKey(key);
        Supplier supplier = activeLockedSupplier(supplierId);
        byte[] content;
        try { content = file == null ? null : file.getBytes(); }
        catch (IOException exception) { throw invalid("INVALID_XLSX", "No se pudo leer el archivo XLSX."); }
        String hash = sha256(content == null ? new byte[0] : content);
        var keyed = importKeys.findBySupplierIdAndIdempotencyKey(supplierId, key);
        if (keyed.isPresent()) {
            if (!hash.equals(keyed.get().getSourceSha256())) throw conflict("IDEMPOTENCY_CONFLICT", "La clave de idempotencia ya se uso con otro archivo.");
            return duplicateResult(keyed.get().getDraft());
        }
        var duplicate = drafts.findBySupplierIdAndSourceSha256AndStatusNot(supplierId, hash, PurchaseDraftStatus.DELETED);
        if (duplicate.isPresent()) { importKeys.saveAndFlush(new PurchaseDraftImportKey(supplier, duplicate.get(), key, hash)); return duplicateResult(duplicate.get()); }

        var parsed = parser.parse(file);
        String storageKey = storage.store(content);
        PurchaseDraft draft = new PurchaseDraft();
        draft.setSupplier(supplier); draft.setSourceType(PurchaseDraftSourceType.XLSX); draft.setPurchaseDate(parsed.purchaseDate());
        draft.setOriginalFilename(safeFilename(file.getOriginalFilename())); draft.setSourceContentType(file.getContentType());
        draft.setSourceStorageKey(storageKey); draft.setSourceSha256(hash); draft.setSourceSize((long) content.length);
        draft.setCreatedBy(actor);
        parsed.lines().forEach(source -> draft.addLine(importedLine(supplierId, source)));
        refreshDuplicateErrors(draft);
        try { drafts.saveAndFlush(draft); importKeys.saveAndFlush(new PurchaseDraftImportKey(supplier, draft, key, hash)); return response(draft, false); }
        catch (RuntimeException exception) {
            storage.deleteQuietly(storageKey);
            throw exception;
        }
    }

    @Transactional
    public DraftResponse create(ManualDraftCommand command, String actor) {
        Supplier supplier = activeSupplier(command.supplierId());
        PurchaseDraft draft = new PurchaseDraft();
        draft.setSupplier(supplier); draft.setSourceType(PurchaseDraftSourceType.MANUAL); draft.setPurchaseDate(command.purchaseDate()); draft.setCreatedBy(actor);
        if (command.lines() != null) command.lines().forEach(value -> draft.addLine(manualLine(value, null, draft.getPurchaseDate())));
        refreshDuplicateErrors(draft);
        return response(drafts.saveAndFlush(draft), false);
    }

    @Transactional(readOnly = true)
    public List<DraftResponse> list() {
        return drafts.findAllByStatusNotOrderByUpdatedAtDesc(PurchaseDraftStatus.DELETED).stream().map(value -> response(value, false)).toList();
    }

    @Transactional(readOnly = true)
    public DraftResponse get(UUID id) { return response(detailed(id), false); }

    @Transactional
    public DraftResponse patch(UUID id, MetadataCommand command) {
        PurchaseDraft draft = mutableLocked(id, command.version());
        if (command.purchaseDate() != null) { draft.setPurchaseDate(command.purchaseDate()); if (draft.getSourceType() == PurchaseDraftSourceType.MANUAL) draft.getLines().forEach(line -> line.setSourceDate(command.purchaseDate())); }
        draft.invalidatePreview();
        return flushed(draft);
    }

    @Transactional
    public void delete(UUID id, VersionCommand command) {
        PurchaseDraft draft = mutableLocked(id, command.version());
        draft.setStatus(PurchaseDraftStatus.DELETED); draft.setDeletedAt(Instant.now()); draft.invalidatePreview(); drafts.flush();
    }

    @Transactional
    public DraftResponse addLine(UUID id, LineCommand command) {
        PurchaseDraft draft = mutableLocked(id, command.version());
        int nextRow = draft.getLines().stream().map(PurchaseDraftLine::getSourceRowNumber).filter(java.util.Objects::nonNull).max(Integer::compare).orElse(0) + 1;
        draft.addLine(manualLine(command, nextRow, draft.getPurchaseDate())); refreshDuplicateErrors(draft); draft.invalidatePreview();
        return flushed(draft);
    }

    @Transactional
    public DraftResponse patchLine(UUID id, UUID lineId, LineCommand command) {
        PurchaseDraft draft = mutableLocked(id, command.version());
        PurchaseDraftLine line = line(draft, lineId);
        Integer row = line.getSourceRowNumber();
        applyManualLine(line, command, row, draft.getPurchaseDate());
        refreshDuplicateErrors(draft);
        draft.invalidatePreview();
        return flushed(draft);
    }

    @Transactional
    public DraftResponse deleteLine(UUID id, UUID lineId, VersionCommand command) {
        PurchaseDraft draft = mutableLocked(id, command.version());
        draft.removeLine(line(draft, lineId)); refreshDuplicateErrors(draft); draft.invalidatePreview();
        return flushed(draft);
    }

    @Transactional(readOnly = true)
    public List<PurchaseDraftProductMatcher.CatalogCandidate> candidates(String query, PurchaseDraftUnit unit, int requestedLimit) {
        return matcher.candidates(query, unit, requestedLimit);
    }

    @Transactional
    public DraftResponse match(UUID id, UUID lineId, MatchCommand command) {
        PurchaseDraft draft = mutableLocked(id, command.version());
        PurchaseDraftLine line = line(draft, lineId);
        if (line.getQuantity() == null || line.getUnit() == null || line.getValidationErrors() != null) {
            throw conflict("LINE_INVALID", "La linea tiene errores y no puede vincularse.");
        }
        matcher.match(draft.getSupplier(), line, command.targetId(), command.remember());
        draft.invalidatePreview();
        return flushed(draft);
    }

    @Transactional
    public PreviewResponse preview(UUID id, VersionCommand command) {
        PurchaseDraft draft = mutableLocked(id, command.version());
        PreviewResponse preview = calculatePreview(draft);
        draft.setPreviewHash(preview.ready() ? preview.previewHash() : null);
        drafts.flush();
        return new PreviewResponse(draft.getVersion(), preview.ready(), preview.previewHash(), preview.canonicalDeltas(), preview.errors());
    }

    @Transactional
    public ConfirmResponse confirm(UUID id, ConfirmCommand command, String key, String actor, String correlationId) {
        requireKey(key);
        PurchaseDraft draft = drafts.findByIdForUpdate(id).orElseThrow(() -> notFound("No se encontro el borrador."));
        String requestHash = sha256((command.version() + "|" + command.previewHash()).getBytes(StandardCharsets.UTF_8));
        if (draft.getStatus() == PurchaseDraftStatus.CONFIRMED) {
            if (!key.equals(draft.getConfirmIdempotencyKey()) || !requestHash.equals(draft.getConfirmRequestHash())) {
                throw conflict("IDEMPOTENCY_CONFLICT", "La confirmacion ya existe con otro contenido o clave.");
            }
            return new ConfirmResponse(draft.getId(), draft.getConfirmedPurchaseId(), draft.getConfirmedReceiptId(), true,
                    calculatePreview(draft).canonicalDeltas());
        }
        requireVersion(draft, command.version());
        if (draft.getPreviewHash() == null || !draft.getPreviewHash().equals(command.previewHash())) {
            throw conflict("PREVIEW_STALE", "La vista previa no existe o quedo desactualizada.");
        }
        PreviewResponse current = calculatePreview(draft);
        if (!current.ready() || !current.previewHash().equals(command.previewHash())) {
            throw conflict("PREVIEW_STALE", "El borrador cambio desde la vista previa.");
        }
        matcher.revalidateTargets(draft);
        List<ImportedPurchaseLine> lines = draft.getLines().stream().map(line -> new ImportedPurchaseLine(
                line.getSourceProductName(), line.getMappingId(), line.getTargetType(), line.getProductId(), line.getVariantId(),
                line.getQuantity(), line.getConversion())).toList();
        String documentNumber = draft.getSourceSha256() == null ? "BORRADOR-" + draft.getId() : draft.getSourceSha256();
        var result = procurement.confirmImportedPurchase(draft.getSupplier().getId(), draft.getPurchaseDate(), documentNumber,
                lines, "draft:" + draft.getId() + ":" + key, actor, correlationId);
        draft.setConfirmedPurchaseId(result.purchaseId()); draft.setConfirmedReceiptId(result.receiptId());
        draft.setConfirmIdempotencyKey(key); draft.setConfirmRequestHash(requestHash);
        draft.setStatus(PurchaseDraftStatus.CONFIRMED); drafts.flush();
        return new ConfirmResponse(draft.getId(), result.purchaseId(), result.receiptId(), false, current.canonicalDeltas());
    }

    @Transactional(readOnly = true)
    public SourceFile sourceFile(UUID id) {
        PurchaseDraft draft = detailed(id);
        if (draft.getSourceType() != PurchaseDraftSourceType.XLSX || draft.getSourceStorageKey() == null) throw notFound("El borrador no tiene archivo original.");
        return new SourceFile(storage.load(draft.getSourceStorageKey()), draft.getOriginalFilename(), draft.getSourceContentType(), draft.getSourceSize());
    }

    private PurchaseDraftLine importedLine(UUID supplierId, PurchaseWorkbookParser.ParsedLine source) {
        PurchaseDraftLine line = new PurchaseDraftLine();
        line.setSourceRowNumber(source.rowNumber()); line.setSourceDateValue(source.sourceDate()); line.setSourceDate(source.date()); line.setSourceProductName(source.productName());
        line.setNormalizedProductName(source.normalizedProductName()); line.setSourceQuantityValue(source.sourceQuantity());
        line.setQuantity(source.quantity()); line.setUnit(source.unit()); line.setValidationErrors(joinErrors(source.errors())); line.setMatchStatus(source.status());
        if (source.status() != PurchaseDraftMatchStatus.INVALID) matcher.autoMatch(supplierId, line);
        return line;
    }

    private PurchaseDraftLine manualLine(LineCommand command, Integer row, LocalDate date) { PurchaseDraftLine line = new PurchaseDraftLine(); applyManualLine(line, command, row, date); return line; }
    private void applyManualLine(PurchaseDraftLine line, LineCommand command, Integer row, LocalDate date) {
        String name = command.productName() == null ? "" : command.productName().trim();
        List<String> errors = new ArrayList<>();
        if (name.isBlank()) errors.add("El producto es obligatorio.");
        validateQuantity(command.quantity(), command.unit(), errors);
        line.setSourceRowNumber(row); line.setSourceDateValue(null); line.setSourceDate(date); line.setSourceProductName(name); line.setNormalizedProductName(normalizer.normalize(name));
        line.setSourceQuantityValue(command.quantity() == null ? "" : command.quantity().toPlainString()); line.setQuantity(errors.isEmpty() ? command.quantity() : null);
        line.setUnit(command.unit()); line.setValidationErrors(joinErrors(errors)); line.setMatchStatus(errors.isEmpty() ? PurchaseDraftMatchStatus.UNRESOLVED : PurchaseDraftMatchStatus.INVALID);
        clearTarget(line);
    }

    private void validateQuantity(BigDecimal quantity, PurchaseDraftUnit unit, List<String> errors) {
        if (quantity == null || quantity.signum() <= 0 || unit == null) { errors.add("La cantidad y la unidad son obligatorias."); return; }
        try {
            if (unit == PurchaseDraftUnit.KG) {
                if (Math.max(0, quantity.stripTrailingZeros().scale()) > 3) throw new ArithmeticException();
                quantity.multiply(BigDecimal.valueOf(1000)).intValueExact();
            } else quantity.intValueExact();
        } catch (ArithmeticException exception) { errors.add("La cantidad no es valida para la unidad seleccionada."); }
    }

    private void clearTarget(PurchaseDraftLine line) { line.setMappingId(null); line.setTargetType(null); line.setProductId(null); line.setVariantId(null); line.setConversion(null); }

    private void refreshDuplicateErrors(PurchaseDraft draft) {
        draft.getLines().forEach(line -> {
            List<String> retained = new ArrayList<>(splitErrors(line.getValidationErrors()));
            retained.removeIf(DUPLICATE_ROW_ERROR::equals);
            line.setValidationErrors(joinErrors(retained));
            if (line.getMatchStatus() == PurchaseDraftMatchStatus.INVALID && retained.isEmpty()) line.setMatchStatus(PurchaseDraftMatchStatus.UNRESOLVED);
        });
        draft.getLines().stream().filter(line -> line.getQuantity() != null && line.getUnit() != null && !line.getNormalizedProductName().isBlank())
                .collect(java.util.stream.Collectors.groupingBy(line -> line.getNormalizedProductName() + "|"
                        + line.getSourceDate() + "|"
                        + line.getQuantity().stripTrailingZeros().toPlainString() + "|" + line.getUnit()))
                .values().stream().filter(group -> group.size() > 1).flatMap(List::stream).forEach(line -> {
                    List<String> errors = new ArrayList<>(splitErrors(line.getValidationErrors()));
                    errors.add(DUPLICATE_ROW_ERROR); line.setValidationErrors(joinErrors(errors)); line.setMatchStatus(PurchaseDraftMatchStatus.INVALID); clearTarget(line);
                });
    }

    private PreviewResponse calculatePreview(PurchaseDraft draft) {
        List<RowError> errors = new ArrayList<>();
        if (draft.getPurchaseDate() == null) errors.add(new RowError(null, "PURCHASE_DATE_REQUIRED", "La fecha de compra es obligatoria."));
        if (draft.getLines().isEmpty()) errors.add(new RowError(null, "LINES_REQUIRED", "El borrador debe tener al menos una linea."));
        List<CanonicalDelta> deltas = new ArrayList<>();
        for (PurchaseDraftLine line : draft.getLines()) {
            if (line.getValidationErrors() != null) splitErrors(line.getValidationErrors()).forEach(error -> errors.add(new RowError(line.getSourceRowNumber(), "LINE_INVALID", error)));
            if (line.getMatchStatus() != PurchaseDraftMatchStatus.MATCHED) errors.add(new RowError(line.getSourceRowNumber(), "UNRESOLVED_PRODUCT", "Debe vincular el producto antes de confirmar."));
            if (line.getMatchStatus() == PurchaseDraftMatchStatus.MATCHED && line.getQuantity() != null) {
                int delta = line.getQuantity().multiply(line.getConversion()).intValueExact();
                UUID targetId = line.getTargetType() == InventoryTargetType.BULK_GRAM ? line.getProductId() : line.getVariantId();
                deltas.add(new CanonicalDelta(line.getId(), line.getTargetType(), targetId, delta));
            }
        }
        String hash = errors.isEmpty() ? previewHash(draft, deltas) : null;
        return new PreviewResponse(draft.getVersion(), errors.isEmpty(), hash, List.copyOf(deltas), List.copyOf(errors));
    }

    private String previewHash(PurchaseDraft draft, List<CanonicalDelta> deltas) {
        StringBuilder canonical = new StringBuilder(draft.getSupplier().getId().toString()).append('|').append(draft.getPurchaseDate());
        deltas.stream().sorted(Comparator.comparing(value -> value.lineId().toString())).forEach(value -> canonical.append('|')
                .append(value.lineId()).append(':').append(value.targetType()).append(':').append(value.targetId()).append(':').append(value.delta()));
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private DraftResponse duplicateResult(PurchaseDraft draft) {
        if (draft.getStatus() == PurchaseDraftStatus.CONFIRMED) throw new PurchaseDraftException(HttpStatus.CONFLICT, "DUPLICATE_CONFIRMED_FILE", "El archivo ya fue confirmado en la compra " + draft.getConfirmedPurchaseId() + ".");
        return response(draft, true);
    }
    private DraftResponse flushed(PurchaseDraft draft) { drafts.flush(); return response(draft, false); }
    private PurchaseDraft detailed(UUID id) { return drafts.findDetailedById(id).filter(value -> value.getStatus() != PurchaseDraftStatus.DELETED).orElseThrow(() -> notFound("No se encontro el borrador.")); }
    private PurchaseDraft mutableLocked(UUID id, long version) { PurchaseDraft draft = drafts.findByIdForUpdate(id).orElseThrow(() -> notFound("No se encontro el borrador.")); if (draft.getStatus() != PurchaseDraftStatus.DRAFT) throw conflict("DRAFT_IMMUTABLE", "El borrador ya no puede modificarse."); requireVersion(draft, version); return draft; }
    private void requireVersion(PurchaseDraft draft, long version) { if (draft.getVersion() != version) throw conflict("VERSION_CONFLICT", "El borrador fue modificado por otra operacion."); }
    private Supplier activeSupplier(UUID id) { Supplier supplier = suppliers.findById(id).orElseThrow(() -> notFound("No se encontro el proveedor.")); if (!supplier.isActive()) throw conflict("INACTIVE_SUPPLIER", "El proveedor esta inactivo."); return supplier; }
    private Supplier activeLockedSupplier(UUID id) { Supplier supplier = suppliers.findByIdForUpdate(id).orElseThrow(() -> notFound("No se encontro el proveedor.")); if (!supplier.isActive()) throw conflict("INACTIVE_SUPPLIER", "El proveedor esta inactivo."); return supplier; }
    private PurchaseDraftLine line(PurchaseDraft draft, UUID id) { return draft.getLines().stream().filter(value -> value.getId().equals(id)).findFirst().orElseThrow(() -> notFound("No se encontro la linea.")); }

    private DraftResponse response(PurchaseDraft draft, boolean reused) {
        return new DraftResponse(draft.getId(), draft.getVersion(), draft.getStatus(), draft.getSupplier().getId(), draft.getSupplier().getName(),
                draft.getPurchaseDate(), draft.getSourceType(), draft.getOriginalFilename(), draft.getSourceSha256(), draft.getPreviewHash(),
                draft.getConfirmedPurchaseId(), reused, draft.getLines().stream().sorted(Comparator.comparing(line -> line.getSourceRowNumber() == null ? Integer.MAX_VALUE : line.getSourceRowNumber())).map(this::lineResponse).toList());
    }
    private LineResponse lineResponse(PurchaseDraftLine line) { return new LineResponse(line.getId(), line.getSourceRowNumber(), line.getSourceDateValue(), line.getSourceProductName(), line.getSourceQuantityValue(), line.getQuantity(), line.getUnit(), splitErrors(line.getValidationErrors()), line.getMatchStatus(), line.getTargetType(), line.getProductId(), line.getVariantId(), line.getConversion()); }
    private String safeFilename(String value) { if (value == null || value.isBlank()) return "compra.xlsx"; String safe = value.replace('\\', '/'); safe = safe.substring(safe.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\\"]", "_"); return safe.isBlank() ? "compra.xlsx" : safe; }
    private static String joinErrors(List<String> errors) { return errors == null || errors.isEmpty() ? null : String.join("\n", errors); }
    private static List<String> splitErrors(String errors) { return errors == null || errors.isBlank() ? List.of() : List.of(errors.split("\\n")); }
    private static String sha256(byte[] value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); } }
    private static void requireKey(String key) { if (key == null || key.isBlank()) throw invalid("IDEMPOTENCY_KEY_REQUIRED", "Debe enviar el encabezado Idempotency-Key."); }
    private static PurchaseDraftException invalid(String code, String message) { return new PurchaseDraftException(HttpStatus.BAD_REQUEST, code, message); }
    private static PurchaseDraftException conflict(String code, String message) { return new PurchaseDraftException(HttpStatus.CONFLICT, code, message); }
    private static PurchaseDraftException notFound(String message) { return new PurchaseDraftException(HttpStatus.NOT_FOUND, "NOT_FOUND", message); }

    public record ManualDraftCommand(UUID supplierId, LocalDate purchaseDate, List<LineCommand> lines) { }
    public record MetadataCommand(long version, LocalDate purchaseDate) { }
    public record VersionCommand(long version) { }
    public record LineCommand(long version, String productName, BigDecimal quantity, PurchaseDraftUnit unit) { }
    public record MatchCommand(long version, UUID targetId, boolean remember) { }
    public record ConfirmCommand(long version, String previewHash) { }
    public record CanonicalDelta(UUID lineId, InventoryTargetType targetType, UUID targetId, int delta) { }
    public record RowError(Integer rowNumber, String code, String message) { }
    public record PreviewResponse(long version, boolean ready, String previewHash, List<CanonicalDelta> canonicalDeltas, List<RowError> errors) { }
    public record ConfirmResponse(UUID draftId, UUID purchaseId, UUID receiptId, boolean replayed,
            List<CanonicalDelta> canonicalDeltas) { }
    public record LineResponse(UUID id, Integer rowNumber, String sourceDate, String productName, String sourceQuantity,
            BigDecimal quantity, PurchaseDraftUnit unit, List<String> errors, PurchaseDraftMatchStatus matchStatus,
            InventoryTargetType targetType, UUID productId, UUID variantId, BigDecimal conversion) { }
    public record DraftResponse(UUID id, long version, PurchaseDraftStatus status, UUID supplierId, String supplierName,
            LocalDate purchaseDate, PurchaseDraftSourceType sourceType, String originalFilename, String sourceSha256,
            String previewHash, UUID confirmedPurchaseId, boolean reused, List<LineResponse> lines) { }
    public record SourceFile(Resource resource, String filename, String contentType, Long size) { }
}
