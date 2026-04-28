package com.eltano.ecommerce.catalog.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eltano.ecommerce.catalog.api.dto.PublicCatalogProductResponse;
import com.eltano.ecommerce.catalog.domain.ProductImage;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.catalog.repository.ProductRepository;

@Service
public class CatalogQueryService {

    private final ProductRepository productRepository;

    public CatalogQueryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<PublicCatalogProductResponse> list(String categorySlug, String text) {
        String normalizedCategorySlug = normalize(categorySlug);
        String normalizedText = normalize(text);

        return productRepository.searchPublicCatalog(normalizedCategorySlug).stream()
                .filter(product -> matchesText(product, normalizedText))
                .map(this::toPublicResponse)
                .toList();
    }

    private boolean matchesText(Product product, String normalizedText) {
        if (normalizedText == null) {
            return true;
        }

        return product.getName().toLowerCase(Locale.ROOT).contains(normalizedText)
                || product.getDescription().toLowerCase(Locale.ROOT).contains(normalizedText);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private PublicCatalogProductResponse toPublicResponse(Product product) {
        List<PublicCatalogProductResponse.PublicCatalogVariantResponse> variants = product.getVariants().stream()
                .filter(ProductVariant::isActive)
                .map(variant -> new PublicCatalogProductResponse.PublicCatalogVariantResponse(
                        variant.getId(),
                        variant.getSku(),
                        variant.getUnitType(),
                        variant.getWeightGrams(),
                        variant.getUnitLabel(),
                        variant.getPrice(),
                        variant.getStockAvailable(),
                        variant.getStockReserved(),
                        variant.getAttributesJson()))
                .toList();

        List<PublicCatalogProductResponse.PublicCatalogImageResponse> images = product.getImages().stream()
                .sorted(java.util.Comparator.comparingInt(ProductImage::getSortOrder))
                .map(image -> new PublicCatalogProductResponse.PublicCatalogImageResponse(
                        image.getId(),
                        image.getUrl(),
                        image.getAltText(),
                        image.getSortOrder(),
                        image.isPrimary()))
                .toList();

        return new PublicCatalogProductResponse(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getCategory().getName(),
                product.getCategory().getSlug(),
                product.getProductType(),
                product.getInventoryPolicy(),
                product.getStockBaseGrams(),
                images,
                variants);
    }
}
