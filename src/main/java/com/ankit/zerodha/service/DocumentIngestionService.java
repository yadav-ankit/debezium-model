package com.ankit.zerodha.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class DocumentIngestionService {

    private static final String DEBEZIUM_MYSQL_DOC_URL =
            "https://debezium.io/documentation/reference/2.7/connectors/mysql.html";

    private final VectorStore vectorStore;
    private final AtomicBoolean ingested = new AtomicBoolean(false);
    private volatile int totalChunks = 0;

    public DocumentIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @PostConstruct
    public void ingestAsync() {
        CompletableFuture.runAsync(this::ingest);
    }

    private void ingest() {
        try {
            log.info("Fetching Debezium MySQL connector documentation from {}...", DEBEZIUM_MYSQL_DOC_URL);

            org.jsoup.nodes.Document htmlDoc = Jsoup.connect(DEBEZIUM_MYSQL_DOC_URL)
                    .userAgent("Mozilla/5.0 (compatible; RAG-Bot/1.0)")
                    .timeout(60_000)
                    .get();

            // Remove navigation and non-content elements
            htmlDoc.select("nav, header, footer, script, style, .navbar, .toc-menu, #toc").remove();

            List<Document> rawDocuments = new ArrayList<>();

            // Try extracting by sections first for better metadata
            Elements sections = htmlDoc.select(".sect1, .sect2, .sect3");

            if (!sections.isEmpty()) {
                log.info("Found {} sections to extract", sections.size());
                for (Element section : sections) {
                    Element heading = section.selectFirst("h2, h3, h4");
                    String sectionTitle = heading != null ? heading.text() : "Debezium MySQL Connector";
                    String sectionText = section.text();

                    if (sectionText.length() > 100) {
                        rawDocuments.add(new Document(
                                sectionText,
                                Map.of(
                                        "source", DEBEZIUM_MYSQL_DOC_URL,
                                        "section", sectionTitle,
                                        "title", "Debezium MySQL Connector 2.7"
                                )
                        ));
                    }
                }
            }

            // Fallback: extract full body text as one document
            if (rawDocuments.isEmpty()) {
                log.warn("No sections found, falling back to full body text extraction");
                Element mainContent = htmlDoc.selectFirst("#content, main, .content, article");
                String fullText = mainContent != null ? mainContent.text() : htmlDoc.body().text();
                rawDocuments.add(new Document(
                        fullText,
                        Map.of("source", DEBEZIUM_MYSQL_DOC_URL, "title", "Debezium MySQL Connector 2.7")
                ));
            }

            log.info("Extracted {} raw document sections, splitting into chunks...", rawDocuments.size());

            // Split into smaller chunks (default: ~800 tokens per chunk)
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> chunks = splitter.apply(rawDocuments);

            totalChunks = chunks.size();
            log.info("Storing {} chunks in vector store...", totalChunks);

            vectorStore.add(chunks);
            ingested.set(true);

            log.info("Ingestion complete. {} chunks indexed.", totalChunks);

        } catch (Exception e) {
            log.error("Failed to ingest Debezium documentation: {}", e.getMessage(), e);
        }
    }

    public boolean isIngested() {
        return ingested.get();
    }

    public int getTotalChunks() {
        return totalChunks;
    }
}
