package io.github.kang30984.minierp.order;

import io.github.kang30984.minierp.auth.LoginUser;
import io.github.kang30984.minierp.common.PageParams;
import io.github.kang30984.minierp.common.PageResponse;
import jakarta.validation.Valid;
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
@RequestMapping("/api/market/orders")
public class MarketOrderController {

    private final OrderService orderService;

    public MarketOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderDetailResponse> create(@AuthenticationPrincipal LoginUser loginUser,
                                                      @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.create(loginUser.getUser(), request));
    }

    @GetMapping
    public PageResponse<OrderSummaryResponse> myOrders(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        return orderService.myOrders(loginUser.getUser(), status, PageParams.of(page, size));
    }

    @GetMapping("/{id}")
    public OrderDetailResponse myOrder(@AuthenticationPrincipal LoginUser loginUser,
                                       @PathVariable("id") long id) {
        return orderService.myOrder(loginUser.getUser(), id);
    }

    @PostMapping("/{id}/cancel")
    public OrderDetailResponse cancel(@AuthenticationPrincipal LoginUser loginUser,
                                      @PathVariable("id") long id) {
        return orderService.cancelMyOrder(loginUser.getUser(), id);
    }
}
