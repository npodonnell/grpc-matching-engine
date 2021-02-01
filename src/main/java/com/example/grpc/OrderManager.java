package com.example.grpc;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class OrderManager {
    Map<Ticker, OrderBook> tickerToOrderBookMap = new HashMap<>();
    Map<Long, OrderBook> orderIdToOrderBookMap = new HashMap<>();

    private AtomicLong orderCount = new AtomicLong(0);

    public OrderManager() {
        // Create blank order books for all the tickers
        for (Ticker ticker: Ticker.values()) {
            tickerToOrderBookMap.put(ticker, new OrderBook(ticker));
        }
    }

    public Order submitOrder(SubmitOrderRequest submitOrderRequest) {
        // Get the appropriate order book
        OrderBook orderBook = tickerToOrderBookMap.get(submitOrderRequest.getTicker());

        // Generate a unique order ID
        long orderId = orderCount.incrementAndGet();

        // Associate the order ID with the order book for later retrieval
        orderIdToOrderBookMap.put(orderId, orderBook);

        // Attempt to match the order
        return orderBook.submitOrder(orderId, submitOrderRequest);
    }

    public Optional<Order> retrieveOrder(OrderReference orderReference) {
        // Get the order ID
        long orderId = orderReference.getOrderId();

        if (!orderIdToOrderBookMap.containsKey(orderId)) {
            return Optional.empty();
        }

        // Get the appropriate order book
        OrderBook orderBook = orderIdToOrderBookMap.get(orderId);

        // Return the order
        return Optional.of(orderBook.retrieveOrder(orderId));
    }

    public Optional<OrderStatus> cancelOrder(OrderReference orderReference) {
        // Get the order ID
        long orderId = orderReference.getOrderId();

        if (!orderIdToOrderBookMap.containsKey(orderId)) {
            return Optional.empty();
        }

        // Get the appropriate order book
        OrderBook orderBook = orderIdToOrderBookMap.get(orderId);

        // Cancel the order
        return Optional.of(orderBook.cancelOrder(orderId));
    }

    public Quote getQuote(TickerReference tickerReference) {
        return tickerToOrderBookMap.get(tickerReference.getTicker()).getQuote();
    }
}
