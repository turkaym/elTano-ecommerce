package com.eltano.ecommerce.catalog;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eltano.ecommerce.catalog.domain.Category;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.repository.CategoryRepository;
import com.eltano.ecommerce.catalog.repository.ProductRepository;
import com.eltano.ecommerce.catalog.service.AdminCategoryService;
import com.eltano.ecommerce.common.api.UnprocessableEntityException;

@ExtendWith(MockitoExtension.class)
class AdminCategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private AdminCategoryService adminCategoryService;

    @Test
    void deleteWithReassignmentMovesProductsAndDeletesCategoryAtomically() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        Category source = category(sourceId, "Frutos secos", "frutos-secos");
        Category target = category(targetId, "Semillas", "semillas");

        Product first = product("Almendra", "almendra", source);
        Product second = product("Nuez", "nuez", source);

        when(categoryRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(categoryRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(productRepository.findAllByCategoryId(sourceId)).thenReturn(List.of(first, second));

        adminCategoryService.deleteWithReassignment(sourceId, targetId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Product>> productsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(productRepository).saveAll(productsCaptor.capture());
        for (Product moved : productsCaptor.getValue()) {
            verifyCategoryTarget(moved, target);
        }
        verify(categoryRepository).delete(source);
    }

    @Test
    void deleteWithReassignmentRejectsMissingTargetAndKeepsState() {
        UUID sourceId = UUID.randomUUID();
        UUID missingTargetId = UUID.randomUUID();
        Category source = category(sourceId, "Frutos secos", "frutos-secos");
        Product first = product("Almendra", "almendra", source);

        when(categoryRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(categoryRepository.findById(missingTargetId)).thenReturn(Optional.empty());
        when(productRepository.findAllByCategoryId(sourceId)).thenReturn(List.of(first));

        assertThrows(UnprocessableEntityException.class,
                () -> adminCategoryService.deleteWithReassignment(sourceId, missingTargetId));

        verify(productRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
        verify(categoryRepository, never()).delete(source);
    }

    private Category category(UUID id, String name, String slug) {
        Category category = new Category();
        org.springframework.test.util.ReflectionTestUtils.setField(category, "id", id);
        category.setName(name);
        category.setSlug(slug);
        category.setActive(true);
        return category;
    }

    private Product product(String name, String slug, Category category) {
        Product product = new Product();
        product.setName(name);
        product.setSlug(slug);
        product.setDescription(name);
        product.setActive(true);
        product.setCategory(category);
        return product;
    }

    private void verifyCategoryTarget(Product product, Category expectedCategory) {
        if (!product.getCategory().getId().equals(expectedCategory.getId())) {
            throw new AssertionError("Product category was not reassigned");
        }
    }
}
