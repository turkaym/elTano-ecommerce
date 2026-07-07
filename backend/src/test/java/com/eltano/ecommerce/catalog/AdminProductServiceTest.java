package com.eltano.ecommerce.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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

import com.eltano.ecommerce.catalog.api.dto.AdminProductImageUpsertRequest;
import com.eltano.ecommerce.catalog.api.dto.AdminProductUpsertRequest;
import com.eltano.ecommerce.catalog.api.dto.AdminProductVariantUpsertRequest;
import com.eltano.ecommerce.catalog.domain.Category;
import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductType;
import com.eltano.ecommerce.catalog.domain.UnitType;
import com.eltano.ecommerce.catalog.repository.CategoryRepository;
import com.eltano.ecommerce.catalog.repository.ProductRepository;
import com.eltano.ecommerce.catalog.repository.ProductVariantRepository;
import com.eltano.ecommerce.catalog.service.AdminProductService;
import com.eltano.ecommerce.common.api.ConflictException;
import com.eltano.ecommerce.common.api.UnprocessableEntityException;

@ExtendWith(MockitoExtension.class)
class AdminProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private AdminProductService adminProductService;

    @BeforeEach
    void setUp() {
        adminProductService = new AdminProductService(productRepository, productVariantRepository, categoryRepository);
    }

    @Test
    void softDeleteMarksProductAsDeleted() {
        UUID productId = UUID.randomUUID();
        Product product = product(productId);
        when(productRepository.findByIdWithRelations(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminProductService.softDelete(productId, "admin-user", "deprecated");

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product saved = productCaptor.getValue();
        assertNotNull(saved.getDeletedAt());
        assertEquals("deprecated", saved.getDeleteReason());
    }

    @Test
    void restoreClearsSoftDeleteFields() {
        UUID productId = UUID.randomUUID();
        Product product = product(productId);
        product.setDeletedBy("admin-user");
        product.setDeleteReason("deprecated");
        product.setDeletedAt(java.time.Instant.now());

        when(productRepository.findByIdWithRelations(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminProductService.restore(productId);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product saved = productCaptor.getValue();
        assertNull(saved.getDeletedAt());
        assertNull(saved.getDeletedBy());
        assertNull(saved.getDeleteReason());
    }

    @Test
    void createRejectsInvalidImageUrlWith422() {
        UUID categoryId = UUID.randomUUID();
        AdminProductUpsertRequest request = validRequest(categoryId, List.of(
                new AdminProductImageUpsertRequest(null, "not-a-url", "alt", 0, true)));

        UnprocessableEntityException exception = assertThrows(UnprocessableEntityException.class,
                () -> adminProductService.create(request));

        assertEquals("Product images validation failed", exception.getMessage());
        assertEquals(1, exception.getFieldErrors().size());
        assertEquals("images[0].url", exception.getFieldErrors().get(0).field());
    }

    @Test
    void createAllowsProductsWithoutImages() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category(categoryId)));
        when(productRepository.existsBySlugIgnoreCase("almendra")).thenReturn(false);
        when(productVariantRepository.existsBySkuIgnoreCase("SKU-1")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.findByIdWithRelations(any())).thenReturn(Optional.of(product(categoryId)));

        adminProductService.create(validRequest(categoryId, List.of()));

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertTrue(productCaptor.getValue().getImages().isEmpty());
    }

    @Test
    void createAllowsProductsWhenImagesAreOmitted() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category(categoryId)));
        when(productRepository.existsBySlugIgnoreCase("almendra")).thenReturn(false);
        when(productVariantRepository.existsBySkuIgnoreCase("SKU-1")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.findByIdWithRelations(any())).thenReturn(Optional.of(product(categoryId)));

        adminProductService.create(validRequest(categoryId, null));

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertTrue(productCaptor.getValue().getImages().isEmpty());
    }

    @Test
    void createRejectsInventoryPolicyConflicts() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category(categoryId)));

        AdminProductUpsertRequest request = new AdminProductUpsertRequest(
                "Almendra",
                "almendra-conflict",
                "desc",
                true,
                categoryId,
                ProductType.GRANEL,
                InventoryPolicy.PER_VARIANT,
                1000,
                List.of(validVariant()),
                List.of(new AdminProductImageUpsertRequest(null, "https://cdn.example.com/a.jpg", "alt", 0, true)));

        assertThrows(ConflictException.class, () -> adminProductService.create(request));
    }

    @Test
    void updateRejectsInventoryPolicyViolationAndKeepsPriorState() {
        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Category category = category(categoryId);
        Product existing = product(productId);
        existing.setCategory(category);
        existing.setProductType(ProductType.ENVASADO);
        existing.setInventoryPolicy(InventoryPolicy.PER_VARIANT);

        when(productRepository.findByIdWithRelations(productId)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        AdminProductUpsertRequest invalidRequest = new AdminProductUpsertRequest(
                "Almendra",
                "almendra",
                "desc",
                true,
                categoryId,
                ProductType.GRANEL,
                InventoryPolicy.PER_VARIANT,
                1000,
                List.of(validVariant()),
                List.of(new AdminProductImageUpsertRequest(null, "https://cdn.example.com/a.jpg", "alt", 0, true)));

        assertThrows(ConflictException.class, () -> adminProductService.update(productId, invalidRequest));

        assertEquals(ProductType.ENVASADO, existing.getProductType());
        assertEquals(InventoryPolicy.PER_VARIANT, existing.getInventoryPolicy());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void listMapsGranelReservedBaseGrams() {
        Product product = product(UUID.randomUUID());
        product.setProductType(ProductType.GRANEL);
        product.setInventoryPolicy(InventoryPolicy.BULK_WEIGHT);
        product.setStockBaseGrams(5000);
        product.setStockReservedBaseGrams(750);
        when(productRepository.findAllWithRelations()).thenReturn(List.of(product));

        var responses = adminProductService.list();

        assertEquals(1, responses.size());
        assertEquals(5000, responses.get(0).stockBaseGrams());
        assertEquals(750, responses.get(0).stockReservedBaseGrams());
        verifyNoInteractions(productVariantRepository, categoryRepository);
    }

    @Test
    void listDefaultsMissingGranelReservedBaseGramsToZero() {
        Product product = product(UUID.randomUUID());
        product.setProductType(ProductType.GRANEL);
        product.setInventoryPolicy(InventoryPolicy.BULK_WEIGHT);
        product.setStockBaseGrams(5000);
        when(productRepository.findAllWithRelations()).thenReturn(List.of(product));

        var responses = adminProductService.list();

        assertEquals(1, responses.size());
        assertEquals(5000, responses.get(0).stockBaseGrams());
        assertEquals(0, responses.get(0).stockReservedBaseGrams());
    }

    private Product product(UUID productId) {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", productId);
        product.setName("Almendra");
        product.setSlug("almendra");
        product.setDescription("desc");
        product.setActive(true);
        product.setCategory(category(UUID.randomUUID()));
        product.setProductType(ProductType.ENVASADO);
        product.setInventoryPolicy(InventoryPolicy.PER_VARIANT);
        return product;
    }

    private Category category(UUID id) {
        Category category = new Category();
        ReflectionTestUtils.setField(category, "id", id);
        category.setName("Frutos secos");
        category.setSlug("frutos-secos");
        category.setActive(true);
        return category;
    }

    private AdminProductUpsertRequest validRequest(UUID categoryId, List<AdminProductImageUpsertRequest> images) {
        return new AdminProductUpsertRequest(
                "Almendra",
                "almendra",
                "desc",
                true,
                categoryId,
                ProductType.ENVASADO,
                InventoryPolicy.PER_VARIANT,
                null,
                List.of(validVariant()),
                images);
    }

    private AdminProductVariantUpsertRequest validVariant() {
        return new AdminProductVariantUpsertRequest(
                null,
                "SKU-1",
                UnitType.WEIGHT,
                500,
                "bolsa 500 g",
                new BigDecimal("6000.00"),
                5,
                0,
                true,
                null);
    }
}
