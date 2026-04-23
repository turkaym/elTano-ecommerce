package com.eltano.ecommerce.orders.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.catalog.repository.ProductVariantRepository;
import com.eltano.ecommerce.common.api.ConflictException;
import com.eltano.ecommerce.orders.domain.OrderDraft;
import com.eltano.ecommerce.orders.domain.OrderDraftLine;
import com.eltano.ecommerce.orders.domain.OrderDraftStatus;
import com.eltano.ecommerce.orders.payment.mercadopago.MercadoPagoClient;
import com.eltano.ecommerce.orders.repository.OrderDraftRepository;

@ExtendWith(MockitoExtension.class)
class OrderDraftServiceTest {

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private OrderDraftRepository orderDraftRepository;

    @Mock
    private MercadoPagoClient mercadoPagoClient;

    @Mock
    private InventoryPolicyService inventoryPolicyService;

    private OrderDraftService orderDraftService;

    @BeforeEach
    void setUp() {
        orderDraftService = new OrderDraftService(
                productVariantRepository,
                orderDraftRepository,
                mercadoPagoClient,
                inventoryPolicyService);
    }

    @Test
    void createDraftReservesStockAndBuildsTotals() {
        stubPersistenceLayer();
        ProductVariant variant = variantWith("Almendra", 6000, 10, 0);
        when(productVariantRepository.findAllByIdInForUpdate(anyList())).thenReturn(List.of(variant));

        OrderDraftService.Result result = orderDraftService.createDraft(new OrderDraftService.Command(
                "Juan Perez",
                "+5491112345678",
                "Tocar timbre",
                List.of(new OrderDraftService.CommandItem(variant.getId(), 2))));

        assertTrue(result.reference().startsWith("ET-" + Year.now().getValue() + "-"));
        assertEquals(new BigDecimal("12000.00"), result.subtotal());
        assertEquals(new BigDecimal("12000.00"), result.total());
        assertTrue(result.whatsappMessage().contains(result.reference()));
        assertTrue(result.whatsappMessage().contains("Almendra"));
        verify(inventoryPolicyService).reserve(variant, 2);

        ArgumentCaptor<OrderDraft> captor = ArgumentCaptor.forClass(OrderDraft.class);
        verify(orderDraftRepository).save(captor.capture());
        assertEquals("DRAFT", captor.getValue().getStatus().name());
        assertEquals(1, captor.getValue().getLines().size());
    }

    @Test
    void createDraftThrowsConflictWhenInsufficientStock() {
        ProductVariant variant = variantWith("Almendra", 4000, 1, 0);
        when(productVariantRepository.findAllByIdInForUpdate(anyList())).thenReturn(List.of(variant));
        doThrow(new ConflictException("Insufficient stock"))
                .when(inventoryPolicyService)
                .reserve(variant, 3);

        assertThrows(ConflictException.class, () -> orderDraftService.createDraft(new OrderDraftService.Command(
                "Juan Perez",
                "+5491112345678",
                null,
                List.of(new OrderDraftService.CommandItem(variant.getId(), 3)))));
    }

    @Test
    void createDraftThrowsBadRequestWhenVariantMissing() {
        UUID missingVariantId = UUID.randomUUID();
        when(productVariantRepository.findAllByIdInForUpdate(anyList())).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> orderDraftService.createDraft(new OrderDraftService.Command(
                "Juan Perez",
                "+5491112345678",
                null,
                List.of(new OrderDraftService.CommandItem(missingVariantId, 1)))));
    }

    @Test
    void createDraftSupportsMultipleLinesTotalAggregation() {
        stubPersistenceLayer();
        ProductVariant variantOne = variantWith("Almendra", 5000, 10, 0);
        ProductVariant variantTwo = variantWith("Nuez", 3000, 10, 0);
        when(productVariantRepository.findAllByIdInForUpdate(anyList())).thenReturn(List.of(variantOne, variantTwo));

        OrderDraftService.Result result = orderDraftService.createDraft(new OrderDraftService.Command(
                "Juan Perez",
                "+5491112345678",
                null,
                List.of(
                        new OrderDraftService.CommandItem(variantOne.getId(), 1),
                        new OrderDraftService.CommandItem(variantTwo.getId(), 2))));

        assertEquals(new BigDecimal("11000.00"), result.total());
        assertTrue(result.whatsappMessage().contains("Nuez"));
        verify(inventoryPolicyService).reserve(variantOne, 1);
        verify(inventoryPolicyService).reserve(variantTwo, 2);
    }

    @Test
    void createDraftRejectsMissingVariantSelection() {
        when(productVariantRepository.findAllByIdInForUpdate(anyList())).thenReturn(List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> orderDraftService.createDraft(
                new OrderDraftService.Command(
                        "Juan Perez",
                        "+5491112345678",
                        null,
                        List.of(new OrderDraftService.CommandItem(null, 1)))));

        assertEquals("Variant selection required", ex.getMessage());
    }

    @Test
    void applyPaymentTransitionReleasesBulkWeightStockOnFailure() {
        UUID draftId = UUID.randomUUID();
        ProductVariant variant = bulkVariantWith(500, 3000);

        OrderDraftLine line = new OrderDraftLine();
        line.setVariant(variant);
        line.setQuantity(2);
        line.setUnitPrice(new BigDecimal("4000.00"));
        line.setLineTotal(new BigDecimal("8000.00"));
        line.setProductName("Almendra");
        line.setUnitLabel("bolsa 500 g");

        OrderDraft draft = new OrderDraft();
        ReflectionTestUtils.setField(draft, "id", draftId);
        draft.setStatus(OrderDraftStatus.PAYMENT_PENDING);
        draft.addLine(line);

        when(orderDraftRepository.findByIdForUpdate(draftId)).thenReturn(Optional.of(draft));
        when(orderDraftRepository.save(any(OrderDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderDraftService.applyPaymentTransition(
                new OrderDraftService.PaymentTransitionCommand(draftId, "pay-1", OrderDraftStatus.FAILED, "rejected"));

        verify(inventoryPolicyService).release(variant, 2);
    }

    @Test
    void startPaymentAllowsDraftAndMarksPending() {
        UUID draftId = UUID.randomUUID();
        OrderDraft draft = new OrderDraft();
        ReflectionTestUtils.setField(draft, "id", draftId);
        draft.setStatus(OrderDraftStatus.DRAFT);

        when(orderDraftRepository.findByIdForUpdate(draftId)).thenReturn(Optional.of(draft));
        when(orderDraftRepository.save(any(OrderDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderDraftService.PaymentPreferenceResult result = orderDraftService.startPayment(
                new OrderDraftService.StartPaymentCommand(draftId, "pref-123", "https://mp/init"));

        assertEquals("pref-123", result.preferenceId());
        assertEquals("https://mp/init", result.initPoint());
        assertEquals(OrderDraftStatus.PAYMENT_PENDING, draft.getStatus());
    }

    @Test
    void startPaymentKeepsPendingWhenAlreadyPending() {
        UUID draftId = UUID.randomUUID();
        OrderDraft draft = new OrderDraft();
        ReflectionTestUtils.setField(draft, "id", draftId);
        draft.setStatus(OrderDraftStatus.PAYMENT_PENDING);

        when(orderDraftRepository.findByIdForUpdate(draftId)).thenReturn(Optional.of(draft));
        when(orderDraftRepository.save(any(OrderDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderDraftService.PaymentPreferenceResult result = orderDraftService.startPayment(
                new OrderDraftService.StartPaymentCommand(draftId, "pref-abc", "https://mp/init-2"));

        assertEquals("pref-abc", result.preferenceId());
        assertEquals(OrderDraftStatus.PAYMENT_PENDING, draft.getStatus());
    }

    @Test
    void startPaymentRejectsTerminalStates() {
        UUID draftId = UUID.randomUUID();

        for (OrderDraftStatus status : List.of(
                OrderDraftStatus.PAID,
                OrderDraftStatus.CANCELLED,
                OrderDraftStatus.FAILED,
                OrderDraftStatus.EXPIRED)) {
            OrderDraft draft = new OrderDraft();
            ReflectionTestUtils.setField(draft, "id", draftId);
            draft.setStatus(status);
            when(orderDraftRepository.findByIdForUpdate(draftId)).thenReturn(Optional.of(draft));

            assertThrows(IllegalStateException.class, () -> orderDraftService.startPayment(
                    new OrderDraftService.StartPaymentCommand(draftId, "pref-x", "https://mp/init")));
        }
    }

    @Test
    void applyPaymentTransitionReleasesReservedStockOnceForFailure() {
        UUID draftId = UUID.randomUUID();
        ProductVariant variant = variantWith("Almendra", 4000, 3, 2);

        OrderDraftLine line = new OrderDraftLine();
        line.setVariant(variant);
        line.setQuantity(2);
        line.setUnitPrice(new BigDecimal("4000.00"));
        line.setLineTotal(new BigDecimal("8000.00"));
        line.setProductName("Almendra");
        line.setUnitLabel("bolsa 500 g");

        OrderDraft draft = new OrderDraft();
        ReflectionTestUtils.setField(draft, "id", draftId);
        draft.setStatus(OrderDraftStatus.PAYMENT_PENDING);
        draft.addLine(line);

        when(orderDraftRepository.findByIdForUpdate(draftId)).thenReturn(Optional.of(draft));
        when(orderDraftRepository.save(any(OrderDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderDraftService.PaymentTransitionResult result = orderDraftService.applyPaymentTransition(
                new OrderDraftService.PaymentTransitionCommand(draftId, "pay-1", OrderDraftStatus.FAILED, "rejected"));

        assertEquals("FAILED_APPLIED", result.outcome());
        assertEquals(OrderDraftStatus.FAILED, draft.getStatus());
        verify(inventoryPolicyService).release(variant, 2);
        Instant releasedAt = draft.getStockReleasedAt();

        orderDraftService.applyPaymentTransition(
                new OrderDraftService.PaymentTransitionCommand(draftId, "pay-1", OrderDraftStatus.FAILED, "rejected"));
        assertEquals(releasedAt, draft.getStockReleasedAt());
    }

    @Test
    void applyPaymentTransitionReleasesReservedStockOnceForCancelledAndExpired() {
        for (OrderDraftStatus terminal : List.of(OrderDraftStatus.CANCELLED, OrderDraftStatus.EXPIRED)) {
            UUID draftId = UUID.randomUUID();
            ProductVariant variant = variantWith("Almendra", 4000, 3, 2);
            reset(inventoryPolicyService);

            OrderDraftLine line = new OrderDraftLine();
            line.setVariant(variant);
            line.setQuantity(2);
            line.setUnitPrice(new BigDecimal("4000.00"));
            line.setLineTotal(new BigDecimal("8000.00"));
            line.setProductName("Almendra");
            line.setUnitLabel("bolsa 500 g");

            OrderDraft draft = new OrderDraft();
            ReflectionTestUtils.setField(draft, "id", draftId);
            draft.setStatus(OrderDraftStatus.PAYMENT_PENDING);
            draft.addLine(line);

            when(orderDraftRepository.findByIdForUpdate(draftId)).thenReturn(Optional.of(draft));
            when(orderDraftRepository.save(any(OrderDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

            OrderDraftService.PaymentTransitionResult result = orderDraftService.applyPaymentTransition(
                    new OrderDraftService.PaymentTransitionCommand(draftId, "pay-" + terminal, terminal, terminal.name()));

            assertEquals(terminal.name() + "_APPLIED", result.outcome());
            verify(inventoryPolicyService, times(1)).release(variant, 2);

            Instant releasedAt = draft.getStockReleasedAt();
            orderDraftService.applyPaymentTransition(
                    new OrderDraftService.PaymentTransitionCommand(draftId, "pay-" + terminal, terminal, terminal.name()));
            assertEquals(releasedAt, draft.getStockReleasedAt());
        }
    }

    @Test
    void applyPaymentTransitionIgnoresRegressiveUpdateAfterPaid() {
        UUID draftId = UUID.randomUUID();
        OrderDraft draft = new OrderDraft();
        ReflectionTestUtils.setField(draft, "id", draftId);
        draft.setStatus(OrderDraftStatus.PAID);

        when(orderDraftRepository.findByIdForUpdate(draftId)).thenReturn(Optional.of(draft));

        OrderDraftService.PaymentTransitionResult result = orderDraftService.applyPaymentTransition(
                new OrderDraftService.PaymentTransitionCommand(
                        draftId,
                        "pay-2",
                        OrderDraftStatus.FAILED,
                        "late_failure"));

        assertEquals("REGRESSION_IGNORED", result.outcome());
        assertEquals(OrderDraftStatus.PAID, draft.getStatus());
    }

    private ProductVariant variantWith(String productName, int price, int stockAvailable, int stockReserved) {
        Product product = new Product();
        product.setName(productName);

        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", UUID.randomUUID());
        variant.setProduct(product);
        variant.setUnitLabel("bolsa 500 g");
        variant.setPrice(BigDecimal.valueOf(price));
        variant.setStockAvailable(stockAvailable);
        variant.setStockReserved(stockReserved);
        variant.setActive(true);
        variant.setSku("SKU-" + UUID.randomUUID());
        return variant;
    }

    private ProductVariant bulkVariantWith(int weightGrams, int stockBaseGrams) {
        Product product = new Product();
        product.setName("Almendra");
        product.setInventoryPolicy(com.eltano.ecommerce.catalog.domain.InventoryPolicy.BULK_WEIGHT);
        product.setProductType(com.eltano.ecommerce.catalog.domain.ProductType.GRANEL);
        product.setStockBaseGrams(stockBaseGrams);

        ProductVariant variant = variantWith("Almendra", 4000, 10, 0);
        variant.setProduct(product);
        variant.setWeightGrams(weightGrams);
        return variant;
    }

    private void stubPersistenceLayer() {
        when(orderDraftRepository.save(any(OrderDraft.class))).thenAnswer(invocation -> {
            OrderDraft draft = invocation.getArgument(0);
            ReflectionTestUtils.setField(draft, "id", UUID.randomUUID());
            return draft;
        });
        when(orderDraftRepository.existsByReference(any())).thenReturn(false);
    }
}
