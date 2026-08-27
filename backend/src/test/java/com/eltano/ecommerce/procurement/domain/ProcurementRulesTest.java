package com.eltano.ecommerce.procurement.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class ProcurementRulesTest {

    @Test
    void convertsSupplierQuantityToExactCanonicalInteger() {
        assertEquals(1500, ProcurementRules.toCanonical(new BigDecimal("3.000000"), new BigDecimal("500.000000")));
        assertEquals(12, ProcurementRules.toCanonical(new BigDecimal("2.000000"), new BigDecimal("6.000000")));
    }

    @Test
    void rejectsFractionalOrNonPositiveCanonicalConversions() {
        assertThrows(IllegalArgumentException.class,
                () -> ProcurementRules.toCanonical(new BigDecimal("1.000000"), new BigDecimal("0.500000")));
        assertThrows(IllegalArgumentException.class,
                () -> ProcurementRules.toCanonical(BigDecimal.ZERO, BigDecimal.ONE));
    }

    @Test
    void temporaryMissingDoesNotCloseOutstandingQuantity() {
        var progress = ProcurementRules.progress(new BigDecimal("10"), List.of(
                new DispositionQuantity(DispositionType.ACCEPTED_ORDERED, new BigDecimal("4")),
                new DispositionQuantity(DispositionType.TEMP_MISSING, new BigDecimal("6"))));

        assertEquals(new BigDecimal("6"), progress.outstanding());
        assertEquals(PurchaseStatus.PENDING, progress.status());
    }

    @Test
    void finalOutcomesCloseACompletelyDisposedLine() {
        var progress = ProcurementRules.progress(new BigDecimal("10"), List.of(
                new DispositionQuantity(DispositionType.ACCEPTED_ORDERED, new BigDecimal("4")),
                new DispositionQuantity(DispositionType.REJECTED_FINAL, new BigDecimal("2")),
                new DispositionQuantity(DispositionType.NOT_DELIVERABLE_FINAL, new BigDecimal("4"))));

        assertEquals(BigDecimal.ZERO, progress.outstanding());
        assertEquals(PurchaseStatus.RECEIVED, progress.status());
    }

    @Test
    void rejectsProgressBeyondOrderedQuantityAndRequiresExcessNote() {
        assertThrows(IllegalArgumentException.class, () -> ProcurementRules.progress(new BigDecimal("3"), List.of(
                new DispositionQuantity(DispositionType.ACCEPTED_ORDERED, new BigDecimal("4")))));
        assertThrows(IllegalArgumentException.class,
                () -> ProcurementRules.validateNote(DispositionType.ACCEPTED_EXCESS, "  "));
        ProcurementRules.validateNote(DispositionType.ACCEPTED_EXCESS, "Supplier sent replacement package");
    }
}
