package org.wang.rabbitmqlab.springdemo.jsonconverter;

/**
 * 消息体 POJO：普通的 JavaBean，不需要 implements Serializable（JSON 序列化没这要求）。
 * 必须有无参构造 + getter/setter（Jackson 的规矩）。
 */
public class Order {

    private String orderId;
    private String product;
    private int amount;

    public Order() {
    }

    public Order(String orderId, String product, int amount) {
        this.orderId = orderId;
        this.product = product;
        this.amount = amount;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "Order{orderId='" + orderId + "', product='" + product + "', amount=" + amount + '}';
    }
}
