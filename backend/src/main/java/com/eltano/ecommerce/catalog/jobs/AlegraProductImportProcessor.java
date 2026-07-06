package com.eltano.ecommerce.catalog.jobs;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.eltano.ecommerce.catalog.domain.Category;
import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductImage;
import com.eltano.ecommerce.catalog.domain.ProductType;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.catalog.domain.UnitType;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJob;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobRow;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobRowOutcome;
import com.eltano.ecommerce.catalog.jobs.repository.AdminCatalogJobRowRepository;
import com.eltano.ecommerce.catalog.repository.CategoryRepository;
import com.eltano.ecommerce.catalog.repository.ProductRepository;
import com.eltano.ecommerce.catalog.repository.ProductVariantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class AlegraProductImportProcessor {

    private static final String PLACEHOLDER_IMAGE_URL = "/placeholder-product.svg";
    private static final List<VariantPlan> KILOGRAM_VARIANTS = List.of(
            new VariantPlan("100g", "100G", UnitType.WEIGHT, 100),
            new VariantPlan("250g", "250G", UnitType.WEIGHT, 250),
            new VariantPlan("500g", "500G", UnitType.WEIGHT, 500),
            new VariantPlan("1kg", "1KG", UnitType.WEIGHT, 1000));

    private final ObjectMapper objectMapper;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final AdminCatalogJobRowRepository jobRowRepository;

    public AlegraProductImportProcessor(
            ObjectMapper objectMapper,
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            AdminCatalogJobRowRepository jobRowRepository) {
        this.objectMapper = objectMapper;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.jobRowRepository = jobRowRepository;
    }

    public String process(AdminCatalogJob job, String payloadText) {
        ImportCounters counters = new ImportCounters();
        for (String line : splitLines(payloadText)) {
            RowPayload row = parseRow(line);
            counters.processed++;
            RowResult result = importRow(job, row, line);
            if (result.status == RowStatus.CREATED) {
                counters.created++;
            } else if (result.status == RowStatus.UPDATED) {
                counters.updated++;
            } else {
                counters.skipped++;
                counters.errors++;
            }
        }
        return "processed=" + counters.processed
                + ",created=" + counters.created
                + ",updated=" + counters.updated
                + ",skipped=" + counters.skipped
                + ",errors=" + counters.errors;
    }

    private RowResult importRow(AdminCatalogJob job, RowPayload row, String payload) {
        if (row.code().isBlank()) {
            return failed(job, row, "MISSING_KEY", "Referencia or Codigo is required", payload);
        }

        BigDecimal generalPrice;
        try {
            generalPrice = new BigDecimal(row.generalPrice()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return failed(job, row, "INVALID_PRICE", "Precio: General must be a valid decimal", payload);
        }
        if (generalPrice.compareTo(BigDecimal.ZERO) < 0) {
            return failed(job, row, "INVALID_PRICE", "Precio: General must be zero or greater", payload);
        }

        UnitMapping unitMapping = unitMapping(row.unitOfMeasure()).orElse(null);
        if (unitMapping == null) {
            return failed(job, row, "UNSUPPORTED_UNIT", "Unsupported unit of measure: " + row.unitOfMeasure(), payload);
        }

        String baseSku = normalizeSkuBase(row.code());
        if (baseSku.isBlank()) {
            return failed(job, row, "MISSING_KEY", "Referencia or Codigo must contain letters or numbers", payload);
        }

        List<String> skus = unitMapping.variantPlans().stream()
                .map(plan -> skuFor(baseSku, plan))
                .toList();
        Product product = resolveProduct(skus).orElse(null);
        boolean created = product == null;

        if (product == null) {
            product = new Product();
            product.setSlug(uniqueProductSlug(row.name(), baseSku));
            addPlaceholderImage(product, row.name());
        } else if (hasSkuCollision(product.getId(), skus)) {
            return failed(job, row, "SKU_COLLISION", "One or more target SKUs belong to another product", payload);
        } else if (product.getImages().isEmpty()) {
            addPlaceholderImage(product, row.name());
        }

        Category category = upsertCategory(row.category());
        product.setCategory(category);
        product.setName(row.name());
        product.setDescription(row.description().isBlank() ? row.name() : row.description());
        product.setActive(true);
        product.setProductType(unitMapping.productType());
        product.setInventoryPolicy(unitMapping.inventoryPolicy());
        if (created && unitMapping.productType() != ProductType.GRANEL) {
            product.setStockBaseGrams(null);
            product.setStockReservedBaseGrams(0);
        }

        for (VariantPlan variantPlan : unitMapping.variantPlans()) {
            String sku = skuFor(baseSku, variantPlan);
            Product currentProduct = product;
            ProductVariant variant = product.getVariants().stream()
                    .filter(existing -> existing.getSku().equalsIgnoreCase(sku))
                    .findFirst()
                    .orElseGet(() -> newVariant(currentProduct, sku));
            variant.setSku(sku);
            variant.setUnitType(variantPlan.unitType());
            variant.setWeightGrams(variantPlan.weightGrams());
            variant.setUnitLabel(variantPlan.label());
            variant.setPrice(priceFor(generalPrice, variantPlan));
            variant.setActive(true);
        }

        productRepository.save(product);
        success(job, row, payload);
        return new RowResult(created ? RowStatus.CREATED : RowStatus.UPDATED);
    }

    private Optional<Product> resolveProduct(List<String> skus) {
        List<String> normalizedSkus = skus.stream()
                .map(sku -> sku.toLowerCase(Locale.ROOT))
                .toList();
        return productVariantRepository.findBySkuLowercaseInWithProduct(normalizedSkus).stream()
                .map(ProductVariant::getProduct)
                .findFirst();
    }

    private boolean hasSkuCollision(UUID productId, List<String> skus) {
        List<String> normalizedSkus = skus.stream()
                .map(sku -> sku.toLowerCase(Locale.ROOT))
                .toList();
        return productVariantRepository.findBySkuLowercaseInWithProduct(normalizedSkus).stream()
                .map(ProductVariant::getProduct)
                .anyMatch(product -> !product.getId().equals(productId));
    }

    private Category upsertCategory(String categoryName) {
        String slug = slugify(categoryName);
        Category category = categoryRepository.findBySlugIgnoreCase(slug).orElseGet(Category::new);
        category.setName(categoryName.trim());
        category.setSlug(slug);
        category.setActive(true);
        return categoryRepository.save(category);
    }

    private ProductVariant newVariant(Product product, String sku) {
        ProductVariant variant = new ProductVariant();
        variant.setSku(sku);
        variant.setStockAvailable(0);
        variant.setStockReserved(0);
        product.addVariant(variant);
        return variant;
    }

    private void addPlaceholderImage(Product product, String name) {
        ProductImage image = new ProductImage();
        image.setUrl(PLACEHOLDER_IMAGE_URL);
        image.setAltText(name);
        image.setSortOrder(0);
        image.setPrimary(true);
        product.addImage(image);
    }

    private String uniqueProductSlug(String name, String baseSku) {
        String candidate = slugify(name + " " + baseSku);
        if (!productRepository.existsBySlugIgnoreCase(candidate)) {
            return candidate;
        }
        int suffix = 2;
        while (productRepository.existsBySlugIgnoreCase(candidate + "-" + suffix)) {
            suffix++;
        }
        return candidate + "-" + suffix;
    }

    private Optional<UnitMapping> unitMapping(String value) {
        String normalized = normalizeText(value);
        if (normalized.equals("unidad") || normalized.equals("unidades") || normalized.equals("unit")) {
            return Optional.of(new UnitMapping(
                    ProductType.UNIDAD,
                    InventoryPolicy.PER_VARIANT,
                    List.of(new VariantPlan("unidad", "", UnitType.UNIT, null))));
        }
        if (normalized.equals("kilogramo") || normalized.equals("kilogramos") || normalized.equals("kg")) {
            return Optional.of(new UnitMapping(ProductType.GRANEL, InventoryPolicy.BULK_WEIGHT, KILOGRAM_VARIANTS));
        }
        return Optional.empty();
    }

    private BigDecimal priceFor(BigDecimal generalPrice, VariantPlan plan) {
        if (plan.weightGrams() == null) {
            return generalPrice;
        }
        return generalPrice.multiply(BigDecimal.valueOf(plan.weightGrams()))
                .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
    }

    private String skuFor(String baseSku, VariantPlan plan) {
        return plan.skuSuffix().isBlank() ? baseSku : baseSku + "-" + plan.skuSuffix();
    }

    private RowPayload parseRow(String line) {
        try {
            JsonNode node = objectMapper.readTree(line);
            return new RowPayload(
                    node.path("rowNumber").asInt(0),
                    text(node, "category"),
                    text(node, "name"),
                    text(node, "description"),
                    text(node, "code"),
                    text(node, "unitOfMeasure"),
                    text(node, "generalPrice"));
        } catch (Exception ex) {
            return new RowPayload(0, "", "", "", "", "", "");
        }
    }

    private String text(JsonNode node, String field) {
        return node.path(field).asText("").trim();
    }

    private RowResult failed(AdminCatalogJob job, RowPayload row, String errorCode, String errorMessage, String payload) {
        saveRow(job, row, AdminCatalogJobRowOutcome.FAILED, errorCode, errorMessage, payload);
        return new RowResult(RowStatus.SKIPPED);
    }

    private void success(AdminCatalogJob job, RowPayload row, String payload) {
        saveRow(job, row, AdminCatalogJobRowOutcome.SUCCESS, null, null, payload);
    }

    private void saveRow(
            AdminCatalogJob job,
            RowPayload rowPayload,
            AdminCatalogJobRowOutcome outcome,
            String errorCode,
            String errorMessage,
            String payload) {
        AdminCatalogJobRow row = new AdminCatalogJobRow();
        row.setJob(job);
        row.setRowNumber(rowPayload.rowNumber() == 0 ? 1 : rowPayload.rowNumber());
        row.setOutcome(outcome);
        row.setErrorCode(errorCode);
        row.setErrorMessage(errorMessage);
        row.setPayloadJson(payload);
        jobRowRepository.save(row);
    }

    private List<String> splitLines(String payloadText) {
        String normalized = payloadText == null ? "" : payloadText.replace("\r\n", "\n").replace('\r', '\n');
        String[] rawLines = normalized.split("\n", -1);
        List<String> lines = new ArrayList<>();
        for (String line : rawLines) {
            if (!line.isBlank()) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    private String normalizeSkuBase(String value) {
        String normalized = normalizeText(value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-");
        return normalized.replaceAll("(^-+|-+$)", "");
    }

    private String slugify(String value) {
        String normalized = normalizeText(value).replaceAll("[^a-z0-9]+", "-");
        String slug = normalized.replaceAll("(^-+|-+$)", "");
        return slug.isBlank() ? "item" : slug;
    }

    private String normalizeText(String value) {
        String decomposed = Normalizer.normalize(value == null ? "" : value.trim(), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    private record RowPayload(
            int rowNumber,
            String category,
            String name,
            String description,
            String code,
            String unitOfMeasure,
            String generalPrice) {
    }

    private record VariantPlan(String label, String skuSuffix, UnitType unitType, Integer weightGrams) {
    }

    private record UnitMapping(ProductType productType, InventoryPolicy inventoryPolicy, List<VariantPlan> variantPlans) {
    }

    private record RowResult(RowStatus status) {
    }

    private enum RowStatus {
        CREATED,
        UPDATED,
        SKIPPED
    }

    private static class ImportCounters {
        private int processed;
        private int created;
        private int updated;
        private int skipped;
        private int errors;
    }
}
