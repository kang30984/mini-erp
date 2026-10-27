package io.github.kang30984.minierp.order;

import java.math.BigDecimal;

public class NewOrderItem {

    private final int lineNo;
    private final long productId;
    private final int qty;
    private final BigDecimal unitPrice;
    private final BigDecimal amount;

    public NewOrderItem(int lineNo, long productId, int qty, BigDecimal unitPrice) {
        this.lineNo = lineNo;
        this.productId = productId;
        this.qty = qty;
        this.unitPrice = unitPrice;
        this.amount = unitPrice.multiply(BigDecimal.valueOf(qty));
    }

    public int getLineNo() {
        return lineNo;
    }

    public long getProductId() {
        return productId;
    }

    public int getQty() {
        return qty;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
