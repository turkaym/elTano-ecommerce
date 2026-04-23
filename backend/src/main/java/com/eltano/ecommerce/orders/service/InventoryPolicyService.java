package com.eltano.ecommerce.orders.service;

import org.springframework.stereotype.Service;

import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.common.api.ConflictException;

@Service
public class InventoryPolicyService {

    public void reserve(ProductVariant variant, int quantity) {
        if (resolvePolicy(variant) == InventoryPolicy.BULK_WEIGHT) {
            reserveBulkWeight(variant, quantity);
            return;
        }

        if (variant.getStockAvailable() < quantity) {
            throw new ConflictException("Insufficient stock");
        }
        variant.setStockAvailable(variant.getStockAvailable() - quantity);
        variant.setStockReserved(variant.getStockReserved() + quantity);
    }

    public void release(ProductVariant variant, int quantity) {
        if (resolvePolicy(variant) == InventoryPolicy.BULK_WEIGHT) {
            Product product = variant.getProduct();
            int releasedGrams = requiredGrams(variant, quantity);
            int current = product.getStockBaseGrams() == null ? 0 : product.getStockBaseGrams();
            product.setStockBaseGrams(current + releasedGrams);
            return;
        }

        variant.setStockReserved(Math.max(0, variant.getStockReserved() - quantity));
        variant.setStockAvailable(variant.getStockAvailable() + quantity);
    }

    private void reserveBulkWeight(ProductVariant variant, int quantity) {
        Product product = variant.getProduct();
        Integer available = product.getStockBaseGrams();
        if (available == null) {
            throw new IllegalArgumentException("Variant incompatible with product policy");
        }

        int requiredGrams = requiredGrams(variant, quantity);
        if (available < requiredGrams) {
            throw new ConflictException("Insufficient stock");
        }

        product.setStockBaseGrams(available - requiredGrams);
    }

    private int requiredGrams(ProductVariant variant, int quantity) {
        Integer weightGrams = variant.getWeightGrams();
        if (weightGrams == null || weightGrams <= 0) {
            throw new IllegalArgumentException("Variant incompatible with product policy");
        }
        long required = (long) weightGrams * quantity;
        if (required > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Variant incompatible with product policy");
        }
        return (int) required;
    }

    private InventoryPolicy resolvePolicy(ProductVariant variant) {
        if (variant.getProduct() == null) {
            throw new IllegalArgumentException("Variant not found");
        }
        return variant.getProduct().getInventoryPolicy();
    }
}
