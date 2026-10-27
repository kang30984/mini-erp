package io.github.kang30984.minierp.master.product;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductMapper {

    long count(@Param("keyword") String keyword,
               @Param("category") String category,
               @Param("status") String status);

    List<ProductResponse> search(@Param("keyword") String keyword,
                                 @Param("category") String category,
                                 @Param("status") String status,
                                 @Param("limit") int limit,
                                 @Param("offset") int offset);

    Optional<ProductResponse> findById(@Param("id") long id);

    Optional<ProductResponse> findBySku(@Param("sku") String sku);

    List<ProductResponse> findByIds(@Param("ids") Collection<Long> ids);

    boolean existsBySku(@Param("sku") String sku);

    void insert(@Param("sku") String sku,
                @Param("name") String name,
                @Param("category") String category,
                @Param("unitPrice") BigDecimal unitPrice,
                @Param("stockQty") int stockQty,
                @Param("status") String status);

    /** 재고가 충분할 때만 차감한다. 영향받은 행이 0이면 재고 부족 */
    int decreaseStock(@Param("productId") long productId, @Param("qty") int qty);

    int increaseStock(@Param("productId") long productId, @Param("qty") int qty);

    int update(@Param("id") long id,
               @Param("name") String name,
               @Param("category") String category,
               @Param("unitPrice") BigDecimal unitPrice,
               @Param("stockQty") int stockQty,
               @Param("status") String status);
}
