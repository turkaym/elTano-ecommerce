package com.eltano.ecommerce.inventory.service;

import java.util.UUID;

import com.eltano.ecommerce.procurement.domain.InventoryTargetType;

public record InventoryDelta(InventoryTargetType targetType, UUID targetId, int delta) {
    public static InventoryDelta bulk(UUID productId, int delta) { return new InventoryDelta(InventoryTargetType.BULK_GRAM, productId, delta); }
    public static InventoryDelta variant(UUID variantId, int delta) { return new InventoryDelta(InventoryTargetType.VARIANT_UNIT, variantId, delta); }
}
