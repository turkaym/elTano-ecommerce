package com.eltano.ecommerce.inventory.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.repository.ProductRepository;
import com.eltano.ecommerce.catalog.repository.ProductVariantRepository;
import com.eltano.ecommerce.common.api.ConflictException;
import com.eltano.ecommerce.procurement.domain.InventoryTargetType;

@Service
public class InventoryMutationService {
    private final ProductRepository products;
    private final ProductVariantRepository variants;

    public InventoryMutationService(ProductRepository products, ProductVariantRepository variants) {
        this.products = products;
        this.variants = variants;
    }

    @Transactional
    public List<InventoryMutation> apply(List<InventoryDelta> requested) {
        List<InventoryDelta> deltas = aggregate(requested);
        List<UUID> productIds = sortedIds(deltas, InventoryTargetType.BULK_GRAM);
        List<UUID> variantIds = sortedIds(deltas, InventoryTargetType.VARIANT_UNIT);
        Map<UUID, Product> lockedProducts = indexProducts(products.findAllByIdInForUpdate(productIds));
        Map<UUID, ProductVariant> lockedVariants = indexVariants(variants.findAllByIdInForUpdate(variantIds));
        List<InventoryMutation> changes = new ArrayList<>();
        for (InventoryDelta delta : deltas) {
            if (delta.targetType() == InventoryTargetType.BULK_GRAM) {
                Product product = required(lockedProducts, delta.targetId());
                int before = product.getStockBaseGrams() == null ? 0 : product.getStockBaseGrams();
                int after = exactAdd(before, delta.delta());
                if (after < product.getStockReservedBaseGrams()) throw new InventoryInvariantException("Bulk balance cannot be below reserved stock");
                changes.add(new InventoryMutation(delta.targetType(), delta.targetId(), delta.delta(), before, after));
            } else {
                ProductVariant variant = required(lockedVariants, delta.targetId());
                int before = variant.getStockAvailable();
                int after = exactAdd(before, delta.delta());
                if (after < 0) throw new InventoryInvariantException("Variant available balance cannot be negative");
                changes.add(new InventoryMutation(delta.targetType(), delta.targetId(), delta.delta(), before, after));
            }
        }
        changes.forEach(change -> {
            if (change.targetType() == InventoryTargetType.BULK_GRAM) lockedProducts.get(change.targetId()).setStockBaseGrams(change.afterBalance());
            else lockedVariants.get(change.targetId()).setStockAvailable(change.afterBalance());
        });
        return List.copyOf(changes);
    }

    @Transactional
    public void reserve(ProductVariant requested, int quantity) {
        ProductVariant variant = lockReservationTarget(requested);
        if (resolvePolicy(variant) == InventoryPolicy.BULK_WEIGHT) {
            Product product = variant.getProduct();
            int requiredGrams = requiredGrams(variant, quantity);
            if (product.getStockBaseGrams() == null) throw incompatibleVariant();
            if (product.getStockBaseGrams() - product.getStockReservedBaseGrams() < requiredGrams) throw new ConflictException("Insufficient stock");
            product.setStockReservedBaseGrams(Math.addExact(product.getStockReservedBaseGrams(), requiredGrams));
            return;
        }
        if (variant.getStockAvailable() < quantity) throw new ConflictException("Insufficient stock");
        variant.setStockAvailable(variant.getStockAvailable() - quantity);
        variant.setStockReserved(Math.addExact(variant.getStockReserved(), quantity));
    }

    @Transactional
    public void release(ProductVariant requested, int quantity) {
        ProductVariant variant = lockReservationTarget(requested);
        if (resolvePolicy(variant) == InventoryPolicy.BULK_WEIGHT) {
            Product product = variant.getProduct();
            int grams = requiredGrams(variant, quantity);
            product.setStockReservedBaseGrams(Math.max(0, product.getStockReservedBaseGrams() - grams));
            return;
        }
        variant.setStockReserved(Math.max(0, variant.getStockReserved() - quantity));
        variant.setStockAvailable(Math.addExact(variant.getStockAvailable(), quantity));
    }

    @Transactional
    public void finalizeReservation(ProductVariant requested, int quantity) {
        ProductVariant variant = lockReservationTarget(requested);
        if (resolvePolicy(variant) == InventoryPolicy.BULK_WEIGHT) {
            Product product = variant.getProduct();
            int grams = requiredGrams(variant, quantity);
            int base = product.getStockBaseGrams() == null ? 0 : product.getStockBaseGrams();
            product.setStockBaseGrams(Math.max(0, base - grams));
            product.setStockReservedBaseGrams(Math.max(0, product.getStockReservedBaseGrams() - grams));
            return;
        }
        variant.setStockReserved(Math.max(0, variant.getStockReserved() - quantity));
    }

    private ProductVariant lockReservationTarget(ProductVariant requested) {
        if (requested == null || requested.getId() == null || requested.getProduct() == null) throw new IllegalArgumentException("Variant not found");
        if (resolvePolicy(requested) == InventoryPolicy.BULK_WEIGHT) {
            UUID productId = requested.getProduct().getId();
            Product product = required(indexProducts(products.findAllByIdInForUpdate(List.of(productId))), productId);
            ProductVariant variant = required(indexVariants(variants.findAllByIdInForUpdate(List.of(requested.getId()))), requested.getId());
            variant.setProduct(product);
            return variant;
        }
        return required(indexVariants(variants.findAllByIdInForUpdate(List.of(requested.getId()))), requested.getId());
    }

    private static int requiredGrams(ProductVariant variant, int quantity) {
        Integer weight = variant.getWeightGrams();
        if (weight == null || weight <= 0) throw incompatibleVariant();
        try { return Math.multiplyExact(weight, quantity); }
        catch (ArithmeticException exception) { throw incompatibleVariant(); }
    }

    private static InventoryPolicy resolvePolicy(ProductVariant variant) {
        InventoryPolicy policy = variant.getProduct().getInventoryPolicy();
        return policy == null ? InventoryPolicy.PER_VARIANT : policy;
    }

    private static IllegalArgumentException incompatibleVariant() {
        return new IllegalArgumentException("Variant incompatible with product policy");
    }

    private static List<UUID> sortedIds(List<InventoryDelta> deltas, InventoryTargetType type) {
        return deltas.stream().filter(delta -> delta.targetType() == type).map(InventoryDelta::targetId).distinct()
                .sorted(java.util.Comparator.comparing(UUID::toString)).toList();
    }
    private static List<InventoryDelta> aggregate(List<InventoryDelta> requested) {
        record Target(InventoryTargetType type, UUID id) { }
        Map<Target, Integer> totals = new LinkedHashMap<>();
        requested.stream().filter(delta -> delta.delta() != 0).forEach(delta -> totals.merge(
                new Target(delta.targetType(), delta.targetId()), delta.delta(), InventoryMutationService::exactAdd));
        return totals.entrySet().stream().filter(entry -> entry.getValue() != 0)
                .map(entry -> new InventoryDelta(entry.getKey().type(), entry.getKey().id(), entry.getValue())).toList();
    }
    private static Map<UUID, Product> indexProducts(List<Product> values) { Map<UUID, Product> map = new HashMap<>(); values.forEach(value -> map.put(value.getId(), value)); return map; }
    private static Map<UUID, ProductVariant> indexVariants(List<ProductVariant> values) { Map<UUID, ProductVariant> map = new HashMap<>(); values.forEach(value -> map.put(value.getId(), value)); return map; }
    private static <T> T required(Map<UUID, T> values, UUID id) { T value = values.get(id); if (value == null) throw new IllegalArgumentException("Inventory target not found: " + id); return value; }
    private static int exactAdd(int value, int delta) { try { return Math.addExact(value, delta); } catch (ArithmeticException exception) { throw new InventoryInvariantException("Inventory balance overflow"); } }
}
