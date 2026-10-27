package io.github.kang30984.minierp.order;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderMapper {

    String nextOrderNo();

    void insertOrder(NewOrder order);

    void insertItems(@Param("orderId") long orderId, @Param("items") List<NewOrderItem> items);

    void insertHistory(@Param("orderId") long orderId,
                       @Param("fromStatus") String fromStatus,
                       @Param("toStatus") String toStatus,
                       @Param("reason") String reason,
                       @Param("changedBy") long changedBy);

    long countByPartner(@Param("partnerId") long partnerId, @Param("status") String status);

    List<OrderSummaryResponse> findByPartner(@Param("partnerId") long partnerId,
                                             @Param("status") String status,
                                             @Param("limit") int limit,
                                             @Param("offset") int offset);

    /** partnerId가 null이면 거래처 조건 없이 조회(관리자용) */
    Optional<OrderHeader> findHeader(@Param("orderId") long orderId, @Param("partnerId") Long partnerId);

    List<OrderItemResponse> findItems(@Param("orderId") long orderId);

    List<OrderHistoryResponse> findHistories(@Param("orderId") long orderId);

    int cancelRequested(@Param("orderId") long orderId, @Param("partnerId") long partnerId);
}
