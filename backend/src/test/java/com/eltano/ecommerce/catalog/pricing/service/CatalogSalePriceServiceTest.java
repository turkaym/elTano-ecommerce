package com.eltano.ecommerce.catalog.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductType;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.catalog.domain.UnitType;
import com.eltano.ecommerce.catalog.pricing.domain.CatalogSalePricePreview;
import com.eltano.ecommerce.catalog.pricing.repository.CatalogSalePricePreviewRepository;
import com.eltano.ecommerce.catalog.repository.ProductRepository;
import com.eltano.ecommerce.catalog.repository.ProductVariantRepository;
import com.eltano.ecommerce.common.api.ConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;

class CatalogSalePriceServiceTest {
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final CatalogSalePricePreviewRepository previews = mock(CatalogSalePricePreviewRepository.class);
    private CatalogSalePriceService service;
    private Product product;
    private ProductVariant small;
    private ProductVariant large;

    @BeforeEach
    void setUp() {
        service = new CatalogSalePriceService(products, variants, previews,
                new CatalogSalePriceWorkbookParser(), new ObjectMapper().findAndRegisterModules());
        product = bulkProduct();
        small = variant(product, "NUEZ-250G", 250, "2500.00");
        large = variant(product, "NUEZ-1KG", 1000, "10000.00");
        product.addVariant(small);
        product.addVariant(large);
        when(products.findAllWithRelations()).thenReturn(List.of(product));
        when(previews.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void previewsAndConfirmsBulkPriceAtomicallyWhilePreservingIdsAndSkus() throws Exception {
        var previewResponse = service.preview(workbook("10000.00", "12000.50"), "admin");
        ArgumentCaptor<CatalogSalePricePreview> previewCaptor = ArgumentCaptor.forClass(CatalogSalePricePreview.class);
        verify(previews).save(previewCaptor.capture());
        var stored = previewCaptor.getValue();
        UUID smallId = small.getId();
        UUID largeId = large.getId();

        when(previews.findByIdForUpdate(previewResponse.previewId())).thenReturn(Optional.of(stored));
        when(previews.findByConfirmIdempotencyKey("confirm-1")).thenReturn(Optional.empty());
        when(products.findAllByIdInForUpdate(List.of(product.getId()))).thenReturn(List.of(product));
        when(variants.findIdsByProductId(product.getId())).thenReturn(List.of(smallId, largeId));
        when(variants.findAllByIdInForUpdate(List.of(smallId, largeId).stream().sorted().toList()))
                .thenReturn(List.of(small, large));

        var confirmed = service.confirm(previewResponse.previewId(),
                new CatalogSalePriceService.ConfirmCommand(previewResponse.previewHash()), "confirm-1");

        assertThat(confirmed.reused()).isFalse();
        assertThat(small.getPrice()).isEqualByComparingTo("3000.13");
        assertThat(large.getPrice()).isEqualByComparingTo("12000.50");
        assertThat(small.getId()).isEqualTo(smallId);
        assertThat(small.getSku()).isEqualTo("NUEZ-250G");

        assertThat(service.confirm(previewResponse.previewId(),
                new CatalogSalePriceService.ConfirmCommand(previewResponse.previewHash()), "confirm-1").reused()).isTrue();
    }

    @Test
    void rejectsConfirmationWhenAReviewedCatalogPriceChanged() throws Exception {
        var previewResponse = service.preview(workbook("10000.00", "12000.00"), "admin");
        ArgumentCaptor<CatalogSalePricePreview> previewCaptor = ArgumentCaptor.forClass(CatalogSalePricePreview.class);
        verify(previews).save(previewCaptor.capture());
        var stored = previewCaptor.getValue();
        when(previews.findByIdForUpdate(previewResponse.previewId())).thenReturn(Optional.of(stored));
        when(previews.findByConfirmIdempotencyKey("confirm-stale")).thenReturn(Optional.empty());
        when(products.findAllByIdInForUpdate(List.of(product.getId()))).thenReturn(List.of(product));
        when(variants.findIdsByProductId(product.getId())).thenReturn(List.of(small.getId(), large.getId()));
        when(variants.findAllByIdInForUpdate(List.of(small.getId(), large.getId()).stream().sorted().toList()))
                .thenReturn(List.of(small, large));
        small.setPrice(new BigDecimal("2600.00"));

        assertThatThrownBy(() -> service.confirm(previewResponse.previewId(),
                new CatalogSalePriceService.ConfirmCommand(previewResponse.previewHash()), "confirm-stale"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("catalogo cambio");
        assertThat(large.getPrice()).isEqualByComparingTo("10000.00");
    }

    @Test
    void invalidPreviewDoesNotCreateAConfirmableServerSnapshot() throws Exception {
        var response = service.preview(workbook("9999.00", "12000.00"), "admin");

        assertThat(response.valid()).isFalse();
        assertThat(response.previewId()).isNull();
        assertThat(response.rows().get(0).errors()).contains("El precio actual por kilogramo no coincide con el catalogo.");
    }

    @Test
    void usesResolvedCatalogLabelsInsteadOfWorkbookLabelsForSkuPreview() throws Exception {
        Product packaged = packagedProduct();
        ProductVariant variant = variant(packaged, "ALM-250G", 250, "30.86");
        variant.setUnitLabel("250g catalogo");
        packaged.addVariant(variant);
        when(products.findAllWithRelations()).thenReturn(List.of(packaged));

        var response = service.preview(skuWorkbook(packaged, variant, "Producto falso", "Presentacion falsa"), "admin");

        assertThat(response.valid()).isTrue();
        assertThat(response.rows().get(0).productName()).isEqualTo("Almendra");
        assertThat(response.rows().get(0).presentation()).isEqualTo("250g catalogo");
    }

    @Test
    void rejectsBulkPriceWhenAnyRoundedPresentationPriceWouldBeZero() throws Exception {
        var response = service.preview(workbook("10000.00", "0.01"), "admin");

        assertThat(response.valid()).isFalse();
        assertThat(response.rows().get(0).errors())
                .contains("El precio por kilogramo produce una presentacion con precio no positivo.");
    }

    @Test
    void infersUniqueTwoDecimalKgPriceFromRounded250gAnd500gPrices() {
        small.setPrice(new BigDecimal("30.86"));
        large.setWeightGrams(500);
        large.setUnitLabel("500g");
        large.setPrice(new BigDecimal("61.73"));

        var parsed = new CatalogSalePriceWorkbookParser().parse(service.template());

        assertThat(parsed.rows().get(0).oldPrice()).isEqualByComparingTo("123.45");
    }

    @Test
    void refusesToEmitTemplateBeyondParserRowLimit() {
        Product packaged = packagedProduct();
        for (int index = 0; index <= CatalogSalePriceWorkbookParser.MAX_ROWS; index++) {
            ProductVariant variant = variant(packaged, "SKU-" + index, 1, "1.00");
            packaged.addVariant(variant);
        }
        when(products.findAllWithRelations()).thenReturn(List.of(packaged));

        assertThatThrownBy(service::template).isInstanceOf(ConflictException.class)
                .hasMessageContaining("maximo de 2000 filas");
    }

    @Test
    void rejectsConfirmationWhenTheBulkPresentationSetChanged() throws Exception {
        var previewResponse = service.preview(workbook("10000.00", "12000.00"), "admin");
        ArgumentCaptor<CatalogSalePricePreview> previewCaptor = ArgumentCaptor.forClass(CatalogSalePricePreview.class);
        verify(previews).save(previewCaptor.capture());
        var stored = previewCaptor.getValue();
        ProductVariant added = variant(product, "NUEZ-500G", 500, "5000.00");
        product.addVariant(added);
        when(previews.findByIdForUpdate(previewResponse.previewId())).thenReturn(Optional.of(stored));
        when(previews.findByConfirmIdempotencyKey("confirm-new-presentation")).thenReturn(Optional.empty());
        when(products.findAllByIdInForUpdate(List.of(product.getId()))).thenReturn(List.of(product));
        List<UUID> allIds = List.of(small.getId(), large.getId(), added.getId()).stream().sorted().toList();
        when(variants.findIdsByProductId(product.getId())).thenReturn(allIds);
        when(variants.findAllByIdInForUpdate(allIds)).thenReturn(List.of(small, large, added));

        assertThatThrownBy(() -> service.confirm(previewResponse.previewId(),
                new CatalogSalePriceService.ConfirmCommand(previewResponse.previewHash()), "confirm-new-presentation"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("catalogo cambio");
    }

    private Product bulkProduct() {
        Product value = new Product();
        ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
        value.setName("Nuez");
        value.setActive(true);
        value.setProductType(ProductType.GRANEL);
        value.setInventoryPolicy(InventoryPolicy.BULK_WEIGHT);
        return value;
    }

    private Product packagedProduct() {
        Product value = new Product();
        ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
        value.setName("Almendra");
        value.setActive(true);
        value.setProductType(ProductType.ENVASADO);
        value.setInventoryPolicy(InventoryPolicy.PER_VARIANT);
        return value;
    }

    private ProductVariant variant(Product owner, String sku, int grams, String price) {
        ProductVariant value = new ProductVariant();
        ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
        value.setProduct(owner);
        value.setSku(sku);
        value.setUnitType(UnitType.WEIGHT);
        value.setWeightGrams(grams);
        value.setUnitLabel(grams == 1000 ? "1kg" : grams + "g");
        value.setPrice(new BigDecimal(price));
        value.setActive(true);
        return value;
    }

    private MockMultipartFile workbook(String oldPrice, String newPrice) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Precios");
            var header = sheet.createRow(0);
            String[] headers = { "tipo_clave", "clave", "producto", "presentacion", "precio_actual", "precio_nuevo" };
            for (int column = 0; column < headers.length; column++) header.createCell(column).setCellValue(headers[column]);
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("PRODUCTO_GRANEL");
            row.createCell(1).setCellValue(product.getId().toString());
            row.createCell(2).setCellValue("Nuez");
            row.createCell(3).setCellValue("Precio por kg");
            row.createCell(4).setCellValue(oldPrice);
            row.createCell(5).setCellValue(newPrice);
            workbook.write(output);
            return new MockMultipartFile("file", "precios.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    private MockMultipartFile skuWorkbook(Product owner, ProductVariant variant, String productName,
            String presentation) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Precios");
            var header = sheet.createRow(0);
            String[] headers = { "tipo_clave", "clave", "producto", "presentacion", "precio_actual", "precio_nuevo" };
            for (int column = 0; column < headers.length; column++) header.createCell(column).setCellValue(headers[column]);
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("SKU");
            row.createCell(1).setCellValue(variant.getSku());
            row.createCell(2).setCellValue(productName);
            row.createCell(3).setCellValue(presentation);
            row.createCell(4).setCellValue(variant.getPrice().toPlainString());
            row.createCell(5).setCellValue("35.00");
            workbook.write(output);
            return new MockMultipartFile("file", owner.getName() + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}
