package com.eltano.ecommerce.inventory.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.catalog.repository.ProductRepository;

@Service
public class InventoryExportService {
    private static final List<String> HEADERS = List.of("categoría", "producto", "variante/SKU", "tipo",
            "stock total", "reservado", "disponible", "unidad", "último costo unitario", "fecha último costo");
    private static final Comparator<String> TEXT_ORDER = String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());
    private final ProductRepository products;

    public InventoryExportService(ProductRepository products) { this.products = products; }

    @Transactional(readOnly = true)
    public byte[] export() {
        List<ExportRow> rows = rows(products.findAllInventoryExportTargets());
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Inventario");
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy hh:mm:ss"));
            Row header = sheet.createRow(0);
            for (int index = 0; index < HEADERS.size(); index++) text(header, index, HEADERS.get(index));
            for (int index = 0; index < rows.size(); index++) write(sheet.createRow(index + 1), rows.get(index), dateStyle);
            for (int index = 0; index < HEADERS.size(); index++) sheet.autoSizeColumn(index);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo generar la exportación de inventario.", exception);
        }
    }

    private List<ExportRow> rows(List<Product> source) {
        List<ExportRow> result = new ArrayList<>();
        source.stream().sorted(Comparator.comparing((Product product) -> product.getCategory().getName(), TEXT_ORDER)
                .thenComparing(Product::getName, TEXT_ORDER).thenComparing(product -> product.getId().toString())).forEach(product -> {
            if (product.getInventoryPolicy() == InventoryPolicy.BULK_WEIGHT) {
                BigDecimal total = kilograms(product.getStockBaseGrams());
                BigDecimal reserved = kilograms(product.getStockReservedBaseGrams());
                result.add(new ExportRow(product.getCategory().getName(), product.getName(), "", "Granel", total,
                        reserved, total.subtract(reserved), "kg", product.getLatestUnitCost(), product.getLatestCostAt()));
                return;
            }
            product.getVariants().stream().sorted(Comparator.comparing(ProductVariant::getSku, TEXT_ORDER)
                    .thenComparing(variant -> variant.getId().toString())).forEach(variant -> {
                BigDecimal available = BigDecimal.valueOf(variant.getStockAvailable());
                BigDecimal reserved = BigDecimal.valueOf(variant.getStockReserved());
                result.add(new ExportRow(product.getCategory().getName(), product.getName(), variant.getSku(), "Variante",
                        available.add(reserved), reserved, available, "unidad", variant.getLatestUnitCost(), variant.getLatestCostAt()));
            });
        });
        return List.copyOf(result);
    }

    private void write(Row row, ExportRow value, CellStyle dateStyle) {
        text(row, 0, value.category()); text(row, 1, value.product()); text(row, 2, value.variant()); text(row, 3, value.type());
        number(row, 4, value.total()); number(row, 5, value.reserved()); number(row, 6, value.available()); text(row, 7, value.unit());
        if (value.latestCost() == null) row.createCell(8, CellType.BLANK); else number(row, 8, value.latestCost());
        if (value.latestCostAt() == null) row.createCell(9, CellType.BLANK);
        else { var cell = row.createCell(9, CellType.NUMERIC); cell.setCellValue(Date.from(value.latestCostAt())); cell.setCellStyle(dateStyle); }
    }

    private void text(Row row, int index, String value) { row.createCell(index, CellType.STRING).setCellValue(value); }
    private void number(Row row, int index, BigDecimal value) { row.createCell(index, CellType.NUMERIC).setCellValue(value.doubleValue()); }
    private BigDecimal kilograms(Integer grams) { return BigDecimal.valueOf(grams == null ? 0L : grams.longValue(), 3); }

    private record ExportRow(String category, String product, String variant, String type, BigDecimal total,
            BigDecimal reserved, BigDecimal available, String unit, BigDecimal latestCost, Instant latestCostAt) { }
}
