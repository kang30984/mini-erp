package io.github.kang30984.minierp.market;

import io.github.kang30984.minierp.common.PageParams;
import io.github.kang30984.minierp.common.PageResponse;
import io.github.kang30984.minierp.master.product.ProductResponse;
import io.github.kang30984.minierp.master.product.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 거래처용 상품 목록. 판매 중인 상품만 보여 준다.
 */
@RestController
@RequestMapping("/api/market/products")
public class MarketProductController {

    private final ProductService productService;

    public MarketProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public PageResponse<ProductResponse> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        return productService.search(keyword, category, "ON_SALE", PageParams.of(page, size));
    }
}
