package com.eltano.ecommerce.procurement.draft.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.catalog.repository.ProductRepository;
import com.eltano.ecommerce.catalog.repository.ProductVariantRepository;
import com.eltano.ecommerce.procurement.domain.InventoryTargetType;
import com.eltano.ecommerce.procurement.domain.Supplier;
import com.eltano.ecommerce.procurement.domain.SupplierItemMapping;
import com.eltano.ecommerce.procurement.draft.domain.PurchaseDraft;
import com.eltano.ecommerce.procurement.draft.domain.PurchaseDraftLine;
import com.eltano.ecommerce.procurement.draft.domain.PurchaseDraftMatchStatus;
import com.eltano.ecommerce.procurement.draft.domain.PurchaseDraftUnit;
import com.eltano.ecommerce.procurement.repository.SupplierItemMappingRepository;

@Service
public class PurchaseDraftProductMatcher {
    private final SupplierItemMappingRepository mappings;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final SupplierProductNameNormalizer normalizer;

    public PurchaseDraftProductMatcher(SupplierItemMappingRepository mappings, ProductRepository products,
            ProductVariantRepository variants, SupplierProductNameNormalizer normalizer) {
        this.mappings = mappings; this.products = products; this.variants = variants; this.normalizer = normalizer;
    }

    public List<CatalogCandidate> candidates(String query, PurchaseDraftUnit unit, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 50));
        String normalizedQuery = normalizer.normalize(query);
        if (unit == PurchaseDraftUnit.KG) {
            return products.findAllWithRelations().stream().filter(product -> product.isActive() && product.getDeletedAt() == null
                    && product.getInventoryPolicy() == InventoryPolicy.BULK_WEIGHT && normalizer.normalize(product.getName()).contains(normalizedQuery))
                    .sorted(Comparator.comparing(Product::getName)).limit(limit)
                    .map(product -> new CatalogCandidate(product.getId(), product.getName() + " (a granel)", InventoryTargetType.BULK_GRAM)).toList();
        }
        return products.findAllWithRelations().stream().filter(product -> product.isActive() && product.getDeletedAt() == null
                && product.getInventoryPolicy() == InventoryPolicy.PER_VARIANT)
                .flatMap(product -> product.getVariants().stream().filter(ProductVariant::isActive)
                        .map(variant -> new CandidateValue(variant, product.getName() + " - " + variant.getSku())))
                .filter(value -> normalizer.normalize(value.label()).contains(normalizedQuery))
                .sorted(Comparator.comparing(CandidateValue::label)).limit(limit)
                .map(value -> new CatalogCandidate(value.variant().getId(), value.label(), InventoryTargetType.VARIANT_UNIT)).toList();
    }

    public void autoMatch(UUID supplierId, PurchaseDraftLine line) {
        mappings.findBySupplierIdAndNormalizedNameAndActiveTrue(supplierId, line.getNormalizedProductName()).ifPresent(mapping -> {
            try { applyTarget(line, mapping, mappingTarget(mapping, line.getUnit())); }
            catch (PurchaseDraftException ignored) { }
        });
    }

    public void match(Supplier supplier, PurchaseDraftLine line, UUID targetId, boolean remember) {
        Target target = target(targetId, line.getUnit());
        SupplierItemMapping mapping = remember ? remember(supplier, line, target) : null;
        applyTarget(line, mapping, target);
    }

    public String targetLabel(PurchaseDraftLine line) {
        if (line.getTargetLabel() != null) return line.getTargetLabel();
        if (line.getTargetType() == InventoryTargetType.BULK_GRAM && line.getProductId() != null) {
            return products.findById(line.getProductId()).map(product -> product.getName() + " (a granel)").orElse(null);
        }
        if (line.getTargetType() == InventoryTargetType.VARIANT_UNIT && line.getVariantId() != null) {
            return variants.findById(line.getVariantId()).map(variant -> variant.getProduct().getName() + " - " + variant.getSku()).orElse(null);
        }
        return null;
    }

    public void revalidateTargets(PurchaseDraft draft) {
        List<UUID> productIds = draft.getLines().stream().filter(line -> line.getTargetType() == InventoryTargetType.BULK_GRAM)
                .map(PurchaseDraftLine::getProductId).distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        List<UUID> variantIds = draft.getLines().stream().filter(line -> line.getTargetType() == InventoryTargetType.VARIANT_UNIT)
                .map(PurchaseDraftLine::getVariantId).distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        if (!productIds.isEmpty()) products.findAllByIdInForUpdate(productIds);
        if (!variantIds.isEmpty()) variants.findAllByIdInForUpdate(variantIds);
        draft.getLines().forEach(line -> {
            Target current = target(line.getTargetType() == InventoryTargetType.BULK_GRAM ? line.getProductId() : line.getVariantId(), line.getUnit());
            if (current.type() != line.getTargetType() || current.conversion().compareTo(line.getConversion()) != 0) {
                throw conflict("INCOMPATIBLE_TARGET", "Una vinculacion dejo de ser compatible.");
            }
            if (line.getTargetLabel() == null) line.setTargetLabel(current.label());
        });
    }

    private Target target(UUID id, PurchaseDraftUnit unit) {
        if (id == null) throw new PurchaseDraftException(HttpStatus.BAD_REQUEST, "TARGET_REQUIRED", "Debe seleccionar un producto compatible.");
        if (unit == PurchaseDraftUnit.KG) {
            Product product = products.findById(id).orElseThrow(() -> notFound("No se encontro el producto."));
            if (!product.isActive() || product.getDeletedAt() != null || product.getInventoryPolicy() != InventoryPolicy.BULK_WEIGHT) throw conflict("INCOMPATIBLE_TARGET", "El producto debe estar activo y usar inventario BULK_WEIGHT.");
            return new Target(InventoryTargetType.BULK_GRAM, product.getId(), null, BigDecimal.valueOf(1000), product.getName() + " (a granel)");
        }
        ProductVariant variant = variants.findById(id).orElseThrow(() -> notFound("No se encontro la variante."));
        Product product = variant.getProduct();
        if (!variant.isActive() || !product.isActive() || product.getDeletedAt() != null || product.getInventoryPolicy() != InventoryPolicy.PER_VARIANT) throw conflict("INCOMPATIBLE_TARGET", "La variante debe estar activa y pertenecer a un producto PER_VARIANT.");
        return new Target(InventoryTargetType.VARIANT_UNIT, null, variant.getId(), BigDecimal.ONE, product.getName() + " - " + variant.getSku());
    }

    private Target mappingTarget(SupplierItemMapping mapping, PurchaseDraftUnit unit) {
        if (!mapping.isActive()) throw conflict("INCOMPATIBLE_TARGET", "La vinculacion guardada esta inactiva.");
        UUID id = unit == PurchaseDraftUnit.KG ? mapping.getProductId() : mapping.getVariantId();
        Target target = target(id, unit);
        if (target.type() != mapping.getTargetType()) throw conflict("INCOMPATIBLE_TARGET", "La vinculacion guardada no coincide con la unidad.");
        return target;
    }

    private SupplierItemMapping remember(Supplier supplier, PurchaseDraftLine line, Target target) {
        var existing = mappings.findBySupplierIdAndNormalizedNameForUpdate(supplier.getId(), line.getNormalizedProductName());
        if (existing.isPresent()) {
            SupplierItemMapping mapping = existing.get();
            if (mapping.isActive()) {
                if (!mappingTarget(mapping, line.getUnit()).equals(target)) throw conflict("MAPPING_CONFLICT", "El proveedor ya tiene otra vinculacion activa para este nombre. Use la correccion explicita para cambiarla.");
                return mapping;
            }
            mapping.setTargetType(target.type()); mapping.setProductId(target.productId()); mapping.setVariantId(target.variantId());
            mapping.setDefaultConversion(target.conversion()); mapping.setActive(true);
            return mapping;
        }
        SupplierItemMapping mapping = new SupplierItemMapping();
        mapping.setSupplier(supplier); mapping.setSupplierItemName(line.getSourceProductName()); mapping.setNormalizedName(line.getNormalizedProductName());
        mapping.setDescription(line.getSourceProductName()); mapping.setTargetType(target.type()); mapping.setProductId(target.productId());
        mapping.setVariantId(target.variantId()); mapping.setDefaultConversion(target.conversion()); mapping.setActive(true);
        try { return mappings.saveAndFlush(mapping); }
        catch (DataIntegrityViolationException exception) { throw conflict("MAPPING_CONFLICT", "El proveedor ya tiene una vinculacion para este nombre."); }
    }

    private void applyTarget(PurchaseDraftLine line, SupplierItemMapping mapping, Target target) {
        line.setMappingId(mapping == null ? null : mapping.getId()); line.setTargetType(target.type()); line.setProductId(target.productId());
        line.setVariantId(target.variantId()); line.setTargetLabel(target.label()); line.setConversion(target.conversion()); line.setMatchStatus(PurchaseDraftMatchStatus.MATCHED);
    }

    private static PurchaseDraftException conflict(String code, String message) { return new PurchaseDraftException(HttpStatus.CONFLICT, code, message); }
    private static PurchaseDraftException notFound(String message) { return new PurchaseDraftException(HttpStatus.NOT_FOUND, "NOT_FOUND", message); }
    private record Target(InventoryTargetType type, UUID productId, UUID variantId, BigDecimal conversion, String label) { }
    private record CandidateValue(ProductVariant variant, String label) { }
    public record CatalogCandidate(UUID value, String label, InventoryTargetType targetType) { }
}
