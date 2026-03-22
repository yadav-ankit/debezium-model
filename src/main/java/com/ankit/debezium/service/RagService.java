package com.ankit.debezium.service;

import com.ankit.debezium.model.QueryRequest;
import com.ankit.debezium.model.QueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagService {

    private static final String SYSTEM_PROMPT = """
            You are an expert on the Debezium MySQL Connector (version 2.7).
            Your job is to answer questions accurately based solely on the provided documentation context.

            Guidelines:
            - Answer only from the given context. Do not use external knowledge.
            - If the answer is not present in the context, clearly say: "I couldn't find this information in the Debezium MySQL Connector documentation."
            - Be concise, structured, and developer-friendly.
            - Use bullet points or numbered lists when appropriate.
            - When mentioning configuration properties, use their exact names as they appear in the docs.
            """;

    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final DocumentIngestionService ingestionService;

    public RagService(VectorStore vectorStore, ChatModel chatModel,
                      DocumentIngestionService ingestionService) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.ingestionService = ingestionService;
    }

    public QueryResponse query(QueryRequest request) {
        if (!ingestionService.isIngested()) {
            return new QueryResponse(
                    "Documentation is still being indexed. Please try again in a moment.",
                    0,
                    "INDEXING_IN_PROGRESS"
            );
        }

        String question = request.question();
        log.info("Processing query: {}", question);

        // Step 1: Retrieve top-k relevant chunks from vector store
        List<Document> relevantDocs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(5)
                        .similarityThreshold(0.5)
                        .build()
        );

        if (relevantDocs.isEmpty()) {
            return new QueryResponse(
                    "No relevant documentation found for your question.",
                    0,
                    "NO_RESULTS"
            );
        }

        log.info("Retrieved {} relevant chunks for query", relevantDocs.size());

        // Step 2: Build context from retrieved chunks
        String context = relevantDocs.stream()
                .map(doc -> {
                    String section = (String) doc.getMetadata().getOrDefault("section", "General");
                    return "### " + section + "\n" + doc.getText();
                })
                .collect(Collectors.joining("\n\n---\n\n"));

        // Step 3: Build prompt
        String userPrompt = """
                Use the following Debezium MySQL Connector documentation excerpts to answer the question.

                DOCUMENTATION CONTEXT:
                %s

                QUESTION: %s
                """.formatted(context, question);

        // Step 4: Call LLM
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage(userPrompt)
        ));

        String answer = chatModel.call(prompt)
                .getResult()
                .getOutput()
                .getText();

        log.info("Generated answer using {} source chunks", relevantDocs.size());

        return new QueryResponse(answer, relevantDocs.size(), "OK");
    }
}
