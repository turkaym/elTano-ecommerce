package com.eltano.ecommerce.procurement.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.eltano.ecommerce.procurement.domain.InventoryTargetType;
import com.eltano.ecommerce.procurement.domain.StockMovement;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
    List<StockMovement> findAllByPurchaseIdOrderByCreatedAt(UUID purchaseId);
    List<StockMovement> findAllByReceiptIdOrderByCreatedAt(UUID receiptId);
    boolean existsByTargetTypeAndTargetId(InventoryTargetType targetType, UUID targetId);
}
