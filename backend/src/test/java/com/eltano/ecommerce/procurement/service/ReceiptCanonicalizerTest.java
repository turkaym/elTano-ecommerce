package com.eltano.ecommerce.procurement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.eltano.ecommerce.procurement.domain.DispositionType;

class ReceiptCanonicalizerTest {

    @Test
    void canonicalHashIgnoresInputOrderAndDecimalScale() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var a = new ReceiptCanonicalizer.Line(first, DispositionType.ACCEPTED_ORDERED, new BigDecimal("2.0"), null);
        var b = new ReceiptCanonicalizer.Line(second, DispositionType.TEMP_MISSING, new BigDecimal("1.000000"), " delayed ");

        assertEquals(ReceiptCanonicalizer.hash(List.of(a, b)), ReceiptCanonicalizer.hash(List.of(b, a)));
        assertEquals(ReceiptCanonicalizer.hash(List.of(a)), ReceiptCanonicalizer.hash(List.of(
                new ReceiptCanonicalizer.Line(first, DispositionType.ACCEPTED_ORDERED, new BigDecimal("2.000000"), null))));
    }

    @Test
    void canonicalHashChangesWhenSemanticContentChanges() {
        UUID line = UUID.randomUUID();
        String first = ReceiptCanonicalizer.hash(List.of(
                new ReceiptCanonicalizer.Line(line, DispositionType.ACCEPTED_ORDERED, BigDecimal.ONE, null)));
        String second = ReceiptCanonicalizer.hash(List.of(
                new ReceiptCanonicalizer.Line(line, DispositionType.ACCEPTED_ORDERED, new BigDecimal("2"), null)));

        assertNotEquals(first, second);
    }
}
