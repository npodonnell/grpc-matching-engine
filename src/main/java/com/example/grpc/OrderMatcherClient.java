package com.example.grpc;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.protobuf.Descriptors;
import io.grpc.ManagedChannelBuilder;

public class OrderMatcherClient {
    private enum SubCommand {
        SUBMIT_ORDER,
        RETRIEVE_ORDER,
        CANCEL_ORDER,
        GET_QUOTE
    };

    @Parameter(names={"--subCommand", "-sc"})
    private SubCommand subCommand;

    @Parameter(names={"--customerId", "-cid"})
    private long customerId;

    @Parameter(names={"--orderId", "-oid"})
    private long orderId;

    @Parameter(names={"--ticker", "-t"})
    private Ticker ticker;

    @Parameter(names={"--direction", "-d"})
    private OrderDirection orderDirection;

    @Parameter(names={"--orderType", "-ot"})
    private OrderType orderType;

    @Parameter(names={"--price", "-p"})
    private long price;

    @Parameter(names={"--volume", "-v"})
    private long volume;

    private OrderMatcherServiceGrpc.OrderMatcherServiceBlockingStub orderMatcherServiceBlockingStub;

    public static void main(String... argv) {
        OrderMatcherClient orderMatcherClient = new OrderMatcherClient();
        JCommander.newBuilder()
                .addObject(orderMatcherClient)
                .build()
                .parse(argv);
        orderMatcherClient.run();
    }

    private void run() {
        // Obtain the stub for the order matching service
        orderMatcherServiceBlockingStub =
                OrderMatcherServiceGrpc.newBlockingStub(ManagedChannelBuilder.forAddress("localhost", 8080)
                .usePlaintext()
                .build());

        switch(subCommand) {
            case SUBMIT_ORDER:
                submitOrder();
                break;

            case RETRIEVE_ORDER:
                retrieveOrder();
                break;

            case CANCEL_ORDER:
                cancelOrder();
                break;

            case GET_QUOTE:
                getQuote();
                break;
        }
    }

    private void submitOrder() {
        SubmitOrderRequest submitOrderRequest = SubmitOrderRequest.newBuilder()
                .setCustomerId(customerId)
                .setTicker(ticker)
                .setOrderDirection(orderDirection)
                .setOrderType(orderType)
                .setLimitPrice(price)
                .setVolume(volume)
                .build();
        SubmitOrderResponse submitOrderResponse = orderMatcherServiceBlockingStub.submitOrder(submitOrderRequest);
        System.out.println(submitOrderResponse);
    }

    private void retrieveOrder() {
        OrderReference orderReference = OrderReference.newBuilder()
                .setOrderId(orderId)
                .build();
        RetrieveOrderResponse retrieveOrderResponse = orderMatcherServiceBlockingStub.retrieveOrder(orderReference);
        if (retrieveOrderResponse.hasOrder()) {
            System.out.println(retrieveOrderResponse.getOrder());
        } else {
            System.out.println("Order " + orderId + " not found!");
        }
    }

    private void cancelOrder() {
        OrderReference orderReference = OrderReference.newBuilder()
                .setOrderId(orderId)
                .build();
        CancelOrderResponse cancelOrderResponse = orderMatcherServiceBlockingStub.cancelOrder(orderReference);
        if (cancelOrderResponse.getOrderWasFound()) {
            System.out.println("Order " + orderId + " is now: " + cancelOrderResponse.getFinalOrderStatus());
        } else {
            System.out.println("Order " + orderId + " not found!");
        }
    }

    private void getQuote() {
        TickerReference tickerReference = TickerReference.newBuilder()
                .setTicker(ticker)
                .build();
        Quote quote = orderMatcherServiceBlockingStub.getQuote(tickerReference);
        System.out.println(quote);
    }
}
