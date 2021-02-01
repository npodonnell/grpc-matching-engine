package com.example.grpc;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

/***
 * An order book for a specific ticker/asset. Contains the core functionality to submit, retrieve and cancel orders.
 */
public class OrderBook {
    /**
     * The ticker to which this order book pertains
     */
    final Ticker ticker;

    /**
     * All orders submitted get put into this hash map so that duplicate orders may not be submitted and existing
     * orders may be retrieved.
     */
    HashMap<Long, MutableOrder> allOrders;

    /**
     * Active buy orders and sell orders are put into these two sets until they are full matched.
     */
    ConcurrentSkipListSet<MutableOrder> buyOrders;
    ConcurrentSkipListSet<MutableOrder> sellOrders;

    /**
     * Once an order becomes inactive by either getting fully matched or cancelled, it goes into the history.
     */
    ConcurrentSkipListSet<MutableOrder> orderHistory;

    /***
     * Constructor.
     * @param ticker Ticker of this order book.
     */
    public OrderBook(Ticker ticker) {
        this.ticker = ticker;

        allOrders = new HashMap<>();

        buyOrders = new ConcurrentSkipListSet<MutableOrder>(new Comparator<MutableOrder>() {
            @Override
            public int compare(MutableOrder o1, MutableOrder o2) {
                int priceCompare = Long.compare(o2.limitPrice, o1.limitPrice);
                return (priceCompare != 0) ? priceCompare : Long.compare(o1.orderId, o2.orderId);
            }
        });

        sellOrders = new ConcurrentSkipListSet<MutableOrder>(new Comparator<MutableOrder>() {
            @Override
            public int compare(MutableOrder o1, MutableOrder o2) {
                int priceCompare = Long.compare(o1.limitPrice, o2.limitPrice);
                return (priceCompare != 0) ? priceCompare : Long.compare(o1.orderId, o2.orderId);
            }
        });

        orderHistory = new ConcurrentSkipListSet<MutableOrder>(new Comparator<MutableOrder>() {
            @Override
            public int compare(MutableOrder o1, MutableOrder o2) {
                return Long.compare(o1.finishTime, o2.finishTime);
            }
        });
    }

    /***
     * Submit a new order to the matching engine.
     * @param orderId Order ID.
     * @param submitOrderRequest Protobuf SubmitOrderRequest.
     * @return Protobuf Order.
     */
    public Order submitOrder(long orderId, SubmitOrderRequest submitOrderRequest) {
        assert(submitOrderRequest.getTicker() == ticker);
        assert(!allOrders.containsKey(orderId));

        MutableOrder mutableOrder = new MutableOrder(orderId, submitOrderRequest);
        allOrders.put(orderId, mutableOrder);

        if (mutableOrder.isBuyOrder) {
            matchBuy(mutableOrder);
        } else {
            matchSell(mutableOrder);
        }

        return mutableOrderToOrder(mutableOrder);
    }

    /***
     * Retrieve an order from the matching engine by orderId.
     * @param orderId Order ID.
     * @return Protobuf Order.
     */
    public Order retrieveOrder(long orderId) {
        return mutableOrderToOrder(allOrders.get(orderId));
    }

    /***
     * Cancel an active order. If the order has already been filled, cancellation is impossible. Otherwise the final
     * state of the order will be CANCELLED or PARTIALLY_FILLED_AND_CANCELLED.
     * @param orderId Order ID.
     * @return Terminal order status.
     */
    public OrderStatus cancelOrder(long orderId) {
        MutableOrder mutableOrder = allOrders.get(orderId);
        OrderStatus currentStatus = mutableOrder.orderStatus();

        if (currentStatus == OrderStatus.CANCELLED || currentStatus == OrderStatus.PARTIALLY_FILLED_AND_CANCELLED ||
                currentStatus == OrderStatus.FILLED) {
            // Order is already in terminal state
            return currentStatus;
        }

        if (mutableOrder.isBuyOrder) {
            buyOrders.remove(mutableOrder);
        } else {
            sellOrders.remove(mutableOrder);
        }

        mutableOrder.finishTime = System.currentTimeMillis();
        mutableOrder.isCancelled = true;
        addToHistory(mutableOrder);

        return mutableOrder.orderStatus();
    }

    /***
     * Retrieve an instant bid/ask quote. bid is the highest bid of a buyer currently in the order book, ask is the
     * lowest ask of a seller currently in the order book. Volume is not considered.
     * @return Protobuf Quote.
     */
    public Quote getQuote() {
        Quote.Builder builder = Quote.newBuilder();
        if (buyOrders.size() > 0) {
            builder.setBid(buyOrders.first().limitPrice);
        }
        if (sellOrders.size() > 0) {
            builder.setAsk(sellOrders.first().limitPrice);
        }
        return builder.build();
    }

    ///
    // Private Functions
    ///

    /***
     * Attempt to match a newly submitted buy order against existing sell order(s).
     * @param buyOrder New buy order.
     */
    private void matchBuy(MutableOrder buyOrder) {
        for (MutableOrder sellOrder: sellOrders) {
            if (buyOrder.isLimitOrder && sellOrder.limitPrice > buyOrder.limitPrice) {
                // Limit reached
                break;
            }

            long volume = Math.min(buyOrder.remainingVolume, sellOrder.remainingVolume);

            buyOrder.remainingVolume -= volume;
            buyOrder.filledVolume += volume;
            sellOrder.remainingVolume -= volume;
            sellOrder.filledVolume += volume;

            buyOrder.cost += sellOrder.limitPrice * volume;
            sellOrder.cost += sellOrder.limitPrice * volume;

            if (sellOrder.remainingVolume == 0) {
                sellOrders.remove(sellOrder);
                addToHistory(sellOrder);
            }

            if (buyOrder.remainingVolume == 0) {
                addToHistory(buyOrder);
                break;
            }
        }

        if (buyOrder.remainingVolume > 0) {
            buyOrders.add(buyOrder);
        }
    }

     /***
     * Attempt to match a newly submitted sell order against existing buy order(s).
     * @param sellOrder New sell order.
     */
    private void matchSell(MutableOrder sellOrder) {
        for (MutableOrder buyOrder: buyOrders) {
            if (sellOrder.isLimitOrder && buyOrder.limitPrice < sellOrder.limitPrice) {
                // Limit reached
                break;
            }

            long volume = Math.min(sellOrder.remainingVolume, buyOrder.remainingVolume);

            sellOrder.remainingVolume -= volume;
            sellOrder.filledVolume += volume;
            buyOrder.remainingVolume -= volume;
            buyOrder.filledVolume += volume;

            sellOrder.cost += buyOrder.limitPrice * volume;
            buyOrder.cost += buyOrder.limitPrice * volume;

            if (buyOrder.remainingVolume == 0) {
                buyOrders.remove(buyOrder);
                addToHistory(buyOrder);
            }

            if (sellOrder.remainingVolume == 0) {
                addToHistory(sellOrder);
                break;
            }
        }

        if (sellOrder.remainingVolume > 0) {
            sellOrders.add(sellOrder);
        }
    }

    /***
     * After an order has been either fully filled or cancelled, it gets added to the history and finish time is
     * recorded.
     * @param mutableOrder Mutable Order.
     */
    private void addToHistory(MutableOrder mutableOrder) {
        mutableOrder.finishTime = System.currentTimeMillis();
        orderHistory.add(mutableOrder);
    }

    ///
    // Utility Functions
    ///

    /***
     * Converts a mutable order to a protobuf order.
     * @param mutableOrder Mutable order.
     * @return Protobuf order.
     */
    private Order mutableOrderToOrder(MutableOrder mutableOrder) {
        return Order.newBuilder()
                .setOrderId(mutableOrder.orderId)
                .setCustomerId(mutableOrder.customerId)
                .setTicker(ticker)
                .setOrderDirection(mutableOrder.isBuyOrder ? OrderDirection.BUY : OrderDirection.SELL)
                .setOrderType(mutableOrder.isLimitOrder ? OrderType.LIMIT : OrderType.MARKET)
                .setOrderStatus(mutableOrder.orderStatus())
                .setLimitPrice(mutableOrder.limitPrice)
                .setVolume(mutableOrder.filledVolume + mutableOrder.remainingVolume)
                .setMeanMatchedPrice(mutableOrder.meanMatchedPrice())
                .setMatchedVolume(mutableOrder.filledVolume)
                .build();
    }
}
