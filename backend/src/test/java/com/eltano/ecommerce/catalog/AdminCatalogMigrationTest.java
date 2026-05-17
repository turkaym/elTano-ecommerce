package com.eltano.ecommerce.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.eltano.ecommerce.catalog.domain.Category;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductImage;
import com.eltano.ecommerce.catalog.repository.CategoryRepository;
import com.eltano.ecommerce.catalog.repository.ProductRepository;

import jakarta.persistence.Column;

@SpringBootTest(properties = "app.catalog.seed-on-empty=false")
@ActiveProfiles("test")
class AdminCatalogMigrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void migrationV15DefinesSoftDeleteAndImageConstraints() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V1_5__admin_catalog_core.sql"));

        assertTrue(migration.contains("add column if not exists deleted_at"));
        assertTrue(migration.contains("add column if not exists deleted_by"));
        assertTrue(migration.contains("add column if not exists delete_reason"));
        assertTrue(migration.contains("create table if not exists product_images"));
        assertTrue(migration.contains("uk_product_images_product_sort_order"));
        assertTrue(migration.contains("idx_product_images_primary"));
    }

    @Test
    void migrationV19BackfillsReservedStockBeforeNotNullConstraint() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V1_9__bulk_stock_reservations.sql"));

        assertTrue(migration.contains("add column if not exists stock_reserved_base_grams integer"));
        assertTrue(migration.contains("alter column stock_reserved_base_grams set default 0"));
        assertTrue(migration.contains("update products"));
        assertTrue(migration.contains("set stock_reserved_base_grams = 0"));
        assertTrue(migration.contains("where stock_reserved_base_grams is null"));
        assertTrue(migration.contains("alter column stock_reserved_base_grams set not null"));
    }

    @Test
    void migrationV111ReplacesLegacyOrderDraftStatusConstraintName() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V1_11__fix_order_draft_status_constraint_name.sql"));

        assertTrue(migration.contains("drop constraint if exists order_drafts_status_check"));
        assertTrue(migration.contains("drop constraint if exists chk_order_drafts_status"));
        assertTrue(migration.contains("add constraint chk_order_drafts_status"));
        assertTrue(migration.contains("'PREPARING'"));
        assertTrue(migration.contains("'READY'"));
        assertTrue(migration.contains("'DELIVERED'"));
    }

    @Test
    void productReservedStockMappingAllowsHibernateUpdateToCreateBackfilledColumn() throws Exception {
        Column reservedStockColumn = Product.class.getDeclaredField("stockReservedBaseGrams").getAnnotation(Column.class);

        assertEquals(false, reservedStockColumn.nullable());
        assertTrue(reservedStockColumn.columnDefinition().contains("default 0"));
    }

    @Test
    void searchPublicCatalogExcludesSoftDeletedProducts() {
        Category category = new Category();
        category.setName("Frutos secos");
        category.setSlug("frutos-secos-soft-delete");
        category.setActive(true);
        categoryRepository.save(category);

        Product visible = new Product();
        visible.setName("Producto visible");
        visible.setSlug("producto-visible");
        visible.setDescription("Visible");
        visible.setActive(true);
        visible.setCategory(category);

        Product deleted = new Product();
        deleted.setName("Producto borrado");
        deleted.setSlug("producto-borrado");
        deleted.setDescription("Deleted");
        deleted.setActive(true);
        deleted.setCategory(category);
        deleted.setDeletedAt(Instant.now());

        productRepository.saveAll(List.of(visible, deleted));

        List<Product> catalog = productRepository.searchPublicCatalog("frutos-secos-soft-delete");
        assertEquals(1, catalog.size());
        assertEquals("Producto visible", catalog.get(0).getName());
    }

    @Test
    void imageUniqueSortOrderConstraintRejectsDuplicates() {
        Category category = new Category();
        category.setName("Aceites");
        category.setSlug("aceites-img-test");
        category.setActive(true);
        categoryRepository.save(category);

        Product product = new Product();
        product.setName("Aceite test");
        product.setSlug("aceite-test-img");
        product.setDescription("Desc");
        product.setActive(true);
        product.setCategory(category);

        ProductImage first = new ProductImage();
        first.setUrl("https://cdn.example.com/aceite-1.jpg");
        first.setAltText("Aceite 1");
        first.setSortOrder(1);
        first.setPrimary(true);

        ProductImage duplicatedSortOrder = new ProductImage();
        duplicatedSortOrder.setUrl("https://cdn.example.com/aceite-2.jpg");
        duplicatedSortOrder.setAltText("Aceite 2");
        duplicatedSortOrder.setSortOrder(1);
        duplicatedSortOrder.setPrimary(false);

        product.replaceImages(List.of(first, duplicatedSortOrder));

        DataIntegrityViolationException duplicateSortOrderViolation = assertThrows(DataIntegrityViolationException.class,
                () -> productRepository.saveAndFlush(product));

        assertTrue(duplicateSortOrderViolation.getMessage().toLowerCase().contains("unique"));
    }
}
