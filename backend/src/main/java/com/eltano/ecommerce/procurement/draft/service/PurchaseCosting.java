package com.eltano.ecommerce.procurement.draft.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class PurchaseCosting {
    private PurchaseCosting() { }

    static BigDecimal parseUnitPrice(String source) {
        String value = source == null ? "" : source.trim();
        if (!value.matches("[0-9]+(?:[.,][0-9]{1,2})?")) throw new IllegalArgumentException();
        BigDecimal price = new BigDecimal(value.replace(',', '.'));
        if (price.signum() <= 0 || price.setScale(2).precision() > 19) throw new IllegalArgumentException();
        return price.setScale(2);
    }

    static BigDecimal lineTotal(BigDecimal quantity, BigDecimal unitPrice) {
        if (quantity == null || unitPrice == null) return null;
        BigDecimal total = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        if (total.signum() <= 0 || total.precision() > 19) throw new IllegalArgumentException();
        return total;
    }
}
