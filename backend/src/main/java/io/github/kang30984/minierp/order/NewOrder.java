package io.github.kang30984.minierp.order;

import java.math.BigDecimal;

/**
 * 주문 등록용 파라미터. INSERT 후 생성된 id를 MyBatis가 setId로 채워 준다.
 */
public class NewOrder {

    private Long id;
    private final String orderNo;
    private final Long partnerId;
    private final String partnerGradeCode;
    private final BigDecimal totalAmount;

    public NewOrder(String orderNo, Long partnerId, String partnerGradeCode, BigDecimal totalAmount) {
        this.orderNo = orderNo;
        this.partnerId = partnerId;
        this.partnerGradeCode = partnerGradeCode;
        this.totalAmount = totalAmount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public String getPartnerGradeCode() {
        return partnerGradeCode;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
}
