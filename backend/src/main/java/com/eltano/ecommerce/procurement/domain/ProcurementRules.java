package com.eltano.ecommerce.procurement.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class ProcurementRules {
    private ProcurementRules() { }

    public static int toCanonical(BigDecimal quantity, BigDecimal conversion) {
        if (quantity == null || conversion == null || quantity.signum() <= 0 || conversion.signum() <= 0) {
            throw new IllegalArgumentException("Quantity and conversion must be positive");
        }
        try {
            return quantity.multiply(conversion).setScale(0, RoundingMode.UNNECESSARY).intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Conversion must produce an exact canonical integer", exception);
        }
    }

    public static LineProgress progress(BigDecimal ordered, List<DispositionQuantity> dispositions) {
        if (ordered == null || ordered.signum() <= 0) throw new IllegalArgumentException("Ordered quantity must be positive");
        BigDecimal finalQuantity = dispositions.stream()
                .filter(item -> item.type() == DispositionType.ACCEPTED_ORDERED
                        || item.type() == DispositionType.REJECTED_FINAL
                        || item.type() == DispositionType.NOT_DELIVERABLE_FINAL)
                .map(DispositionQuantity::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (dispositions.stream().anyMatch(item -> item.quantity() == null || item.quantity().signum() <= 0)
                || finalQuantity.compareTo(ordered) > 0) {
            throw new IllegalArgumentException("Disposition progress exceeds ordered quantity");
        }
        BigDecimal outstanding = ordered.subtract(finalQuantity).stripTrailingZeros();
        if (outstanding.signum() == 0) outstanding = BigDecimal.ZERO;
        return new LineProgress(outstanding, outstanding.signum() == 0 ? PurchaseStatus.RECEIVED : PurchaseStatus.PENDING);
    }

    public static void validateNote(DispositionType type, String note) {
        if (type == DispositionType.ACCEPTED_EXCESS && (note == null || note.isBlank())) {
            throw new IllegalArgumentException("Accepted excess requires a note");
        }
    }
}
