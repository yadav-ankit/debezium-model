package com.ankit.zerodha.model;

public record QueryResponse(
        String answer,
        int sourceChunksUsed,
        String status
) {
}
