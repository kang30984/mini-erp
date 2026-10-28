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

    /**
     * 상품 수정. 재고는 화면이 불러온 값(baseStockQty)을 기준으로 비교 후 교체한다.
     * 재고를 바꾸지 않았으면 재고 칼럼을 건드리지 않고, 바꿨는데 그 사이 승인·취소·반품으로 재고가 달라졌으면
     * 덮어쓰지 않고 409로 알린다. 예전 숫자로 덮어쓰면 승인으로 줄어든 재고가 되살아나 초과 판매가 생긴다.
     */
    @Transactional
    public ProductResponse update(long id, ProductRequest request) {
        if (request.baseStockQty() == null) {
            throw BusinessException.badRequest("BASE_STOCK_REQUIRED",
                    "상품을 불러왔을 때의 재고 수량(baseStockQty)이 필요합니다. 화면을 새로 고친 뒤 다시 저장해 주세요.");
        }
        boolean stockChanged = !request.stockQty().equals(request.baseStockQty());
        int updated = productMapper.update(id, request.name().strip(), request.category(), request.unitPrice(),
                stockChanged, request.stockQty(), request.baseStockQty(), request.status());
        if (updated == 0) {
            ProductResponse current = get(id);
            throw BusinessException.conflict("STALE_STOCK",
                    "화면을 연 뒤 주문 처리로 재고가 %d개에서 %d개로 바뀌었습니다. 화면을 새로 고친 뒤 다시 입력해 주세요."
                            .formatted(request.baseStockQty(), current.stockQty()));
        }
        return get(id);
    }

    private BusinessException duplicateSku(String sku) {
        return BusinessException.conflict("DUPLICATE_SKU", "이미 등록된 상품 코드입니다: " + sku);
    }
}
