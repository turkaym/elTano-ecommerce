package com.eltano.ecommerce.catalog.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eltano.ecommerce.catalog.api.dto.AdminProductImageResponse;
import com.eltano.ecommerce.catalog.api.dto.AdminProductImageUpsertRequest;
import com.eltano.ecommerce.catalog.api.dto.AdminProductResponse;
import com.eltano.ecommerce.catalog.api.dto.AdminProductUpsertRequest;
import com.eltano.ecommerce.catalog.api.dto.AdminProductVariantResponse;
import com.eltano.ecommerce.catalog.api.dto.AdminProductVariantUpsertRequest;
import com.eltano.ecommerce.catalog.domain.Category;
import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductImage;
import com.eltano.ecommerce.catalog.domain.ProductType;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.catalog.repository.CategoryRepository;
import com.eltano.ecommerce.catalog.repository.ProductRepository;
import com.eltano.ecommerce.catalog.repository.ProductVariantRepository;
import com.eltano.ecommerce.common.api.ConflictException;
import com.eltano.ecommerce.common.api.ResourceNotFoundException;
import com.eltano.ecommerce.common.api.UnprocessableEntityException;

@Service
public class AdminProductService {

    private static final Pattern HTTP_URL_PATTERN = Pattern.compile("^https?://.+", Pattern.CASE_INSENSITIVE);

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
        ensureImagesValid(request.images());

        Category category = findCategory(request.categoryId());

        Product product = new Product();
        applyProductData(product, request, category);
        product.replaceVariants(buildVariants(product, List.of(), request.variants()));
        product.replaceImages(buildImages(product, List.of(), request.images()));

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
        ensureImagesValid(request.images());

        Category category = findCategory(request.categoryId());
        applyProductData(product, request, category);

        List<ProductVariant> updatedVariants = buildVariants(product, product.getVariants(), request.variants());
        product.replaceVariants(updatedVariants);
        List<ProductImage> updatedImages = buildImages(product, product.getImages(), request.images());
        product.replaceImages(updatedImages);

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

    @Transactional
    public void softDelete(UUID id, String deletedBy, String reason) {
        Product product = productRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        product.setDeletedAt(java.time.Instant.now());
        product.setDeletedBy(deletedBy == null ? null : deletedBy.trim());
        product.setDeleteReason(reason == null ? null : reason.trim());
        product.setActive(false);

        productRepository.save(product);
    }

    @Transactional
    public void restore(UUID id) {
        Product product = productRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        product.setDeletedAt(null);
        product.setDeletedBy(null);
        product.setDeleteReason(null);
        product.setActive(true);

        productRepository.save(product);
    }

    private void applyProductData(Product product, AdminProductUpsertRequest request, Category category) {
        ProductType resolvedProductType = resolveProductType(request.productType(), product.getProductType());
        InventoryPolicy resolvedInventoryPolicy = resolveInventoryPolicy(
                request.inventoryPolicy(),
                resolvedProductType,
                product.getInventoryPolicy());

        product.setName(request.name().trim());
        product.setSlug(request.slug().trim());
        product.setDescription(request.description().trim());
        product.setActive(request.active());
        product.setCategory(category);
        product.setProductType(resolvedProductType);
        product.setInventoryPolicy(resolvedInventoryPolicy);
        if (request.stockBaseGrams() != null || product.getStockBaseGrams() == null) {
            product.setStockBaseGrams(request.stockBaseGrams());
        }
    }

    private ProductType resolveProductType(ProductType requestedType, ProductType existingType) {
        if (requestedType != null) {
            return requestedType;
        }
        if (existingType != null) {
            return existingType;
        }
        return ProductType.ENVASADO;
    }

    private InventoryPolicy resolveInventoryPolicy(
            InventoryPolicy requestedPolicy,
            ProductType productType,
            InventoryPolicy existingPolicy) {
        InventoryPolicy resolvedPolicy = requestedPolicy != null
                ? requestedPolicy
                : (existingPolicy != null ? existingPolicy : defaultPolicyFor(productType));

        validatePolicyCombination(productType, resolvedPolicy);
        return resolvedPolicy;
    }

    private InventoryPolicy defaultPolicyFor(ProductType productType) {
        return productType == ProductType.GRANEL ? InventoryPolicy.BULK_WEIGHT : InventoryPolicy.PER_VARIANT;
    }

    private void validatePolicyCombination(ProductType productType, InventoryPolicy inventoryPolicy) {
        if (productType == ProductType.GRANEL && inventoryPolicy != InventoryPolicy.BULK_WEIGHT) {
            throw new ConflictException("GRANEL products require BULK_WEIGHT policy");
        }
        if ((productType == ProductType.ENVASADO || productType == ProductType.UNIDAD)
                && inventoryPolicy != InventoryPolicy.PER_VARIANT) {
            throw new ConflictException("ENVASADO/UNIDAD products require PER_VARIANT policy");
        }
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

    private List<ProductImage> buildImages(
            Product product,
            List<ProductImage> existingImages,
            List<AdminProductImageUpsertRequest> imageRequests) {
        Map<UUID, ProductImage> existingById = existingImages.stream()
                .collect(Collectors.toMap(ProductImage::getId, Function.identity()));

        List<ProductImage> output = new ArrayList<>();
        for (AdminProductImageUpsertRequest requestImage : imageRequests) {
            ProductImage image;
            if (requestImage.id() != null) {
                image = existingById.get(requestImage.id());
                if (image == null) {
                    throw new ResourceNotFoundException("Image not found in product");
                }
            } else {
                image = new ProductImage();
            }

            image.setProduct(product);
            image.setUrl(requestImage.url().trim());
            image.setAltText(requestImage.altText() == null ? null : requestImage.altText().trim());
            image.setSortOrder(requestImage.sortOrder());
            image.setPrimary(requestImage.primary());

            output.add(image);
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

    private void ensureImagesValid(List<AdminProductImageUpsertRequest> images) {
        if (images == null || images.isEmpty()) {
            throw new UnprocessableEntityException(
                    "At least one product image is required",
                    List.of(new UnprocessableEntityException.FieldError("images", "At least one product image is required")));
        }

        Set<Integer> sortOrders = new HashSet<>();
        int primaryCount = 0;
        List<UnprocessableEntityException.FieldError> fieldErrors = new ArrayList<>();

        for (int index = 0; index < images.size(); index++) {
            AdminProductImageUpsertRequest image = images.get(index);
            String url = image.url() == null ? "" : image.url().trim();
            if (!HTTP_URL_PATTERN.matcher(url).matches()) {
                fieldErrors.add(new UnprocessableEntityException.FieldError(
                        "images[" + index + "].url",
                        "Image URL must be a valid http/https URL"));
            }

            if (!sortOrders.add(image.sortOrder())) {
                fieldErrors.add(new UnprocessableEntityException.FieldError(
                        "images[" + index + "].sortOrder",
                        "Image sortOrder values must be unique per product"));
            }

            if (Boolean.TRUE.equals(image.primary())) {
                primaryCount++;
            }
        }

        if (primaryCount != 1) {
            fieldErrors.add(new UnprocessableEntityException.FieldError(
                    "images",
                    "Exactly one product image must be marked as primary"));
        }

        if (!fieldErrors.isEmpty()) {
            throw new UnprocessableEntityException("Product images validation failed", fieldErrors);
        }
    }

    private AdminProductResponse toResponse(Product product) {
        List<AdminProductVariantResponse> variants = product.getVariants().stream()
                .map(this::toVariantResponse)
                .toList();

        List<AdminProductImageResponse> images = product.getImages().stream()
                .sorted(java.util.Comparator.comparingInt(ProductImage::getSortOrder))
                .map(this::toImageResponse)
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
                product.getProductType(),
                product.getInventoryPolicy(),
                product.getStockBaseGrams(),
                variants,
                images,
                product.getDeletedAt(),
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

    private AdminProductImageResponse toImageResponse(ProductImage image) {
        return new AdminProductImageResponse(
                image.getId(),
                image.getUrl(),
                image.getAltText(),
                image.getSortOrder(),
                image.isPrimary(),
                image.getCreatedAt(),
                image.getUpdatedAt());
    }
}
