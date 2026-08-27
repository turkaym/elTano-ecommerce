package com.eltano.ecommerce.inventory.service;

import java.util.UUID;

import com.eltano.ecommerce.procurement.domain.InventoryTargetType;

public record InventoryMutation(InventoryTargetType targetType, UUID targetId, int delta, int beforeBalance, int afterBalance) { }
