package com.eltano.ecommerce.procurement.draft.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.eltano.ecommerce.procurement.draft.domain.PurchaseDraftMatchStatus;
import com.eltano.ecommerce.procurement.draft.domain.PurchaseDraftUnit;

@Component
public class PurchaseWorkbookParser {
    public static final long MAX_FILE_SIZE = 5L * 1024L * 1024L;
    public static final int MAX_ROWS = 1_000;
    private static final Set<String> MIME_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream");
    private static final List<String> HEADERS = List.of("fecha", "producto", "cantidad", "unidad");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter LOCAL_DATE = DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT);

    static {
        ZipSecureFile.setMinInflateRatio(0.01d);
        ZipSecureFile.setMaxEntrySize(20L * 1024L * 1024L);
        ZipSecureFile.setMaxTextSize(5L * 1024L * 1024L);
    }

    private final SupplierProductNameNormalizer normalizer;
    private final DataFormatter formatter = new DataFormatter(new Locale("es", "AR"), false);

    public PurchaseWorkbookParser(SupplierProductNameNormalizer normalizer) { this.normalizer = normalizer; }

    public ParsedWorkbook parse(MultipartFile file) {
        validateUpload(file);
        try { return parse(file.getBytes()); }
        catch (IOException exception) { throw invalid("No se pudo leer el archivo XLSX."); }
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
        } catch (PurchaseDraftException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("El contenido no es un libro XLSX valido, no cifrado y seguro.");
        }
    }

    private ParsedWorkbook parseSheet(Sheet sheet) {
        Row header = sheet.getRow(sheet.getFirstRowNum());
        Map<String, Integer> columns = headers(header);
        List<ParsedLine> lines = new ArrayList<>();
        for (int index = header.getRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null || rowEmpty(row)) continue;
            if (lines.size() == MAX_ROWS) throw invalid("El archivo supera el maximo de 1000 filas de datos.");
            lines.add(parseLine(row, columns));
        }
        if (lines.isEmpty()) throw invalid("El archivo no contiene filas de compra.");
        markMixedDates(lines);
        markDuplicates(lines);
        LocalDate date = lines.stream().map(ParsedLine::date).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        return new ParsedWorkbook(date, List.copyOf(lines));
    }

    private ParsedLine parseLine(Row row, Map<String, Integer> columns) {
        Cell dateCell = row.getCell(columns.get("fecha"));
        String dateSource = text(dateCell);
        String product = text(row.getCell(columns.get("producto")));
        Cell quantityCell = row.getCell(columns.get("cantidad"));
        String quantitySource = text(quantityCell);
        String unitSource = text(row.getCell(columns.get("unidad"))).toLowerCase(Locale.ROOT);
        List<String> errors = new ArrayList<>();
        LocalDate date = parseDate(dateCell, dateSource, errors);
        if (product.isBlank()) errors.add("El producto es obligatorio.");
        PurchaseDraftUnit unit = switch (unitSource) {
            case "kg" -> PurchaseDraftUnit.KG;
            case "unidad" -> PurchaseDraftUnit.UNIDAD;
            default -> { errors.add("La unidad debe ser kg o unidad."); yield null; }
        };
        BigDecimal quantity = parseQuantity(quantityCell, quantitySource, unit, errors);
        return new ParsedLine(row.getRowNum() + 1, dateSource, date, product, normalizer.normalize(product),
                quantitySource, quantity, unit, errors, errors.isEmpty() ? PurchaseDraftMatchStatus.UNRESOLVED : PurchaseDraftMatchStatus.INVALID);
    }

    private LocalDate parseDate(Cell cell, String source, List<String> errors) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            try { return cell.getLocalDateTimeCellValue().toLocalDate(); }
            catch (RuntimeException ignored) { }
        }
        for (DateTimeFormatter candidate : List.of(ISO_DATE, LOCAL_DATE)) {
            try { return LocalDate.parse(source, candidate); }
            catch (DateTimeParseException ignored) { }
        }
        errors.add("La fecha debe ser una fecha de Excel, yyyy-MM-dd o d/M/yyyy.");
        return null;
    }

    private BigDecimal parseQuantity(Cell cell, String source, PurchaseDraftUnit unit, List<String> errors) {
        if (unit == null) return null;
        BigDecimal value = null;
        try {
            if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                String raw = cell instanceof org.apache.poi.xssf.usermodel.XSSFCell xssf ? xssf.getRawValue() : source;
                if (raw == null || raw.toLowerCase(Locale.ROOT).contains("e")) throw new NumberFormatException();
                value = new BigDecimal(raw);
            } else {
                String pattern = unit == PurchaseDraftUnit.KG ? "[0-9]+(?:\\.[0-9]{1,3})?" : "[0-9]+";
                if (!source.matches(pattern)) throw new NumberFormatException();
                value = new BigDecimal(source);
            }
            if (value.signum() <= 0) throw new NumberFormatException();
            if (unit == PurchaseDraftUnit.KG) {
                if (Math.max(0, value.stripTrailingZeros().scale()) > 3) throw new ArithmeticException();
                value.multiply(BigDecimal.valueOf(1000)).setScale(0, RoundingMode.UNNECESSARY).intValueExact();
            } else {
                value.setScale(0, RoundingMode.UNNECESSARY).intValueExact();
            }
            return value;
        } catch (NumberFormatException | ArithmeticException exception) {
            errors.add(unit == PurchaseDraftUnit.KG
                    ? "La cantidad en kg debe ser positiva, sin separadores ni notacion cientifica, y tener hasta 3 decimales."
                    : "La cantidad en unidad debe ser un entero positivo sin separadores ni notacion cientifica.");
            return null;
        }
    }

    private void markMixedDates(List<ParsedLine> lines) {
        Set<LocalDate> dates = new HashSet<>();
        lines.stream().map(ParsedLine::date).filter(java.util.Objects::nonNull).forEach(dates::add);
        if (dates.size() > 1) lines.forEach(line -> line.addError("Todas las filas deben tener la misma fecha."));
    }

    private void markDuplicates(List<ParsedLine> lines) {
        Map<String, List<ParsedLine>> groups = new HashMap<>();
        lines.stream().filter(line -> line.date() != null && line.quantity() != null && line.unit() != null)
                .forEach(line -> groups.computeIfAbsent(line.normalizedProductName() + "|" + line.date() + "|"
                        + line.quantity().stripTrailingZeros().toPlainString() + "|" + line.unit(), ignored -> new ArrayList<>()).add(line));
        groups.values().stream().filter(group -> group.size() > 1).flatMap(List::stream)
                .forEach(line -> line.addError("La fila esta duplicada exactamente dentro del archivo."));
    }

    private Map<String, Integer> headers(Row row) {
        if (row == null) throw invalid("Falta la fila de encabezados.");
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int column = 0; column < row.getLastCellNum(); column++) {
            String value = text(row.getCell(column)).trim().toLowerCase(Locale.ROOT);
            if (value.isBlank()) continue;
            if (result.putIfAbsent(value, column) != null) throw invalid("Hay encabezados duplicados: " + value + ".");
        }
        List<String> missing = HEADERS.stream().filter(required -> !result.containsKey(required)).toList();
        if (!missing.isEmpty()) throw invalid("Faltan encabezados obligatorios: " + String.join(", ", missing) + ".");
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
    private boolean zipSignature(byte[] value) { return value.length >= 4 && value[0] == 'P' && value[1] == 'K'
            && ((value[2] == 3 && value[3] == 4) || (value[2] == 5 && value[3] == 6) || (value[2] == 7 && value[3] == 8)); }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("Debe adjuntar un archivo XLSX.");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".xlsx") || !MIME_TYPES.contains(mime)) throw invalid("Solo se aceptan archivos .xlsx.");
        if (file.getSize() > MAX_FILE_SIZE) throw invalid("El archivo supera el maximo de 5 MiB.");
    }

    private PurchaseDraftException invalid(String message) {
        return new PurchaseDraftException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_XLSX", message);
    }

    public record ParsedWorkbook(LocalDate purchaseDate, List<ParsedLine> lines) { }
    public static final class ParsedLine {
        private final int rowNumber;
        private final String sourceDate;
        private final LocalDate date;
        private final String productName;
        private final String normalizedProductName;
        private final String sourceQuantity;
        private final BigDecimal quantity;
        private final PurchaseDraftUnit unit;
        private final List<String> errors;
        private PurchaseDraftMatchStatus status;

        ParsedLine(int rowNumber, String sourceDate, LocalDate date, String productName, String normalizedProductName,
                String sourceQuantity, BigDecimal quantity, PurchaseDraftUnit unit, List<String> errors, PurchaseDraftMatchStatus status) {
            this.rowNumber = rowNumber; this.sourceDate = sourceDate; this.date = date; this.productName = productName;
            this.normalizedProductName = normalizedProductName; this.sourceQuantity = sourceQuantity; this.quantity = quantity;
            this.unit = unit; this.errors = errors; this.status = status;
        }
        public int rowNumber() { return rowNumber; }
        public String sourceDate() { return sourceDate; }
        public LocalDate date() { return date; }
        public String productName() { return productName; }
        public String normalizedProductName() { return normalizedProductName; }
        public String sourceQuantity() { return sourceQuantity; }
        public BigDecimal quantity() { return quantity; }
        public PurchaseDraftUnit unit() { return unit; }
        public List<String> errors() { return List.copyOf(errors); }
        public PurchaseDraftMatchStatus status() { return status; }
        void addError(String error) { if (!errors.contains(error)) errors.add(error); status = PurchaseDraftMatchStatus.INVALID; }
    }
}
