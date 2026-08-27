package com.eltano.ecommerce.procurement.api;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eltano.ecommerce.common.api.CorrelationIdFilter;
import com.eltano.ecommerce.procurement.domain.PurchaseStatus;
import com.eltano.ecommerce.procurement.service.ProcurementService;
import com.eltano.ecommerce.procurement.service.ProcurementService.CorrectionCommand;
import com.eltano.ecommerce.procurement.service.ProcurementService.MappingCommand;
import com.eltano.ecommerce.procurement.service.ProcurementService.MappingResponse;
import com.eltano.ecommerce.procurement.service.ProcurementService.PurchaseCommand;
import com.eltano.ecommerce.procurement.service.ProcurementService.PurchaseResponse;
import com.eltano.ecommerce.procurement.service.ProcurementService.ReasonCommand;
import com.eltano.ecommerce.procurement.service.ProcurementService.ReceiptCommand;
import com.eltano.ecommerce.procurement.service.ProcurementService.ReceiptResponse;
import com.eltano.ecommerce.procurement.service.ProcurementService.SupplierCommand;
import com.eltano.ecommerce.procurement.service.ProcurementService.SupplierResponse;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/procurement")
public class AdminProcurementController {
    private final ProcurementService service;
    public AdminProcurementController(ProcurementService service) { this.service = service; }

    @GetMapping("/suppliers")
    public List<SupplierResponse> suppliers() { return service.listSuppliers(); }
    @PostMapping("/suppliers")
    public ResponseEntity<SupplierResponse> createSupplier(@RequestBody SupplierCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createSupplier(command));
    }
    @PatchMapping("/suppliers/{id}")
    public SupplierResponse updateSupplier(@PathVariable UUID id, @RequestBody SupplierCommand command) { return service.updateSupplier(id, command); }

    @GetMapping("/mappings")
    public List<MappingResponse> mappings(@RequestParam(required = false) UUID supplierId) { return service.listMappings(supplierId); }
    @PostMapping("/mappings")
    public ResponseEntity<MappingResponse> createMapping(@RequestBody MappingCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createMapping(command));
    }
    @PatchMapping("/mappings/{id}")
    public MappingResponse updateMapping(@PathVariable UUID id, @RequestBody MappingCommand command) { return service.updateMapping(id, command); }

    @GetMapping("/purchases")
    public List<PurchaseResponse> purchases(@RequestParam(required = false) PurchaseStatus status,
            @RequestParam(required = false) UUID supplierId) { return service.listPurchases(status, supplierId); }
    @GetMapping("/purchases/{id}")
    public PurchaseResponse purchase(@PathVariable UUID id) { return service.getPurchase(id); }
    @PostMapping("/purchases")
    public ResponseEntity<PurchaseResponse> createPurchase(@RequestBody PurchaseCommand command, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createPurchase(command, principal.getName()));
    }
    @PutMapping("/purchases/{id}")
    public PurchaseResponse updatePurchase(@PathVariable UUID id, @RequestBody PurchaseCommand command) { return service.updatePurchase(id, command); }

    @PostMapping("/purchases/{id}/receipts/preview")
    public ReceiptResponse preview(@PathVariable UUID id, @RequestBody ReceiptCommand command) { return service.preview(id, command); }
    @PostMapping("/purchases/{id}/receipts/confirm")
    public ReceiptResponse confirm(@PathVariable UUID id, @RequestBody ReceiptCommand command,
            @RequestHeader("Idempotency-Key") String key, Principal principal, HttpServletRequest request) {
        return service.confirm(id, command, key, principal.getName(), correlation(request));
    }
    @PostMapping("/purchases/{id}/corrections")
    public ReceiptResponse correct(@PathVariable UUID id, @RequestBody CorrectionCommand command,
            @RequestHeader("Idempotency-Key") String key, Principal principal, HttpServletRequest request) {
        return service.correct(id, command, key, principal.getName(), correlation(request));
    }
    @PostMapping("/purchases/{id}/cancel")
    public ReceiptResponse cancel(@PathVariable UUID id, @RequestBody ReasonCommand command,
            @RequestHeader("Idempotency-Key") String key, Principal principal, HttpServletRequest request) {
        return service.cancel(id, command, key, principal.getName(), correlation(request));
    }

    private String correlation(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        return value == null ? UUID.randomUUID().toString() : value.toString();
    }
}
