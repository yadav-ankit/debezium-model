package com.ankit.debezium.controller;

import com.ankit.debezium.model.QueryRequest;
import com.ankit.debezium.model.QueryResponse;
import com.ankit.debezium.service.DocumentIngestionService;
import com.ankit.debezium.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;
    private final DocumentIngestionService ingestionService;

    /**
     * Query the Debezium MySQL Connector documentation.
     *
     * POST /api/rag/query
     * Body: { "question": "What snapshot modes does Debezium MySQL connector support?" }
     */
    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new QueryResponse("Question cannot be empty.", 0, "BAD_REQUEST"));
        }
        QueryResponse response = ragService.query(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Check ingestion status.
     *
     * GET /api/rag/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "ingested", ingestionService.isIngested(),
                "totalChunks", ingestionService.getTotalChunks(),
                "message", ingestionService.isIngested()
                        ? "Ready to answer questions about Debezium MySQL Connector."
                        : "Documentation indexing in progress..."
        ));
    }
}
