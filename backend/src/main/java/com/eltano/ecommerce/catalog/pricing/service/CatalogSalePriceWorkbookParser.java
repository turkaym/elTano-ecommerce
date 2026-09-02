package com.eltano.ecommerce.catalog.pricing.service;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.eltano.ecommerce.common.api.UnprocessableEntityException;

@Component
public class CatalogSalePriceWorkbookParser {
    public static final long MAX_FILE_SIZE = 5L * 1024L * 1024L;
    public static final int MAX_ROWS = 2_000;
    private static final Set<String> MIME_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream");
    private static final List<String> HEADERS = List.of(
            "tipo_clave", "clave", "producto", "presentacion", "precio_actual", "precio_nuevo");
    private final DataFormatter formatter = new DataFormatter(new Locale("es", "AR"), false);

    static {
        ZipSecureFile.setMinInflateRatio(0.01d);
        ZipSecureFile.setMaxEntrySize(20L * 1024L * 1024L);
        ZipSecureFile.setMaxTextSize(5L * 1024L * 1024L);
    }

    public ParsedWorkbook parse(MultipartFile file) {
        validateUpload(file);
        try { return parse(file.getBytes()); }
        catch (UnprocessableEntityException exception) { throw exception; }
        catch (Exception exception) { throw invalid("No se pudo leer el archivo XLSX."); }
    }

    public ParsedWorkbook parse(byte[] content) {
        if (content == null || content.length == 0 || content.length > MAX_FILE_SIZE || !zipSignature(content)) {
            throw invalid("El archivo debe ser un XLSX valido de hasta 5 MiB.");
        }
        try (OPCPackage packageFile = OPCPackage.open(new ByteArrayInputStream(content));
                XSSFWorkbook workbook = new XSSFWorkbook(packageFile)) {
            if (workbook.isMacroEnabled()) throw invalid("Los archivos XLSM con macros no estan permitidos.");
            if (!workbook.getExternalLinksTable().isEmpty()) throw invalid("El archivo no puede contener vinculos externos.");
            rejectFormulas(workbook);
            List<Sheet> nonEmpty = new ArrayList<>();
            for (Sheet sheet : workbook) if (!sheetEmpty(sheet)) nonEmpty.add(sheet);
            if (nonEmpty.size() != 1) throw invalid("El archivo debe contener una sola hoja no vacia.");
            return parseSheet(nonEmpty.get(0));
        } catch (UnprocessableEntityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("El contenido no es un libro XLSX valido, no cifrado y seguro.");
        }
    }

    private ParsedWorkbook parseSheet(Sheet sheet) {
        Row header = sheet.getRow(sheet.getFirstRowNum());
        Map<String, Integer> columns = headers(header);
        List<ParsedRow> rows = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (int index = header.getRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null || rowEmpty(row)) continue;
            if (rows.size() == MAX_ROWS) throw invalid("El archivo supera el maximo de 2000 filas de datos.");
            List<String> errors = new ArrayList<>();
            String type = text(row.getCell(columns.get("tipo_clave"))).toUpperCase(Locale.ROOT);
            String key = text(row.getCell(columns.get("clave")));
            String product = text(row.getCell(columns.get("producto")));
            String presentation = text(row.getCell(columns.get("presentacion")));
            BigDecimal oldPrice = price(row.getCell(columns.get("precio_actual")), "precio_actual", errors);
            BigDecimal newPrice = price(row.getCell(columns.get("precio_nuevo")), "precio_nuevo", errors);
            if (!type.equals("SKU") && !type.equals("PRODUCTO_GRANEL")) errors.add("El tipo de clave no es compatible.");
            if (key.isBlank()) errors.add("La clave es obligatoria.");
            String normalizedKey = type + "|" + key.toLowerCase(Locale.ROOT);
            if (!key.isBlank() && !keys.add(normalizedKey)) errors.add("La clave esta duplicada dentro del archivo.");
            rows.add(new ParsedRow(row.getRowNum() + 1, type, key, product, presentation, oldPrice, newPrice, List.copyOf(errors)));
        }
        if (rows.isEmpty()) throw invalid("El archivo no contiene filas de precios.");
        return new ParsedWorkbook(List.copyOf(rows));
    }

    private BigDecimal price(Cell cell, String field, List<String> errors) {
        String source = sourceNumber(cell);
        try {
            if (!source.matches("[0-9]+(?:\\.[0-9]{1,2})?")) throw new NumberFormatException();
            BigDecimal value = new BigDecimal(source).setScale(2, RoundingMode.UNNECESSARY);
            if (value.signum() <= 0 || value.precision() > 12) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException | ArithmeticException exception) {
            errors.add(field + " debe ser positivo, tener hasta 2 decimales y no usar formulas, simbolos, separadores ni notacion cientifica.");
            return null;
        }
    }

    private Map<String, Integer> headers(Row row) {
        if (row == null) throw invalid("Falta la fila de encabezados.");
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int column = 0; column < row.getLastCellNum(); column++) {
            String value = text(row.getCell(column)).toLowerCase(Locale.ROOT);
            if (value.isBlank()) continue;
            if (result.putIfAbsent(value, column) != null) throw invalid("Hay encabezados duplicados: " + value + ".");
        }
        if (!List.copyOf(result.keySet()).equals(HEADERS)) {
            throw invalid("Los encabezados deben ser exactamente: " + String.join(" | ", HEADERS) + ".");
        }
        return result;
    }

    private void rejectFormulas(XSSFWorkbook workbook) {
        for (Sheet sheet : workbook) for (Row row : sheet) for (Cell cell : row) {
            if (cell.getCellType() == CellType.FORMULA) throw invalid("El archivo no puede contener formulas.");
        }
    }

    private boolean sheetEmpty(Sheet sheet) { for (Row row : sheet) if (!rowEmpty(row)) return false; return true; }
    private boolean rowEmpty(Row row) { for (Cell cell : row) if (!text(cell).isBlank()) return false; return true; }
    private String text(Cell cell) { return cell == null ? "" : formatter.formatCellValue(cell).trim(); }
    private String sourceNumber(Cell cell) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && cell instanceof XSSFCell xssf) {
            return xssf.getRawValue() == null ? "" : xssf.getRawValue().trim();
        }
        return text(cell);
    }
    private boolean zipSignature(byte[] value) { return value.length >= 4 && value[0] == 'P' && value[1] == 'K'
            && ((value[2] == 3 && value[3] == 4) || (value[2] == 5 && value[3] == 6) || (value[2] == 7 && value[3] == 8)); }
    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("Debe adjuntar un archivo XLSX.");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".xlsx") || !MIME_TYPES.contains(mime)) throw invalid("Solo se aceptan archivos .xlsx.");
        if (file.getSize() > MAX_FILE_SIZE) throw invalid("El archivo supera el maximo de 5 MiB.");
    }
    private UnprocessableEntityException invalid(String message) { return new UnprocessableEntityException(message); }

    public record ParsedWorkbook(List<ParsedRow> rows) { }
    public record ParsedRow(int rowNumber, String keyType, String key, String productName, String presentation,
            BigDecimal oldPrice, BigDecimal newPrice, List<String> errors) { }
}
