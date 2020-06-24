package com.example.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class OrderMatcherServer {
    public static void main(String[] args) throws IOException, InterruptedException {

        Server server = ServerBuilder
                .forPort(8080)
                .addService(new OrderMatcherServiceImpl())
                .build();

        server.start();

        System.out.println("Server Started\n");
        server.awaitTermination();
    }
}

