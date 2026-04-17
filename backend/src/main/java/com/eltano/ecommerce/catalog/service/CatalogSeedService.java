package com.eltano.ecommerce.catalog.service;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.eltano.ecommerce.catalog.domain.Category;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.catalog.domain.UnitType;
import com.eltano.ecommerce.catalog.repository.CategoryRepository;
import com.eltano.ecommerce.catalog.repository.ProductRepository;

@Component
@ConditionalOnProperty(prefix = "app.catalog", name = "seed-on-empty", havingValue = "true", matchIfMissing = true)
public class CatalogSeedService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogSeedService.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public CatalogSeedService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (productRepository.count() > 0) {
            return;
        }

        log.info("Catalog is empty. Seeding default products for local checkout flow.");

        Category nuts = upsertCategory("Frutos secos", "frutos-secos");
        Category seeds = upsertCategory("Semillas", "semillas");
        Category flours = upsertCategory("Harinas", "harinas");
        Category oils = upsertCategory("Aceites", "aceites");

        seedProduct(
                nuts,
                "Almendra natural premium",
                "almendra-natural-premium",
                "Ideal para snack, granola casera o manteca de almendras.",
                "ALM-500",
                "bolsa 500 g",
                500,
                new BigDecimal("6400.00"),
                24);

        seedProduct(
                seeds,
                "Mix semillas activas",
                "mix-semillas-activas",
                "Blend de chía, lino dorado, girasol y sésamo para sumar fibra.",
                "SEM-350",
                "bolsa 350 g",
                350,
                new BigDecimal("3900.00"),
                31);

        seedProduct(
                flours,
                "Harina de almendras fina",
                "harina-de-almendras-fina",
                "Textura pareja para panificados sin gluten y repostería saludable.",
                "HAR-ALM-1000",
                "bolsa 1 kg",
                1000,
                new BigDecimal("9800.00"),
                14);

        seedProduct(
                oils,
                "Aceite de oliva extra virgen",
                "aceite-de-oliva-extra-virgen",
                "Primera prensada en frío, sabor suave para cocina diaria.",
                "ACE-OLI-500",
                "botella 500 ml",
                500,
                new BigDecimal("7200.00"),
                19);

        seedProduct(
                nuts,
                "Nuez mariposa seleccionada",
                "nuez-mariposa-seleccionada",
                "Nuez grande, crocante y fresca para picadas o ensaladas.",
                "NUE-500",
                "bolsa 500 g",
                500,
                new BigDecimal("7600.00"),
                22);

        seedProduct(
                flours,
                "Harina de coco orgánica",
                "harina-de-coco-organica",
                "Alternativa baja en carbohidratos para recetas dulces y saladas.",
                "HAR-COC-500",
                "bolsa 500 g",
                500,
                new BigDecimal("5300.00"),
                16);
    }

    private Category upsertCategory(String name, String slug) {
        return categoryRepository.findBySlugIgnoreCase(slug)
                .map(existing -> {
                    existing.setName(name);
                    existing.setActive(true);
                    return existing;
                })
                .orElseGet(() -> {
                    Category category = new Category();
                    category.setName(name);
                    category.setSlug(slug);
                    category.setActive(true);
                    return category;
                });
    }

    private void seedProduct(
            Category category,
            String productName,
            String productSlug,
            String description,
            String sku,
            String unitLabel,
            Integer weightGrams,
            BigDecimal price,
            int stockAvailable) {
        Category persistedCategory = categoryRepository.save(category);

        Product product = new Product();
        product.setName(productName);
        product.setSlug(productSlug);
        product.setDescription(description);
        product.setActive(true);
        product.setCategory(persistedCategory);

        ProductVariant variant = new ProductVariant();
        variant.setSku(sku);
        variant.setUnitType(UnitType.WEIGHT);
        variant.setWeightGrams(weightGrams);
        variant.setUnitLabel(unitLabel);
        variant.setPrice(price);
        variant.setStockAvailable(stockAvailable);
        variant.setStockReserved(0);
        variant.setActive(true);
        variant.setAttributesJson(null);

        product.replaceVariants(List.of(variant));
        productRepository.save(product);
    }
}
