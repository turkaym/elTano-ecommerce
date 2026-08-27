package com.eltano.ecommerce.procurement.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.eltano.ecommerce.procurement.domain.PurchaseReceipt;
import com.eltano.ecommerce.procurement.domain.ReceiptKind;

public interface PurchaseReceiptRepository extends JpaRepository<PurchaseReceipt, UUID> {
    Optional<PurchaseReceipt> findByPurchaseIdAndKindAndIdempotencyKey(UUID purchaseId, ReceiptKind kind, String key);
    List<PurchaseReceipt> findAllByPurchaseIdOrderByConfirmedAt(UUID purchaseId);
}
