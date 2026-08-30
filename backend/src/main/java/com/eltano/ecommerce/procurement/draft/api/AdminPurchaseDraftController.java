package com.eltano.ecommerce.procurement.draft.api;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import org.springframework.web.multipart.MultipartFile;

import com.eltano.ecommerce.common.api.CorrelationIdFilter;
import com.eltano.ecommerce.procurement.draft.domain.PurchaseDraftUnit;
import com.eltano.ecommerce.procurement.draft.service.PurchaseDraftService;
import com.eltano.ecommerce.procurement.draft.service.PurchaseDraftProductMatcher.CatalogCandidate;
import com.eltano.ecommerce.procurement.draft.service.PurchaseDraftService.ConfirmCommand;
import com.eltano.ecommerce.procurement.draft.service.PurchaseDraftService.ConfirmResponse;
import com.eltano.ecommerce.procurement.draft.service.PurchaseDraftService.DraftResponse;
import com.eltano.ecommerce.procurement.draft.service.PurchaseDraftService.LineCommand;
import com.eltano.ecommerce.procurement.draft.service.PurchaseDraftService.ManualDraftCommand;
import com.eltano.ecommerce.procurement.draft.service.PurchaseDraftService.MatchCommand;
import com.eltano.ecommerce.procurement.draft.service.PurchaseDraftService.MetadataCommand;
import com.eltano.ecommerce.procurement.draft.service.PurchaseDraftService.PreviewResponse;
import com.eltano.ecommerce.procurement.draft.service.PurchaseDraftService.VersionCommand;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/procurement/purchase-drafts")
public class AdminPurchaseDraftController {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final PurchaseDraftService service;

    public AdminPurchaseDraftController(PurchaseDraftService service) { this.service = service; }

    @GetMapping("/template")
    public ResponseEntity<ByteArrayResource> template() {
        byte[] content = service.template();
        return ResponseEntity.ok().contentType(XLSX).contentLength(content.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("plantilla-compra.xlsx", StandardCharsets.UTF_8).build().toString())
                .body(new ByteArrayResource(content));
    }

    @PostMapping(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DraftResponse> importWorkbook(@RequestParam UUID supplierId, @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, Principal principal) {
        DraftResponse response = service.importWorkbook(supplierId, file, key, principal.getName());
        return ResponseEntity.status(response.reused() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }

    @PostMapping
    public ResponseEntity<DraftResponse> create(@RequestBody ManualDraftCommand command, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(command, principal.getName()));
    }

    @GetMapping
    public List<DraftResponse> list() { return service.list(); }
    @GetMapping("/{draftId}")
    public DraftResponse get(@PathVariable UUID draftId) { return service.get(draftId); }
    @PatchMapping("/{draftId}")
    public DraftResponse patch(@PathVariable UUID draftId, @RequestBody MetadataCommand command) { return service.patch(draftId, command); }
    @DeleteMapping("/{draftId}")
    public ResponseEntity<Void> delete(@PathVariable UUID draftId, @RequestBody VersionCommand command) { service.delete(draftId, command); return ResponseEntity.noContent().build(); }

    @PostMapping("/{draftId}/lines")
    public DraftResponse addLine(@PathVariable UUID draftId, @RequestBody LineCommand command) { return service.addLine(draftId, command); }
    @PatchMapping("/{draftId}/lines/{lineId}")
    public DraftResponse patchLine(@PathVariable UUID draftId, @PathVariable UUID lineId, @RequestBody LineCommand command) { return service.patchLine(draftId, lineId, command); }
    @DeleteMapping("/{draftId}/lines/{lineId}")
    public DraftResponse deleteLine(@PathVariable UUID draftId, @PathVariable UUID lineId, @RequestBody VersionCommand command) { return service.deleteLine(draftId, lineId, command); }

    @GetMapping("/catalog-candidates")
    public List<CatalogCandidate> candidates(@RequestParam(defaultValue = "") String q, @RequestParam PurchaseDraftUnit unit,
            @RequestParam(defaultValue = "20") int limit) { return service.candidates(q, unit, limit); }

    @PutMapping("/{draftId}/lines/{lineId}/match")
    public DraftResponse match(@PathVariable UUID draftId, @PathVariable UUID lineId, @RequestBody MatchCommand command) { return service.match(draftId, lineId, command); }
    @PostMapping("/{draftId}/preview")
    public PreviewResponse preview(@PathVariable UUID draftId, @RequestBody VersionCommand command) { return service.preview(draftId, command); }
    @PostMapping("/{draftId}/confirm")
    public ConfirmResponse confirm(@PathVariable UUID draftId, @RequestBody ConfirmCommand command,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, Principal principal, HttpServletRequest request) {
        return service.confirm(draftId, command, key, principal.getName(), correlation(request));
    }

    @GetMapping("/{draftId}/source-file")
    public ResponseEntity<org.springframework.core.io.Resource> sourceFile(@PathVariable UUID draftId) {
        var source = service.sourceFile(draftId);
        MediaType contentType;
        try { contentType = source.contentType() == null ? XLSX : MediaType.parseMediaType(source.contentType()); }
        catch (IllegalArgumentException exception) { contentType = XLSX; }
        return ResponseEntity.ok().contentType(contentType).contentLength(source.size() == null ? -1 : source.size())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(source.filename(), StandardCharsets.UTF_8).build().toString())
                .body(source.resource());
    }

    private String correlation(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        return value == null ? UUID.randomUUID().toString() : value.toString();
    }
}
