package io.github.kang30984.minierp.order.admin;

import io.github.kang30984.minierp.auth.LoginUser;
import io.github.kang30984.minierp.common.PageParams;
import io.github.kang30984.minierp.common.PageResponse;
import io.github.kang30984.minierp.order.OrderDetailResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final AdminOrderService service;

    public AdminOrderController(AdminOrderService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AdminOrderSummary> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        return service.search(keyword, status, from, to, PageParams.of(page, size));
    }

    @GetMapping("/status-counts")
    public Map<String, Long> statusCounts() {
        return service.statusCounts();
    }

    @GetMapping("/{id}")
    public OrderDetailResponse get(@PathVariable("id") long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/approve")
    public OrderDetailResponse approve(@AuthenticationPrincipal LoginUser loginUser, @PathVariable("id") long id,
                                       @Valid @RequestBody OrderActionRequest request) {
        return service.approve(loginUser.getUser(), id, request);
    }

    @PostMapping("/{id}/reject")
    public OrderDetailResponse reject(@AuthenticationPrincipal LoginUser loginUser, @PathVariable("id") long id,
                                      @Valid @RequestBody OrderActionRequest request) {
        return service.reject(loginUser.getUser(), id, request);
    }

    @PostMapping("/{id}/ship")
    public OrderDetailResponse ship(@AuthenticationPrincipal LoginUser loginUser, @PathVariable("id") long id,
                                    @Valid @RequestBody OrderActionRequest request) {
        return service.ship(loginUser.getUser(), id, request);
    }

    @PostMapping("/{id}/cancel")
    public OrderDetailResponse cancel(@AuthenticationPrincipal LoginUser loginUser, @PathVariable("id") long id,
                                      @Valid @RequestBody OrderActionRequest request) {
        return service.cancel(loginUser.getUser(), id, request);
    }

    @PostMapping("/{id}/returns")
    public ResponseEntity<OrderDetailResponse> registerReturn(@AuthenticationPrincipal LoginUser loginUser,
                                                              @PathVariable("id") long id,
                                                              @Valid @RequestBody ReturnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registerReturn(loginUser.getUser(), id, request));
    }
}
