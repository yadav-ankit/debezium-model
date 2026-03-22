# Debezium MySQL Connector RAG

A **Retrieval-Augmented Generation (RAG)** application that lets you ask natural language questions about the [Debezium MySQL Connector](https://debezium.io/documentation/reference/2.7/connectors/mysql.html) and get accurate, doc-grounded answers — completely free and local, no API keys required.

---

## What does it do?

1. **On startup**, it scrapes the official Debezium MySQL Connector 2.7 documentation page using JSoup.
2. Splits the content into ~800-token chunks and embeds them using a local embedding model (`nomic-embed-text` via Ollama).
3. Stores all embeddings in an **in-memory vector store** (Spring AI `SimpleVectorStore`).
4. When you send a question via the REST API, it:
   - Embeds the question and finds the top-5 most relevant doc chunks (similarity search)
   - Injects those chunks as context into a prompt
   - Sends the prompt to a local LLM (`llama3.2` via Ollama) to generate an answer

---

## Tech Stack

| Layer         | Technology                          |
|---------------|-------------------------------------|
| Language      | Java 17                             |
| Framework     | Spring Boot 3.3.5                   |
| AI Framework  | Spring AI 1.0.0-M6                  |
| LLM           | `llama3.2` (via Ollama, runs locally) |
| Embeddings    | `nomic-embed-text` (via Ollama)     |
| Vector DB     | Spring AI `SimpleVectorStore` (in-memory) |
| Doc Scraping  | JSoup 1.18.1                        |
| Build         | Maven                               |

---

## Local Setup

### Prerequisites

- Java 17+
- Maven 3.8+
- [Ollama](https://ollama.com/) installed and running

### 1. Install Ollama

Download from [ollama.com](https://ollama.com/) and start it:

```bash
ollama serve
```

### 2. Pull the required models

```bash
ollama pull llama3.2          # Chat model (~2 GB)
ollama pull nomic-embed-text  # Embedding model (~274 MB)
```

### 3. Clone and run

```bash
git clone <repo-url>
cd debezium-model
mvn spring-boot:run
```

The app starts on **port 8080**. On startup it automatically fetches and indexes the Debezium docs (takes ~5–10 seconds depending on your machine).

---

## API Usage

### Check ingestion status

```bash
curl http://localhost:8080/api/rag/status
```

**Response:**
```json
{
  "message": "Ready to answer questions about Debezium MySQL Connector.",
  "totalChunks": 174,
  "ingested": true
}
```

### Query the docs

```bash
curl -X POST http://localhost:8080/api/rag/query \
  -H "Content-Type: application/json" \
  -d '{"question": "What are the required permissions for the Debezium MySQL connector user?"}'
```

**Response:**
```json
{
  "answer": "The required permissions are:\n- SELECT\n- RELOAD\n- SHOW DATABASES\n- REPLICATION SLAVE\n- REPLICATION CLIENT",
  "sourceChunksUsed": 5,
  "status": "OK"
}
```

---

## Where is the Vector DB?

The vector store is **in-memory** (`SimpleVectorStore`). This means:

- It lives entirely in the JVM heap — no external database needed.
- **It is rebuilt on every restart** by re-scraping and re-embedding the docs.
- There is no persistence to disk.

This is intentional for simplicity. If you need persistence across restarts, you can swap `SimpleVectorStore` for a persistent store like **PGVector**, **Chroma**, or **Qdrant** by changing the `VectorStoreConfig.java` bean and adding the relevant Spring AI dependency.

---

## How to Add More Documentation

To index additional pages, edit `DocumentIngestionService.java`:

### Option A — Add more URLs

Replace the single URL constant with a list and loop over them:

```java
private static final List<String> DOC_URLS = List.of(
    "https://debezium.io/documentation/reference/2.7/connectors/mysql.html",
    "https://debezium.io/documentation/reference/2.7/connectors/postgresql.html"
    // add more URLs here
);
```

Then in the `ingest()` method, iterate over `DOC_URLS` and call `Jsoup.connect(url)` for each, accumulating all raw documents into the same `rawDocuments` list before splitting and storing.

### Option B — Load from local files

Use `new Document(Files.readString(Path.of("path/to/file.txt")))` and add it to `rawDocuments` before the `vectorStore.add(chunks)` call.

### Option C — Load from PDFs

Add the `spring-ai-pdf-document-reader` dependency and use `PagePdfDocumentReader` to read PDF files into `Document` objects.

---

## Project Structure

```
src/main/java/com/ankit/debezium/
├── Main.java                          # Spring Boot entry point
├── config/
│   └── VectorStoreConfig.java         # In-memory vector store bean
├── controller/
│   └── RagController.java             # REST endpoints (/api/rag/query, /api/rag/status)
├── model/
│   ├── QueryRequest.java              # Request record { question }
│   └── QueryResponse.java             # Response record { answer, sourceChunksUsed, status }
└── service/
    ├── DocumentIngestionService.java  # Scrapes docs, splits, embeds, stores on startup
    └── RagService.java                # Similarity search + LLM prompt + answer generation
```

---

## Configuration

All configuration is in `src/main/resources/application.yml`:

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: llama3.2
      embedding:
        options:
          model: nomic-embed-text

server:
  port: 8080
```

To use a different Ollama model (e.g. `mistral`, `llama3.1`), change `model` under `chat.options`.
