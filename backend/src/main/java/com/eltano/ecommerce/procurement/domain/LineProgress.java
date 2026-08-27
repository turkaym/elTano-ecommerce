package com.eltano.ecommerce.procurement.domain;

import java.math.BigDecimal;

public record LineProgress(BigDecimal outstanding, PurchaseStatus status) { }
