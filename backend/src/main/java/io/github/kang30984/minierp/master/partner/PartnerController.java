package io.github.kang30984.minierp.master.partner;

import io.github.kang30984.minierp.common.PageParams;
import io.github.kang30984.minierp.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/partners")
public class PartnerController {

    private final PartnerService partnerService;

    public PartnerController(PartnerService partnerService) {
        this.partnerService = partnerService;
    }

    @GetMapping
    public PageResponse<PartnerResponse> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "gradeCode", required = false) String gradeCode,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        return partnerService.search(keyword, gradeCode, status, PageParams.of(page, size));
    }

    @GetMapping("/{id}")
    public PartnerResponse get(@PathVariable("id") long id) {
        return partnerService.get(id);
    }

    @PostMapping
    public ResponseEntity<PartnerResponse> create(@Valid @RequestBody PartnerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(partnerService.create(request));
    }

    @PutMapping("/{id}")
    public PartnerResponse update(@PathVariable("id") long id, @Valid @RequestBody PartnerRequest request) {
        return partnerService.update(id, request);
    }
}
