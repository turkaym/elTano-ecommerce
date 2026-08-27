package com.eltano.ecommerce.procurement.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.eltano.ecommerce.procurement.domain.DispositionType;

public final class ReceiptCanonicalizer {
    private ReceiptCanonicalizer() { }

    public static String hash(List<Line> lines) {
        String canonical = lines.stream()
                .sorted(Comparator.comparing(Line::purchaseLineId).thenComparing(line -> line.type().name()))
                .map(line -> line.purchaseLineId() + "|" + line.type() + "|" + normalize(line.quantity()) + "|" + normalize(line.note()))
                .reduce((left, right) -> left + "\n" + right).orElse("");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String normalize(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private static String normalize(String value) { return value == null ? "" : value.trim(); }

    public record Line(UUID purchaseLineId, DispositionType type, BigDecimal quantity, String note) { }
}
