package com.eltano.ecommerce.procurement.draft.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SupplierProductNameNormalizerTest {
    private final SupplierProductNameNormalizer normalizer = new SupplierProductNameNormalizer();

    @Test
    void normalizesUnicodePunctuationCaseAndWhitespaceDeterministically() {
        assertEquals("mani tostado sin sal", normalizer.normalize("  MANI, tostado... sin sal  "));
        assertEquals("cafe premium", normalizer.normalize("Cafe\u0301---PREMIUM"));
    }
}
