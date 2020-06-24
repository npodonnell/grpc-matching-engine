package com.example.grpc;

import io.grpc.stub.StreamObserver;

import java.util.Optional;

class OrderMatcherServiceImpl extends OrderMatcherServiceGrpc.OrderMatcherServiceImplBase {
    OrderManager orderManager = new OrderManager();

    /**
     * SubmitOrder submits an order to the matching engine. To make the system as fast as possible, the
     * SubmitOrderRequest object is sent right through to the matching engine and order book as-is even though it has
     * some extra fields the order book is not concerned with.
     *
     * On the way out, the `Order` object returned from the matching engine is reduced down to a `SubmitOrderResponse`
     * object which contains the bare minimum information the customer needs after submitting the order.
     *
     * @param submitOrderRequest - SubmitOrderRequest submitted by the customer
     * @param responseObserver - StreamObserver which is notified when the order was submitted
     */
    @Override
    public void submitOrder(SubmitOrderRequest submitOrderRequest, StreamObserver<SubmitOrderResponse> responseObserver) {
        responseObserver.onNext(orderToSubmitOrderResponse(orderManager.match(submitOrderRequest)));
        responseObserver.onCompleted();
    }

    /**
     * Retrieve an order from the matching engine. Order can be activa or historical. If not found, the `Order` field
     * of the `RetrieveOrderResponse` will be empty.
     *
     * @param orderReference - OrderReference object which contains information used to locate the order (typically id)
     * @param responseObserver - StreamObserver which is notified when the order was found
     */
    @Override
    public void retrieveOrder(OrderReference orderReference, StreamObserver<RetrieveOrderResponse> responseObserver) {
        responseObserver.onNext(optionalOrderToRetrieveOrderResponse(orderManager.retrieveOrder(orderReference)));
        responseObserver.onCompleted();
    }

    /**
     * Attempts to cancel an order that's either in the PENDING or PARTIALLY_FILLED state.
     *
     * @param orderReference - OrderReference object which contains information used to locate the order (typically id)
     * @param responseObserver - StreamObserver which is notified when the order was cancelled
     */
    @Override
    public void cancelOrder(OrderReference orderReference, StreamObserver<CancelOrderResponse> responseObserver) {
        responseObserver.onNext(optionalOrderStatusToCancelOrderResponse(orderManager.cancelOrder(orderReference)));
        responseObserver.onCompleted();
    }

    /**
     * Gets a price quote for the desired ticker
     * @param tickerReference - TickerReference which contains information about the ticker we're interested in
     * @param responseObserver - StreamObserver which is notified when the quote is available
     */
    @Override
    public void getQuote(TickerReference tickerReference, StreamObserver<Quote> responseObserver) {
        responseObserver.onNext(orderManager.getQuote(tickerReference));
        responseObserver.onCompleted();
    }

    ///
    // Utility Functions
    ///

    private static SubmitOrderResponse orderToSubmitOrderResponse(Order order) {
        return SubmitOrderResponse.newBuilder()
                .setOrderId(order.getOrderId())
                .setMeanMatchedPrice(order.getMeanMatchedPrice())
                .setMatchedVolume(order.getMatchedVolume())
                .build();
    }

    private static RetrieveOrderResponse optionalOrderToRetrieveOrderResponse(Optional<Order> order) {
        RetrieveOrderResponse.Builder builder = RetrieveOrderResponse.newBuilder();
        if (order.isPresent()) {
            builder.setOrder(order.get());
        }
        return builder.build();
    }

    private static CancelOrderResponse optionalOrderStatusToCancelOrderResponse(Optional<OrderStatus> orderStatus) {
        CancelOrderResponse.Builder builder = CancelOrderResponse.newBuilder()
                .setOrderWasFound(orderStatus.isPresent());
        if (orderStatus.isPresent()) {
            builder.setFinalOrderStatus(orderStatus.get());
        }
        return builder.build();
    }
}
