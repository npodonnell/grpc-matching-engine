package com.example.grpc;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OrderBookTest {
    OrderBook orderBook;

    @Before
    public void setup() {
        orderBook = new OrderBook(Ticker.BTC_USD);
    }

    @Test
    public void testSubmitOrder() {
        SubmitOrderRequest submitOrderRequest;
        Order order;

        submitOrderRequest = makeLimitSubmitOrderRequest(6001, Ticker.BTC_USD,
                OrderDirection.BUY,10000, 10);
        order = orderBook.submitOrder(12345, submitOrderRequest);

        assertEquals(12345, order.getOrderId());
        assertEquals(6001, order.getCustomerId());
        assertEquals(Ticker.BTC_USD, order.getTicker());
        assertEquals(OrderDirection.BUY, order.getOrderDirection());
        assertEquals(OrderType.LIMIT, order.getOrderType());
        assertEquals(OrderStatus.PENDING, order.getOrderStatus());
        assertEquals(10000, order.getLimitPrice());
        assertEquals(10, order.getVolume());
        assertEquals(0, order.getMeanMatchedPrice());
        assertEquals(0, order.getMatchedVolume());
    }

    @Test
    public void testSubmitOrderSequence1() {
        /**
         * #1: Customer 6001 BUY 10 BTC_USD @ 10000
         * #2: Customer 6002 SELL 10 BTC_USD @ 10000
         */

        Order order1 = orderBook.submitOrder(1, makeLimitSubmitOrderRequest(6001, Ticker.BTC_USD,
                OrderDirection.BUY, 10000, 10));
        Order order2 = orderBook.submitOrder(2, makeLimitSubmitOrderRequest(6002, Ticker.BTC_USD,
                OrderDirection.SELL,10000, 10));

        assertOrderShort(order1, OrderStatus.PENDING, 0, 0);
        assertOrderShort(order2, OrderStatus.FILLED, 10000, 10);
    }

    @Test
    public void testSubmitOrderSequence2() {
        /**
         * #1: Customer 6001 BUY 10 BTC_USD @ 10000
         * #2: Customer 6002 SELL 10 BTC_USD @ MARKET
         */

        Order order1 = orderBook.submitOrder(1, makeLimitSubmitOrderRequest(6001, Ticker.BTC_USD,
                OrderDirection.BUY, 10000, 10));
        Order order2 = orderBook.submitOrder(2, makeMarketSubmitOrderRequest(6002, Ticker.BTC_USD,
                OrderDirection.SELL, 10));

        assertOrderShort(order1, OrderStatus.PENDING, 0, 0);
        assertOrderShort(order2, OrderStatus.FILLED, 10000, 10);
    }

    ///
    // Utility Functions
    ///

    private static SubmitOrderRequest makeLimitSubmitOrderRequest(long customerId, Ticker ticker,
                                                                  OrderDirection orderDirection, long limitPrice,
                                                                  long volume)
    {
        return SubmitOrderRequest.newBuilder()
                .setCustomerId(customerId)
                .setTicker(ticker)
                .setOrderDirection(orderDirection)
                .setOrderType(OrderType.LIMIT)
                .setLimitPrice(limitPrice)
                .setVolume(volume)
                .build();
    }

    private static SubmitOrderRequest makeMarketSubmitOrderRequest(long customerId, Ticker ticker,
                                                                   OrderDirection orderDirection, long volume)
    {
        return SubmitOrderRequest.newBuilder()
                .setCustomerId(customerId)
                .setTicker(ticker)
                .setOrderDirection(orderDirection)
                .setOrderType(OrderType.MARKET)
                .setVolume(volume)
                .build();
    }

    private static void assertOrderShort(Order order, OrderStatus orderStatus, long meanMatchedPrice, long meanMatchedVolume) {
        assertEquals(orderStatus, order.getOrderStatus());
        assertEquals(meanMatchedPrice, order.getMeanMatchedPrice());
        assertEquals(meanMatchedVolume, order.getMatchedVolume());
    }
}
