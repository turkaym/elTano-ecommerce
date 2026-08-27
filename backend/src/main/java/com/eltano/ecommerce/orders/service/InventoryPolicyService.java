package com.eltano.ecommerce.orders.service;

import org.springframework.stereotype.Service;

import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.common.api.ConflictException;
import com.eltano.ecommerce.inventory.service.InventoryMutationService;

@Service
public class InventoryPolicyService {
    private final InventoryMutationService inventory;

    public InventoryPolicyService(InventoryMutationService inventory) {
        this.inventory = inventory;
    }

    InventoryPolicyService() {
        this.inventory = null;
    }

    public void reserve(ProductVariant variant, int quantity) {
        if (inventory != null) {
            inventory.reserve(variant, quantity);
            return;
        }
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
        if (inventory != null) {
            inventory.release(variant, quantity);
            return;
        }
        if (resolvePolicy(variant) == InventoryPolicy.BULK_WEIGHT) {
            Product product = variant.getProduct();
            int releasedGrams = requiredGrams(variant, quantity);
            product.setStockReservedBaseGrams(Math.max(0, product.getStockReservedBaseGrams() - releasedGrams));
            return;
        }

        variant.setStockReserved(Math.max(0, variant.getStockReserved() - quantity));
        variant.setStockAvailable(variant.getStockAvailable() + quantity);
    }

    public void finalizeReservation(ProductVariant variant, int quantity) {
        if (inventory != null) {
            inventory.finalizeReservation(variant, quantity);
            return;
        }
        if (resolvePolicy(variant) == InventoryPolicy.BULK_WEIGHT) {
            Product product = variant.getProduct();
            int finalizedGrams = requiredGrams(variant, quantity);
            int currentBase = product.getStockBaseGrams() == null ? 0 : product.getStockBaseGrams();
            product.setStockBaseGrams(Math.max(0, currentBase - finalizedGrams));
            product.setStockReservedBaseGrams(Math.max(0, product.getStockReservedBaseGrams() - finalizedGrams));
            return;
        }

        variant.setStockReserved(Math.max(0, variant.getStockReserved() - quantity));
    }

    private void reserveBulkWeight(ProductVariant variant, int quantity) {
        Product product = variant.getProduct();
        Integer available = product.getStockBaseGrams();
        if (available == null) {
            throw new IllegalArgumentException("Variant incompatible with product policy");
        }

        int requiredGrams = requiredGrams(variant, quantity);
        int availableForReservation = available - product.getStockReservedBaseGrams();
        if (availableForReservation < requiredGrams) {
            throw new ConflictException("Insufficient stock");
        }

        product.setStockReservedBaseGrams(product.getStockReservedBaseGrams() + requiredGrams);
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
