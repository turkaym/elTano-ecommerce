package com.eltano.ecommerce.catalog.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eltano.ecommerce.catalog.api.dto.AdminProductResponse;
import com.eltano.ecommerce.catalog.api.dto.AdminProductUpsertRequest;
import com.eltano.ecommerce.catalog.api.dto.AdminProductVariantResponse;
import com.eltano.ecommerce.catalog.api.dto.AdminProductVariantUpsertRequest;
import com.eltano.ecommerce.catalog.domain.Category;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.catalog.repository.CategoryRepository;
import com.eltano.ecommerce.catalog.repository.ProductRepository;
import com.eltano.ecommerce.catalog.repository.ProductVariantRepository;
import com.eltano.ecommerce.common.api.ConflictException;
import com.eltano.ecommerce.common.api.ResourceNotFoundException;

@Service
public class AdminProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CategoryRepository categoryRepository;

    public AdminProductService(
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public AdminProductResponse create(AdminProductUpsertRequest request) {
        ensureProductSlugUnique(request.slug(), null);
        ensureVariantSkusUniqueInPayload(request.variants());
        ensureVariantSkusUniqueInDb(request.variants());

        Category category = findCategory(request.categoryId());

        Product product = new Product();
        applyProductData(product, request, category);
        product.replaceVariants(buildVariants(product, List.of(), request.variants()));

        Product saved = productRepository.save(product);
        Product loaded = productRepository.findByIdWithRelations(saved.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return toResponse(loaded);
    }

    @Transactional
    public AdminProductResponse update(UUID id, AdminProductUpsertRequest request) {
        Product product = productRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        ensureProductSlugUnique(request.slug(), id);
        ensureVariantSkusUniqueInPayload(request.variants());

        Category category = findCategory(request.categoryId());
        applyProductData(product, request, category);

        List<ProductVariant> updatedVariants = buildVariants(product, product.getVariants(), request.variants());
        product.replaceVariants(updatedVariants);

        Product saved = productRepository.save(product);
        Product loaded = productRepository.findByIdWithRelations(saved.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return toResponse(loaded);
    }

    @Transactional(readOnly = true)
    public List<AdminProductResponse> list() {
        return productRepository.findAllWithRelations().stream()
                .map(this::toResponse)
                .toList();
    }

    private void applyProductData(Product product, AdminProductUpsertRequest request, Category category) {
        product.setName(request.name().trim());
        product.setSlug(request.slug().trim());
        product.setDescription(request.description().trim());
        product.setActive(request.active());
        product.setCategory(category);
    }

    private List<ProductVariant> buildVariants(
            Product product,
            List<ProductVariant> existingVariants,
            List<AdminProductVariantUpsertRequest> variantRequests) {
        Map<UUID, ProductVariant> existingById = existingVariants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        List<ProductVariant> output = new ArrayList<>();
        for (AdminProductVariantUpsertRequest requestVariant : variantRequests) {
            ProductVariant variant;
            if (requestVariant.id() != null) {
                variant = existingById.get(requestVariant.id());
                if (variant == null) {
                    throw new ResourceNotFoundException("Variant not found in product");
                }
                if (productVariantRepository.existsBySkuIgnoreCaseAndIdNot(requestVariant.sku().trim(), requestVariant.id())) {
                    throw new ConflictException("Variant sku already exists");
                }
            } else {
                if (productVariantRepository.existsBySkuIgnoreCase(requestVariant.sku().trim())) {
                    throw new ConflictException("Variant sku already exists");
                }
                variant = new ProductVariant();
            }

            variant.setProduct(product);
            variant.setSku(requestVariant.sku().trim());
            variant.setUnitType(requestVariant.unitType());
            variant.setWeightGrams(requestVariant.weightGrams());
            variant.setUnitLabel(requestVariant.unitLabel() == null ? null : requestVariant.unitLabel().trim());
            variant.setPrice(requestVariant.price());
            variant.setStockAvailable(requestVariant.stockAvailable());
            variant.setStockReserved(requestVariant.stockReserved());
            variant.setActive(requestVariant.active());
            variant.setAttributesJson(requestVariant.attributesJson());

            output.add(variant);
        }

        return output;
    }

    private void ensureProductSlugUnique(String slug, UUID productId) {
        String normalizedSlug = slug.trim();
        boolean exists = productId == null
                ? productRepository.existsBySlugIgnoreCase(normalizedSlug)
                : productRepository.existsBySlugIgnoreCaseAndIdNot(normalizedSlug, productId);
        if (exists) {
            throw new ConflictException("Product slug already exists");
        }
    }

    private void ensureVariantSkusUniqueInPayload(List<AdminProductVariantUpsertRequest> variants) {
        Set<String> seen = new HashSet<>();
        for (AdminProductVariantUpsertRequest variant : variants) {
            String key = variant.sku().trim().toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                throw new ConflictException("Duplicated sku in request payload");
            }
        }
    }

    private void ensureVariantSkusUniqueInDb(List<AdminProductVariantUpsertRequest> variants) {
        for (AdminProductVariantUpsertRequest variant : variants) {
            if (productVariantRepository.existsBySkuIgnoreCase(variant.sku().trim())) {
                throw new ConflictException("Variant sku already exists");
            }
        }
    }

    private Category findCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private AdminProductResponse toResponse(Product product) {
        List<AdminProductVariantResponse> variants = product.getVariants().stream()
                .map(this::toVariantResponse)
                .toList();

        return new AdminProductResponse(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.isActive(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getCategory().getSlug(),
                variants,
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

    private AdminProductVariantResponse toVariantResponse(ProductVariant variant) {
        return new AdminProductVariantResponse(
                variant.getId(),
                variant.getSku(),
                variant.getUnitType(),
                variant.getWeightGrams(),
                variant.getUnitLabel(),
                variant.getPrice(),
                variant.getStockAvailable(),
                variant.getStockReserved(),
                variant.isActive(),
                variant.getAttributesJson(),
                variant.getCreatedAt(),
                variant.getUpdatedAt());
    }
}
