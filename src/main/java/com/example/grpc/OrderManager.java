package com.example.grpc;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/***
 * High-level manager of all orders and order-related functionality. Contains order books for all
 * tickers and routes incoming order-related operations to the correct order book.
 */
public class OrderManager {
    Map<Ticker, OrderBook> tickerToOrderBookMap = new HashMap<>();
    Map<Long, OrderBook> orderIdToOrderBookMap = new HashMap<>();

    private AtomicLong orderCount = new AtomicLong(0);

    /***
     * Constructor.
     */
    public OrderManager() {
        // Create blank order books for all the tickers
        for (Ticker ticker: Ticker.values()) {
            tickerToOrderBookMap.put(ticker, new OrderBook(ticker));
        }
    }

    /***
     * Submit an order to the appropriate order book.
     * @param submitOrderRequest Protobuf SubmitOrderRequest.
     * @return Protobuf Order.
     */
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

    /***
     * Retrieve an order from the appropriate order book.
     * @param orderReference Protobuf OrderReference.
     * @return Protobuf Order if found, otherwise empty optional.
     */
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

    /***
     * Retrieve the status of an order from the appropriate order book.
     * @param orderReference Protobuf OrderReference.
     * @return Protobuf OrderStatus if found, otherwise empty optional.
     */
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

    /***
     * Retrieve a bid/ask price quote on a ticker from the appropriate order book.
     * @param tickerReference Protobuf Ticker Reference containing a single Ticker.
     * @return price quote.
     */
    public Quote getQuote(TickerReference tickerReference) {
        return tickerToOrderBookMap.get(tickerReference.getTicker()).getQuote();
    }
}
