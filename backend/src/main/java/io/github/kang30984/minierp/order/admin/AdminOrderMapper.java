package io.github.kang30984.minierp.order.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminOrderMapper {

    long count(@Param("keyword") String keyword,
               @Param("status") String status,
               @Param("from") LocalDate from,
               @Param("to") LocalDate to);

    List<AdminOrderSummary> search(@Param("keyword") String keyword,
                                   @Param("status") String status,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to,
                                   @Param("limit") int limit,
                                   @Param("offset") int offset);

    List<StatusCount> countByStatus();

    /**
     * 상태 전이. 현재 상태와 버전이 둘 다 맞을 때만 바꾼다.
     * 영향받은 행이 0이면 이미 다른 상태이거나 다른 사용자가 먼저 처리한 것이다.
     */
    int transition(@Param("orderId") long orderId,
                   @Param("version") int version,
                   @Param("fromStatus") String fromStatus,
                   @Param("toStatus") String toStatus);

    Optional<ReturnTarget> findReturnTargetForUpdate(@Param("orderId") long orderId,
                                                     @Param("lineNo") int lineNo);

    /** 잠금을 잡은 뒤 별도 문장으로 조회해야 다른 트랜잭션이 방금 커밋한 반품까지 보인다 */
    int sumReturnedQty(@Param("orderItemId") long orderItemId);

    void insertReturn(@Param("orderItemId") long orderItemId,
                      @Param("qty") int qty,
                      @Param("amount") BigDecimal amount,
                      @Param("reason") String reason,
                      @Param("createdBy") long createdBy);
}
