package com.eltano.ecommerce.orders.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eltano.ecommerce.orders.domain.OrderDraftLine;

public interface OrderDraftLineRepository extends JpaRepository<OrderDraftLine, UUID> {
}
