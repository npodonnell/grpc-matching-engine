# gRPC Order Matching Engine
N. P. O'Donnell, 2020

## Summary

This is a simple gRPC/protobuf based order matching service/engine, similar to those used in financial markets. Buyers 
and sellers submit orders, and the engine matches buy orders with sell orders, giving both the buyer and seller the best 
price available.

* Buyers and Sellers may:
  * Submit orders
  * Check pending orders
  * Cancel orders
  
* Anybody may:
  * Get a quote of current (latest) price of a market
  * Get a real-time stream of price quotes for a market (**TBD**)

All order matching happens in-memory and orders are stored in two `TreeMap` structures - one for pending buys and one
pending sells. Each `TreeMap` stores the orders sorted by price - pending buys (bids) in in descending order and
pending sells (asks) in ascending order. 

Together, these two `TreeMap`s constitute what is known as
the "Order Book".

When a new *limit* sell order comes in, its price is checked to see if it can be matched immediately.
If there exists a set of pending buy orders such that their prices are all greater or equal to to the sell
order's price, then the sell can be filled. If the summed value of these buy orders is greater or equal to the value
of the sell, then the sell can be fully filled, otherwise its a partial fill, and the highest buys are used and the
order remains, where it waits to be fully filled. If the sell can not be matched immediately then it's placed in the 
order book and waits until when (and if) the price moves in its direction.

Buy orders happen the same way except in the opposite direction.

Pending buy or sell orders may be manually cancelled before they get filled but if the order is already partially 
filled, the filled portion remains filled and the remainder is cancelled. *Market* sells and buys are usually filled 
immediately, and *take* the best bids/asks currently available, which means they are subject to *slippage*, which means
the *taker* (the person who submitted the market order), may get a worse price than they expected.

When a trader's order is submitted, they are returned an `orderId` which they can use to retrieve the order record in
the future. If the order was fully filled immediately, they likely won't need to use the `orderId`, but if the trade was
partially filled or not filled, they can use the `orderId` in the future to either check on the order or cancel it.

## Building & Running

Build:
```
./gradlew build
```

Start server:
```
./gradlew runServer
```

Once the server is running you can submit commands by invoking the client.

### Example Commands

Submit an order:
```
./gradlew runClient --args='-cid 1234 -sc SUBMIT_ORDER -t BTC_USD -d BUY -ot LIMIT -p 10000 -v 70'
```

Retrieve an order:
```
./gradlew runClient --args='-sc RETRIEVE_ORDER -oid 1'
```

Cancel an order:
```
./gradlew runClient --args='-sc CANCEL_ORDER -oid 1'
```

Get a price quote:
```
./gradlew runClient --args='-sc GET_QUOTTE -t BTC_USD'
```
