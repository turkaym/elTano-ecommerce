package com.eltano.ecommerce.procurement.domain;

import java.math.BigDecimal;

public record DispositionQuantity(DispositionType type, BigDecimal quantity) { }
