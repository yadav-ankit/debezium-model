package com.ankit.debezium.model;

public record QueryResponse(
        String answer,
        int sourceChunksUsed,
        String status
) {
}
