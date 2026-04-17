package com.eltano.ecommerce.catalog.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eltano.ecommerce.catalog.api.dto.AdminCategoryResponse;
import com.eltano.ecommerce.catalog.api.dto.AdminCategoryUpsertRequest;
import com.eltano.ecommerce.catalog.domain.Category;
import com.eltano.ecommerce.catalog.repository.CategoryRepository;
import com.eltano.ecommerce.common.api.ConflictException;
import com.eltano.ecommerce.common.api.ResourceNotFoundException;

@Service
public class AdminCategoryService {

    private final CategoryRepository categoryRepository;

    public AdminCategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
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
