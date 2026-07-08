package com.eltano.ecommerce.catalog.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.eltano.ecommerce.catalog.domain.ProductVariant;

final class VariantDisplayOrder {

    private static final Pattern WEIGHT_LABEL_PATTERN = Pattern.compile(
            "(?i)(\\d+(?:[.,]\\d+)?)\\s*(kg|kilo|kilos|g|gr|gramo|gramos)\\b");

    static final Comparator<ProductVariant> COMPARATOR = Comparator
            .comparing(VariantDisplayOrder::knownWeightRank)
            .thenComparing(VariantDisplayOrder::resolvedGrams, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(VariantDisplayOrder::normalizedLabel)
            .thenComparing(VariantDisplayOrder::normalizedSku)
            .thenComparing(VariantDisplayOrder::normalizedId);

    private VariantDisplayOrder() {
    }

    private static int knownWeightRank(ProductVariant variant) {
        return resolvedGrams(variant) == null ? 1 : 0;
    }

    private static Integer resolvedGrams(ProductVariant variant) {
        if (variant.getWeightGrams() != null && variant.getWeightGrams() > 0) {
            return variant.getWeightGrams();
        }
        return parseLabelGrams(variant.getUnitLabel());
    }

    private static Integer parseLabelGrams(String unitLabel) {
        if (unitLabel == null) {
            return null;
        }

        Matcher matcher = WEIGHT_LABEL_PATTERN.matcher(unitLabel.trim());
        if (!matcher.find()) {
            return null;
        }

        BigDecimal amount = new BigDecimal(matcher.group(1).replace(',', '.'));
        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
        BigDecimal grams = unit.startsWith("k")
                ? amount.multiply(BigDecimal.valueOf(1000))
                : amount;
        return grams.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private static String normalizedLabel(ProductVariant variant) {
        return normalize(variant.getUnitLabel());
    }

    private static String normalizedSku(ProductVariant variant) {
        return normalize(variant.getSku());
    }

    private static String normalizedId(ProductVariant variant) {
        UUID id = variant.getId();
        return id == null ? "" : id.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
