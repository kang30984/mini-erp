package io.github.kang30984.minierp.master.product;

import io.github.kang30984.minierp.common.BusinessException;
import io.github.kang30984.minierp.common.PageParams;
import io.github.kang30984.minierp.common.PageResponse;
import io.github.kang30984.minierp.common.SearchText;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductMapper productMapper;

    public ProductService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    public PageResponse<ProductResponse> search(String keyword, String category, String status, PageParams page) {
        String pattern = SearchText.containsPattern(keyword);
        String categoryCond = SearchText.blankToNull(category);
        String statusCond = SearchText.blankToNull(status);

        long total = productMapper.count(pattern, categoryCond, statusCond);
        List<ProductResponse> items = total == 0
                ? List.of()
                : productMapper.search(pattern, categoryCond, statusCond, page.size(), page.offset());
        return PageResponse.of(items, page, total);
    }

    public ProductResponse get(long id) {
        return productMapper.findById(id)
                .orElseThrow(() -> BusinessException.notFound("상품을 찾을 수 없습니다."));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productMapper.existsBySku(request.sku())) {
            throw duplicateSku(request.sku());
        }
        try {
            productMapper.insert(request.sku(), request.name().strip(), request.category(),
                    request.unitPrice(), request.stockQty(), request.status());
        } catch (DuplicateKeyException e) {
            // 중복 확인과 등록 사이에 다른 사용자가 같은 코드를 먼저 등록한 경우
            throw duplicateSku(request.sku());
        }
        return productMapper.findBySku(request.sku()).orElseThrow();
    }

    @Transactional
    public ProductResponse update(long id, ProductRequest request) {
        int updated = productMapper.update(id, request.name().strip(), request.category(),
                request.unitPrice(), request.stockQty(), request.status());
        if (updated == 0) {
            throw BusinessException.notFound("상품을 찾을 수 없습니다.");
        }
        return get(id);
    }

    private BusinessException duplicateSku(String sku) {
        return BusinessException.conflict("DUPLICATE_SKU", "이미 등록된 상품 코드입니다: " + sku);
    }
}
