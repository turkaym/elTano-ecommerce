package com.eltano.ecommerce.catalog.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eltano.ecommerce.catalog.api.dto.AdminCategoryResponse;
import com.eltano.ecommerce.catalog.api.dto.AdminCategoryUpsertRequest;
import com.eltano.ecommerce.catalog.domain.Category;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.repository.CategoryRepository;
import com.eltano.ecommerce.catalog.repository.ProductRepository;
import com.eltano.ecommerce.common.api.ConflictException;
import com.eltano.ecommerce.common.api.ResourceNotFoundException;
import com.eltano.ecommerce.common.api.UnprocessableEntityException;

@Service
public class AdminCategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public AdminCategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public AdminCategoryResponse create(AdminCategoryUpsertRequest request) {
        ensureSlugUnique(request.slug(), null);

        Category category = new Category();
        category.setName(request.name().trim());
        category.setSlug(request.slug().trim());
        category.setActive(request.active());

        Category saved = categoryRepository.save(category);
        return toResponse(saved);
    }

    @Transactional
    public AdminCategoryResponse update(UUID id, AdminCategoryUpsertRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        ensureSlugUnique(request.slug(), id);

        if (!request.active() && category.isActive()) {
            long activeProducts = productRepository.countByCategoryIdAndActiveTrueAndDeletedAtIsNull(id);
            if (activeProducts > 0) {
                throw new UnprocessableEntityException(
                        "Category cannot be deactivated while active products are associated",
                        List.of(new UnprocessableEntityException.FieldError(
                                "active",
                                "Deactivate or reassign active products before deactivating this category")));
            }
        }

        category.setName(request.name().trim());
        category.setSlug(request.slug().trim());
        category.setActive(request.active());

        Category saved = categoryRepository.save(category);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AdminCategoryResponse> list() {
        return categoryRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteWithReassignment(UUID id, UUID targetCategoryId) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        List<Product> linkedProducts = productRepository.findAllByCategoryId(id);
        if (linkedProducts.isEmpty()) {
            categoryRepository.delete(category);
            return;
        }

        if (targetCategoryId == null || id.equals(targetCategoryId)) {
            throw new UnprocessableEntityException("targetCategoryId is required and must be different when category has products");
        }

        Category targetCategory = categoryRepository.findById(targetCategoryId)
                .orElseThrow(() -> new UnprocessableEntityException("targetCategoryId is invalid"));

        linkedProducts.forEach(product -> product.setCategory(targetCategory));
        productRepository.saveAll(linkedProducts);
        categoryRepository.delete(category);
    }

    private void ensureSlugUnique(String slug, UUID categoryId) {
        String normalizedSlug = slug.trim();
        boolean exists = categoryId == null
                ? categoryRepository.existsBySlugIgnoreCase(normalizedSlug)
                : categoryRepository.existsBySlugIgnoreCaseAndIdNot(normalizedSlug, categoryId);
        if (exists) {
            throw new ConflictException("Category slug already exists");
        }
    }

    private AdminCategoryResponse toResponse(Category category) {
        return new AdminCategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.isActive(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }
}
