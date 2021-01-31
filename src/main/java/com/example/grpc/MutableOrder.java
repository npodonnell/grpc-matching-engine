package com.example.grpc;

import java.util.Objects;

/**
 * A more compact structure to represent orders than the protobuf-generated `Order` structure.
 */
public class MutableOrder {
    final public long orderId;
    final public long customerId;
    final public long limitPrice;
    final public boolean isBuyOrder;
    final public boolean isLimitOrder;
    public long remainingVolume;
    public long filledVolume;
    public long finishTime;
    public long cost;
    public boolean isCancelled;

    /***
     * Constructor.
     * @param orderId Order ID.
     * @param submitOrderRequest Protobuf generated SubmitOrderRequest.
     */
    public MutableOrder(long orderId, SubmitOrderRequest submitOrderRequest) {
        this.orderId = orderId;
        this.customerId = submitOrderRequest.getCustomerId();
        this.limitPrice = submitOrderRequest.getLimitPrice();
        this.isBuyOrder = (submitOrderRequest.getOrderDirection() == OrderDirection.BUY);
        this.isLimitOrder = (submitOrderRequest.getOrderType() == OrderType.LIMIT);
        this.remainingVolume = submitOrderRequest.getVolume();
        this.filledVolume = 0;
        this.finishTime = 0;
        this.cost = 0;
        this.isCancelled = false;
    }

    /***
     * Deduces the OrderStatus from the order's filled/remaining volume and cancellation status.
     * @return Deduced order status.
     */
    public OrderStatus orderStatus() {
        if (isCancelled) {
            if (filledVolume == 0) {
                return OrderStatus.CANCELLED;
            } else {
                return OrderStatus.PARTIALLY_FILLED_AND_CANCELLED;
            }
        } else {
            if (filledVolume == 0) {
                return OrderStatus.PENDING;
            } else if (remainingVolume == 0) {
                return OrderStatus.FILLED;
            } else {
                return OrderStatus.PARTIALLY_FILLED;
            }
        }
    }

    /***
     * Computes the mean matched price of the order, zero if no volume is fixed.
     * @return Mean matched price.
     */
    public long meanMatchedPrice() {
        return (filledVolume > 0) ? (cost / filledVolume) : 0;
    }

    /***
     * To tring.
     * @return String representation.
     */
    @Override
    public String toString() {
        return orderId + "|" +
                customerId + "|" +
                remainingVolume + "|" +
                filledVolume + "|" +
                limitPrice;
    }

    /***
     * Hash Code.
     * @return Hash code.
     */
    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }
}
