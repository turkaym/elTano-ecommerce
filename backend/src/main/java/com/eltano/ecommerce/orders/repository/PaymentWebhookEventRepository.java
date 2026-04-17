package com.eltano.ecommerce.orders.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eltano.ecommerce.orders.domain.PaymentWebhookEvent;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {

    boolean existsByProviderEventId(String providerEventId);
}
