# 🔍 Distributed Log Processing & Search Engine

A production-grade distributed log processing and search platform built with Java, Apache Kafka, and Elasticsearch. Capable of ingesting **50,000+ log events per minute** with **sub-300ms search latency**.

![Dashboard Preview](https://img.shields.io/badge/Status-Production%20Ready-00FF88?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F?style=for-the-badge&logo=springboot)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-7.5-231F20?style=for-the-badge&logo=apachekafka)
![Elasticsearch](https://img.shields.io/badge/Elasticsearch-8.11-005571?style=for-the-badge&logo=elasticsearch)
![React](https://img.shields.io/badge/React-18-61DAFB?style=for-the-badge&logo=react)

---

## ✨ Features

- **High-throughput ingestion** — Single and batch log ingestion via REST API, published to Kafka with 6 partitions
- **Intelligent parsing** — Automatic detection and parsing of Logback, Log4j, JSON, and plaintext log formats
- **Real-time processing** — Kafka consumer pipeline processes and indexes logs into Elasticsearch within seconds
- **Powerful search** — Full-text search, filter by service/host/level/time range, trace correlation
- **Dead Letter Queue** — Failed messages routed to `log-events.DLT` for reliability
- **Analytics Dashboard** — React frontend with live charts, log level distribution, service analytics
- **Trace Timeline** — Visualize an entire request journey across microservices by trace ID

---

## 🏗️ Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   React Frontend│───▶│Ingestion Service│────▶│   Apache Kafka   │───▶│Processor Service│
│   (Port 3000)   │     │   (Port 8081)   │     │   (Port 9092)    │     │   (Port 8082)   │
└─────────────────┘     └─────────────────┘     └──────────────────┘     └────────┬────────┘
         │                                                                          │
         │              ┌─────────────────┐                              ┌─────────▼────────┐
         └────────────▶│  Search API     │◀─────────────────────────── │  Elasticsearch   │
                        │  (Port 8083)    │                               │   (Port 9200)    │
                        └─────────────────┘                              └──────────────────┘
```

### Modules

| Module | Description |
|--------|-------------|
| `common` | Shared models (`LogEvent`, `SearchRequest`, `SearchResponse`), DTOs |
| `ingestion` | REST API for receiving logs, Kafka producer, log validation |
| `processor` | Kafka consumer, log parser (Logback/Log4j/JSON/Plaintext), Elasticsearch indexer |
| `search-api` | Full-text search, aggregations, trace lookup, pagination |
| `queue` | Kafka topic configuration, Dead Letter Queue setup |

---

## 🚀 Quick Start

### Prerequisites

- Java 17+
- Docker & Docker Compose
- Node.js 18+ (for frontend)

### 1. Start Infrastructure

```bash
docker compose up -d zookeeper kafka elasticsearch kibana kafka-ui
```

Wait for all containers to be healthy (~30 seconds), then create Kafka topics:

```bash
docker exec kafka kafka-topics --bootstrap-server localhost:9092 \
  --create --if-not-exists --topic log-events --partitions 6 --replication-factor 1

docker exec kafka kafka-topics --bootstrap-server localhost:9092 \
  --create --if-not-exists --topic log-events.DLT --partitions 3 --replication-factor 1
```

### 2. Start Backend Services

Open IntelliJ IDEA and run each main class:

```
ingestion  → IngestionServiceApplication  (port 8081)
processor  → ProcessorApplication         (port 8082)
search-api → SearchApiApplication         (port 8083)
```

### 3. Start Frontend

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:3000**

---

## 📡 API Reference

### Ingestion Service (Port 8081)

#### Ingest Single Log
```http
POST /api/v1/logs
Content-Type: application/json

{
  "serviceName": "payment-service",
  "host": "prod-node-01",
  "log": "2024-01-15 10:23:45.123 [main] ERROR com.example.PaymentService - Payment failed: timeout traceId=abc123"
}
```

**Response:**
```json
{
  "status": "accepted",
  "eventId": "23847a98-6ba7-4f98-abbf-c12b260d8dca",
  "ingestedAt": "2026-02-22T13:43:35.001899Z"
}
```

#### Ingest Batch
```http
POST /api/v1/logs/batch
Content-Type: application/json

{
  "serviceName": "auth-service",
  "host": "prod-node-02",
  "logs": [
    "INFO: User login successful userId=12345 traceId=abc123",
    "WARN: Rate limit approaching for IP 192.168.1.1",
    "ERROR: JWT token expired traceId=abc123"
  ]
}
```

### Search API (Port 8083)

#### Full-Text Search
```http
GET /api/v1/search?query=payment&serviceName=payment-service&page=0&size=20
```

#### Filter by Level
```http
GET /api/v1/search?levels=ERROR,WARN&sortOrder=desc
```

#### Trace Lookup
```http
GET /api/v1/search/trace/{traceId}
```

#### Get by ID
```http
GET /api/v1/search/{id}
```

**Search Response:**
```json
{
  "hits": [...],
  "totalHits": 42,
  "tookMs": 31,
  "page": 0,
  "size": 20,
  "totalPages": 3,
  "levelCounts": { "ERROR": 5, "WARN": 12, "INFO": 25 },
  "serviceCounts": { "payment-service": 20, "auth-service": 22 }
}
```

---

## 🧱 Tech Stack

### Backend
| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 17 | Core language |
| Spring Boot | 3.3.5 | Application framework |
| Apache Kafka | 7.5.0 | Message streaming |
| Spring Kafka | 3.2.4 | Kafka integration |
| Elasticsearch | 8.11.0 | Search & analytics engine |
| Elasticsearch Java Client | 8.11.0 | ES Java API |
| Lombok | Latest | Boilerplate reduction |
| Maven | 3.x | Build tool |

### Frontend
| Technology | Version | Purpose |
|------------|---------|---------|
| React | 18 | UI framework |
| Vite | 5 | Build tool & dev server |
| Tailwind CSS | 3 | Styling |
| Recharts | 2 | Charts & analytics |
| Axios | 1.6 | HTTP client |
| React Router | 6 | Client-side routing |
| Lucide React | Latest | Icons |

### Infrastructure
| Service | Port | Purpose |
|---------|------|---------|
| Zookeeper | 2181 | Kafka coordination |
| Kafka | 9092 | Message broker |
| Elasticsearch | 9200 | Search engine |
| Kibana | 5601 | ES management UI |
| Kafka UI | 8090 | Kafka management UI |

---

## 📊 Performance Targets

| Metric | Target | Notes |
|--------|--------|-------|
| Ingestion throughput | 50,000+ events/min | 6 Kafka partitions |
| Search latency | < 300ms | p99 |
| Batch size | Up to 1,000 logs | Per request |
| Index refresh | 5 seconds | Configurable |

---

## 🗂️ Project Structure

```
distributed-log-search/
├── common/                          # Shared models and DTOs
│   └── src/main/java/com/logSearch/common/
│       ├── model/LogEvent.java
│       └── dto/SearchRequest.java, SearchResponse.java
├── ingestion/                       # Log ingestion service
│   └── src/main/java/com/logSearch/ingestion/
│       ├── controller/LogIngestionController.java
│       ├── service/LogIngestionService.java
│       └── config/KafkaProducerConfig.java
├── processor/                       # Kafka consumer + ES indexer
│   └── src/main/java/com/logSearch/processor/
│       ├── consumer/LogEventConsumer.java
│       ├── service/LogParserService.java
│       └── service/ElasticsearchIndexingService.java
├── search-api/                      # Search REST API
│   └── src/main/java/com/logSearch/search/
│       ├── controller/SearchController.java
│       └── service/LogSearchService.java
├── queue/                           # Kafka topic configuration
├── docker-compose.yml
└── pom.xml
```

---

## 🖥️ Dashboard Screenshots

### Dashboard — Live Analytics
Real-time log statistics, level distribution, and service breakdown charts.

### Search — Full-Text & Filtered
Search across all logs with filters for service, level, host, and time range.

### Trace Timeline
Visualize a complete request journey across microservices using trace IDs.

### Ingest — Send Logs
Send single or batch logs directly from the UI with sample templates.

---

## 🔧 Configuration

### Ingestion Service (`application.yml`)
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      batch-size: 16384
      linger-ms: 5
kafka:
  topic:
    name: log-events
    partitions: 6
```

### Processor Service (`application.yml`)
```yaml
spring:
  kafka:
    consumer:
      group-id: log-processor-group
      max-poll-records: 500
elasticsearch:
  index:
    prefix: applogs
```

### Search API (`application.yml`)
```yaml
elasticsearch:
  index:
    prefix: applogs
server:
  port: 8083
```

---

## 📈 Supported Log Formats

The processor automatically detects and parses these formats:

```
# Logback
2024-01-15 10:23:45.123 [main] ERROR com.example.Service - Message traceId=abc123

# Log4j
2024-01-15 10:23:45,123 ERROR [ThreadName] com.example.Service - Message

# JSON
{"level":"ERROR","message":"Something failed","timestamp":"2024-01-15T10:23:45Z","traceId":"abc123"}

# Plaintext
ERROR: Something went wrong with the payment processor
```

---

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'feat: add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License.

---

<div align="center">
  Built with ☕ Java, ⚡ Kafka, and 🔍 Elasticsearch
</div>
