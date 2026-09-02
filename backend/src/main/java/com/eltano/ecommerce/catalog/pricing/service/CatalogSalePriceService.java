package com.eltano.ecommerce.catalog.pricing.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductType;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.catalog.domain.UnitType;
import com.eltano.ecommerce.catalog.pricing.domain.CatalogSalePricePreview;
import com.eltano.ecommerce.catalog.pricing.repository.CatalogSalePricePreviewRepository;
import com.eltano.ecommerce.catalog.repository.ProductRepository;
import com.eltano.ecommerce.catalog.repository.ProductVariantRepository;
import com.eltano.ecommerce.common.api.ConflictException;
import com.eltano.ecommerce.common.api.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class CatalogSalePriceService {
    private static final List<String> HEADERS = List.of(
            "tipo_clave", "clave", "producto", "presentacion", "precio_actual", "precio_nuevo");
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final CatalogSalePricePreviewRepository previews;
    private final CatalogSalePriceWorkbookParser parser;
    private final ObjectMapper objectMapper;

    public CatalogSalePriceService(ProductRepository products, ProductVariantRepository variants,
            CatalogSalePricePreviewRepository previews, CatalogSalePriceWorkbookParser parser, ObjectMapper objectMapper) {
        this.products = products;
        this.variants = variants;
        this.previews = previews;
        this.parser = parser;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public byte[] template() {
        List<TemplateRow> rows = new ArrayList<>();
        for (Product product : products.findAllWithRelations().stream()
                .filter(this::eligibleProduct).sorted(Comparator.comparing(Product::getName)).toList()) {
            List<ProductVariant> activeVariants = activeVariants(product);
            if (product.getInventoryPolicy() == InventoryPolicy.BULK_WEIGHT) {
                if (activeVariants.isEmpty()) continue;
                BigDecimal pricePerKg = consistentPricePerKg(activeVariants);
                if (pricePerKg == null) {
                    throw new ConflictException("No se puede exportar " + product.getName()
                            + " porque sus precios granel no representan un unico precio por kilogramo");
                }
                rows.add(new TemplateRow("PRODUCTO_GRANEL", product.getId().toString(), product.getName(),
                        "Precio por kg", pricePerKg));
            } else {
                for (ProductVariant variant : activeVariants) {
                    rows.add(new TemplateRow("SKU", variant.getSku(), product.getName(),
                            Objects.toString(variant.getUnitLabel(), ""), money(variant.getPrice())));
                }
            }
        }
        if (rows.size() > CatalogSalePriceWorkbookParser.MAX_ROWS) {
            throw new ConflictException("No se puede exportar la plantilla porque supera el maximo de 2000 filas de datos");
        }
        return workbook(rows);
    }

    @Transactional
    public PreviewResponse preview(MultipartFile file, String actor) {
        CatalogSalePriceWorkbookParser.ParsedWorkbook workbook = parser.parse(file);
        byte[] content;
        try { content = file.getBytes(); }
        catch (Exception exception) { throw new IllegalArgumentException("No se pudo leer el archivo XLSX."); }

        List<Product> catalog = products.findAllWithRelations();
        Map<String, ProductVariant> bySku = new HashMap<>();
        Map<UUID, Product> byId = new HashMap<>();
        for (Product product : catalog) {
            byId.put(product.getId(), product);
            for (ProductVariant variant : product.getVariants()) {
                bySku.put(variant.getSku().toLowerCase(Locale.ROOT), variant);
            }
        }

        List<PreviewRow> responseRows = new ArrayList<>();
        List<TargetSnapshot> targets = new ArrayList<>();
        for (CatalogSalePriceWorkbookParser.ParsedRow row : workbook.rows()) {
            List<String> errors = new ArrayList<>(row.errors());
            Product resolvedProduct = null;
            ProductVariant resolvedVariant = null;
            if (row.keyType().equals("SKU")) {
                resolvedVariant = previewVariant(row, bySku, errors, targets);
            } else if (row.keyType().equals("PRODUCTO_GRANEL")) {
                resolvedProduct = previewBulk(row, byId, errors, targets);
            }
            if (resolvedVariant != null) resolvedProduct = resolvedVariant.getProduct();
            String productName = resolvedProduct == null ? row.productName() : resolvedProduct.getName();
            String presentation = resolvedVariant == null
                    ? (resolvedProduct == null ? row.presentation() : "Precio por kg")
                    : Objects.toString(resolvedVariant.getUnitLabel(), "");
            responseRows.add(new PreviewRow(row.rowNumber(), row.keyType(), row.key(), productName,
                    presentation, row.oldPrice(), row.newPrice(), List.copyOf(errors)));
        }

        boolean valid = responseRows.stream().allMatch(row -> row.errors().isEmpty());
        if (!valid) return new PreviewResponse(null, null, false, responseRows, false);

        Snapshot snapshot = new Snapshot(List.copyOf(targets), List.copyOf(responseRows));
        String json = writeJson(snapshot);
        String previewHash = sha256(json.getBytes(StandardCharsets.UTF_8));
        CatalogSalePricePreview saved = new CatalogSalePricePreview();
        saved.setId(UUID.randomUUID());
        saved.setStatus("READY");
        saved.setWorkbookSha256(sha256(content));
        saved.setPreviewHash(previewHash);
        saved.setSnapshotJson(json);
        saved.setCreatedBy(actor == null || actor.isBlank() ? "unknown" : actor);
        previews.save(saved);
        return new PreviewResponse(saved.getId(), previewHash, true, responseRows, false);
    }

    @Transactional
    public ConfirmResponse confirm(UUID previewId, ConfirmCommand command, String idempotencyKey) {
        requireKey(idempotencyKey);
        if (command == null || command.previewHash() == null || command.previewHash().isBlank()) {
            throw new IllegalArgumentException("previewHash es obligatorio");
        }
        String requestHash = sha256((previewId + "|" + command.previewHash()).getBytes(StandardCharsets.UTF_8));
        CatalogSalePricePreview preview = previews.findByIdForUpdate(previewId)
                .orElseThrow(() -> new ResourceNotFoundException("Vista previa de precios no encontrada"));
        if ("CONFIRMED".equals(preview.getStatus())) {
            if (idempotencyKey.equals(preview.getConfirmIdempotencyKey())
                    && requestHash.equals(preview.getConfirmRequestHash())) {
                return new ConfirmResponse(previewId, true, preview.getConfirmedAt());
            }
            throw new ConflictException("La vista previa ya fue confirmada con otra solicitud");
        }
        if (!preview.getPreviewHash().equals(command.previewHash())) {
            throw new ConflictException("La vista previa no coincide con la confirmacion solicitada");
        }
        previews.findByConfirmIdempotencyKey(idempotencyKey).filter(other -> !other.getId().equals(previewId))
                .ifPresent(other -> { throw new ConflictException("Idempotency-Key ya fue utilizada"); });

        Snapshot snapshot = readSnapshot(preview.getSnapshotJson());
        List<UUID> productIds = snapshot.targets().stream().map(TargetSnapshot::productId).distinct().sorted().toList();
        List<UUID> snapshotVariantIds = snapshot.targets().stream().map(TargetSnapshot::variantId).distinct().sorted().toList();
        Map<UUID, Product> lockedProducts = indexProducts(products.findAllByIdInForUpdate(productIds));
        List<UUID> variantIds = new ArrayList<>(snapshotVariantIds);
        snapshot.targets().stream().filter(target -> target.inventoryPolicy() == InventoryPolicy.BULK_WEIGHT)
                .map(TargetSnapshot::productId).distinct()
                .flatMap(productId -> variants.findIdsByProductId(productId).stream())
                .filter(id -> !variantIds.contains(id)).forEach(variantIds::add);
        variantIds.sort(Comparator.naturalOrder());
        Map<UUID, ProductVariant> lockedVariants = indexVariants(variants.findAllByIdInForUpdate(variantIds));
        if (lockedProducts.size() != productIds.size() || lockedVariants.size() != variantIds.size()) {
            throw new ConflictException("El catalogo cambio desde la vista previa; volve a cargar el archivo");
        }
        for (UUID bulkProductId : snapshot.targets().stream()
                .filter(target -> target.inventoryPolicy() == InventoryPolicy.BULK_WEIGHT)
                .map(TargetSnapshot::productId).distinct().toList()) {
            List<UUID> expectedActive = snapshot.targets().stream().filter(target -> target.productId().equals(bulkProductId))
                    .map(TargetSnapshot::variantId).sorted().toList();
            List<UUID> currentActive = lockedVariants.values().stream()
                    .filter(variant -> variant.getProduct().getId().equals(bulkProductId) && variant.isActive())
                    .map(ProductVariant::getId).sorted().toList();
            if (!expectedActive.equals(currentActive)) {
                throw new ConflictException("El catalogo cambio desde la vista previa; volve a cargar el archivo");
            }
        }
        for (TargetSnapshot target : snapshot.targets()) {
            Product product = lockedProducts.get(target.productId());
            ProductVariant variant = lockedVariants.get(target.variantId());
            if (!matches(target, product, variant)) {
                throw new ConflictException("El catalogo cambio desde la vista previa; volve a cargar el archivo");
            }
        }
        for (TargetSnapshot target : snapshot.targets()) lockedVariants.get(target.variantId()).setPrice(target.newPrice());
        variants.saveAll(lockedVariants.values());
        preview.setStatus("CONFIRMED");
        preview.setConfirmedAt(Instant.now());
        preview.setConfirmIdempotencyKey(idempotencyKey);
        preview.setConfirmRequestHash(requestHash);
        previews.save(preview);
        return new ConfirmResponse(previewId, false, preview.getConfirmedAt());
    }

    private ProductVariant previewVariant(CatalogSalePriceWorkbookParser.ParsedRow row, Map<String, ProductVariant> bySku,
            List<String> errors, List<TargetSnapshot> targets) {
        ProductVariant variant = bySku.get(row.key().toLowerCase(Locale.ROOT));
        if (variant == null) { errors.add("No existe una variante con ese SKU."); return null; }
        Product product = variant.getProduct();
        if (!eligibleProduct(product) || !variant.isActive()) errors.add("El producto o la variante esta inactivo o eliminado.");
        if (product.getInventoryPolicy() != InventoryPolicy.PER_VARIANT) errors.add("Los productos granel deben actualizarse por precio por kilogramo.");
        BigDecimal current = money(variant.getPrice());
        if (row.oldPrice() != null && current.compareTo(row.oldPrice()) != 0) errors.add("El precio actual no coincide con el catalogo.");
        if (errors.isEmpty()) targets.add(snapshot(product, variant, row.newPrice()));
        return variant;
    }

    private Product previewBulk(CatalogSalePriceWorkbookParser.ParsedRow row, Map<UUID, Product> byId,
            List<String> errors, List<TargetSnapshot> targets) {
        UUID id;
        try { id = UUID.fromString(row.key()); }
        catch (IllegalArgumentException exception) { errors.add("La clave de producto granel no es valida."); return null; }
        Product product = byId.get(id);
        if (product == null) { errors.add("No existe el producto granel indicado."); return null; }
        if (!eligibleProduct(product)) errors.add("El producto esta inactivo o eliminado.");
        if (product.getProductType() != ProductType.GRANEL || product.getInventoryPolicy() != InventoryPolicy.BULK_WEIGHT) {
            errors.add("El producto no usa inventario granel.");
        }
        List<ProductVariant> active = activeVariants(product);
        if (active.isEmpty()) errors.add("El producto no tiene presentaciones activas.");
        if (active.stream().anyMatch(variant -> variant.getUnitType() != UnitType.WEIGHT
                || variant.getWeightGrams() == null || variant.getWeightGrams() <= 0)) {
            errors.add("El producto contiene una presentacion con unidad no compatible.");
        }
        BigDecimal currentKg = errors.isEmpty() ? consistentPricePerKg(active) : null;
        if (errors.isEmpty() && currentKg == null) errors.add("Las presentaciones no tienen un precio por kilogramo consistente.");
        if (currentKg != null && row.oldPrice() != null && currentKg.compareTo(row.oldPrice()) != 0) {
            errors.add("El precio actual por kilogramo no coincide con el catalogo.");
        }
        if (errors.isEmpty()) {
            List<BigDecimal> derived = active.stream()
                    .map(variant -> derive(row.newPrice(), variant.getWeightGrams())).toList();
            if (derived.stream().anyMatch(price -> price.signum() <= 0)) {
                errors.add("El precio por kilogramo produce una presentacion con precio no positivo.");
            } else for (int index = 0; index < active.size(); index++) {
                targets.add(snapshot(product, active.get(index), derived.get(index)));
            }
        }
        return product;
    }

    private TargetSnapshot snapshot(Product product, ProductVariant variant, BigDecimal newPrice) {
        return new TargetSnapshot(product.getId(), product.getProductType(), product.getInventoryPolicy(), product.isActive(),
                product.getDeletedAt(), variant.getId(), variant.getSku(), variant.getUnitType(), variant.getWeightGrams(),
                variant.isActive(), money(variant.getPrice()), money(newPrice));
    }

    private boolean matches(TargetSnapshot expected, Product product, ProductVariant variant) {
        return product != null && variant != null && variant.getProduct().getId().equals(product.getId())
                && product.isActive() == expected.productActive() && Objects.equals(product.getDeletedAt(), expected.productDeletedAt())
                && product.getProductType() == expected.productType() && product.getInventoryPolicy() == expected.inventoryPolicy()
                && variant.isActive() == expected.variantActive() && variant.getSku().equals(expected.sku())
                && variant.getUnitType() == expected.unitType() && Objects.equals(variant.getWeightGrams(), expected.weightGrams())
                && money(variant.getPrice()).compareTo(expected.oldPrice()) == 0;
    }

    private boolean eligibleProduct(Product product) { return product.isActive() && product.getDeletedAt() == null; }
    private List<ProductVariant> activeVariants(Product product) {
        return product.getVariants().stream().filter(ProductVariant::isActive)
                .sorted(Comparator.comparing(ProductVariant::getSku)).toList();
    }
    private BigDecimal consistentPricePerKg(List<ProductVariant> active) {
        if (active.stream().anyMatch(v -> v.getUnitType() != UnitType.WEIGHT || v.getWeightGrams() == null || v.getWeightGrams() <= 0)) return null;
        BigDecimal candidate = active.stream().map(v -> money(v.getPrice()).subtract(new BigDecimal("0.005"))
                .multiply(BigDecimal.valueOf(1000)).divide(BigDecimal.valueOf(v.getWeightGrams()), 2, RoundingMode.CEILING))
                .max(Comparator.naturalOrder()).orElseThrow();
        boolean matches = active.stream().allMatch(v -> derive(candidate, v.getWeightGrams()).compareTo(money(v.getPrice())) == 0);
        boolean ambiguous = active.stream().allMatch(v -> derive(candidate.add(new BigDecimal("0.01")), v.getWeightGrams())
                .compareTo(money(v.getPrice())) == 0);
        return matches && !ambiguous ? candidate : null;
    }
    private BigDecimal derive(BigDecimal pricePerKg, int grams) {
        return pricePerKg.multiply(BigDecimal.valueOf(grams)).divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
    }
    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

    private byte[] workbook(List<TemplateRow> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Precios de venta");
            Row header = sheet.createRow(0);
            for (int index = 0; index < HEADERS.size(); index++) header.createCell(index).setCellValue(HEADERS.get(index));
            DataFormat format = workbook.createDataFormat();
            CellStyle priceStyle = workbook.createCellStyle();
            priceStyle.setDataFormat(format.getFormat("0.00"));
            int rowIndex = 1;
            for (TemplateRow source : rows) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(source.keyType());
                row.createCell(1).setCellValue(source.key());
                row.createCell(2).setCellValue(source.productName());
                row.createCell(3).setCellValue(source.presentation());
                row.createCell(4).setCellValue(source.price().doubleValue());
                row.createCell(5).setCellValue(source.price().doubleValue());
                row.getCell(4).setCellStyle(priceStyle);
                row.getCell(5).setCellStyle(priceStyle);
            }
            for (int column = 0; column < HEADERS.size(); column++) sheet.autoSizeColumn(column);
            sheet.createFreezePane(0, 1);
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo generar la plantilla de precios", exception);
        }
    }

    private Map<UUID, Product> indexProducts(List<Product> values) {
        Map<UUID, Product> result = new LinkedHashMap<>();
        values.forEach(value -> result.put(value.getId(), value));
        return result;
    }
    private Map<UUID, ProductVariant> indexVariants(List<ProductVariant> values) {
        Map<UUID, ProductVariant> result = new LinkedHashMap<>();
        values.forEach(value -> result.put(value.getId(), value));
        return result;
    }
    private String writeJson(Snapshot snapshot) {
        try { return objectMapper.writeValueAsString(snapshot); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("No se pudo guardar la vista previa", exception); }
    }
    private Snapshot readSnapshot(String json) {
        try { return objectMapper.readValue(json, Snapshot.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("La vista previa guardada no es valida", exception); }
    }
    private String sha256(byte[] value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 no disponible", exception); }
    }
    private void requireKey(String value) {
        if (value == null || value.isBlank() || value.length() > 180) throw new IllegalArgumentException("Idempotency-Key es obligatorio");
    }

    private record TemplateRow(String keyType, String key, String productName, String presentation, BigDecimal price) { }
    public record PreviewRow(int rowNumber, String keyType, String key, String productName, String presentation,
            BigDecimal oldPrice, BigDecimal newPrice, List<String> errors) { }
    public record PreviewResponse(UUID previewId, String previewHash, boolean valid, List<PreviewRow> rows, boolean reused) { }
    public record ConfirmCommand(String previewHash) { }
    public record ConfirmResponse(UUID previewId, boolean reused, Instant confirmedAt) { }
    public record Snapshot(List<TargetSnapshot> targets, List<PreviewRow> rows) { }
    public record TargetSnapshot(UUID productId, ProductType productType, InventoryPolicy inventoryPolicy,
            boolean productActive, Instant productDeletedAt, UUID variantId, String sku, UnitType unitType,
            Integer weightGrams, boolean variantActive, BigDecimal oldPrice, BigDecimal newPrice) { }
}
