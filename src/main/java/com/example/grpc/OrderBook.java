package com.example.grpc;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

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

    public Order match(long orderId, SubmitOrderRequest submitOrderRequest) {
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

    public Order retrieveOrder(long orderId) {
        return mutableOrderToOrder(allOrders.get(orderId));
    }

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

    private void addToHistory(MutableOrder mutableOrder) {
        mutableOrder.finishTime = System.currentTimeMillis();
        orderHistory.add(mutableOrder);
    }

    ///
    // Utility Functions
    ///

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
