# High-Performance Product Catalog API

[![Spring Boot Version](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java Version](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/technologies/downloads/)
[![Redis](https://img.shields.io/badge/Redis-7.0-red.svg)](https://redis.io/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue.svg)](https://www.postgresql.org/)
[![Flyway](https://img.shields.io/badge/Flyway-Enabled-red.svg)](https://flywaydb.org/)

A production-grade, highly optimized Product Catalog REST API built using **Spring Boot 4**, **Java 21**, **PostgreSQL**, and **Redis**. Designed to handle high-read traffic patterns, secure endpoints, control request limits, and recover gracefully from infrastructure failures.

---

## 🏗️ Architecture & Key Concepts

This application is built with performance and resilience at its core. Below is an overview of the key concepts implemented, the specific problems they solve, and their architectural solutions.

### 1. High-Performance Caching (Cache-Aside Pattern)
*   **The Problem:** Reading data directly from a relational database (PostgreSQL) is resource-intensive. Under high read volumes (e.g., product search, catalog browsing), database connections can easily saturate, leading to CPU spikes and increased response times (latency).
*   **The Solution:** Implements the **Cache-Aside (Lazy Loading)** caching pattern with Redis. 
    *   **Read Workflow:** When a request arrives, the application checks Redis first (Cache Hit). If the data is absent (Cache Miss), it fetches it from PostgreSQL and stores it in Redis with a configurable Time-To-Live (TTL) for subsequent requests.
    *   **Cache TTL:** Helps balance data freshness and read speed by expiring keys automatically.
*   **Selective Cache Invalidation:**
    *   *Problem:* When data is created, updated, or deleted, the cached version becomes stale.
    *   *Solution:* On write operations (Create/Update/Delete), the system immediately invalidates (deletes) the individual product's cache entry. To handle paginated lists, the system tracks active catalog list cache keys in a Redis Set (`products:list_keys`) and selectively purges them, ensuring absolute consistency.

```mermaid
sequenceDiagram
    autonumber
    Client->>API: GET /api/v1/products/{id}
    API->>Redis: Fetch cache "product:{id}"
    alt Cache Hit
        Redis-->>API: Return Product JSON
        API-->>Client: 200 OK (Product Detail)
    else Cache Miss
        Redis-->>API: Null
        API->>PostgreSQL: Query product by ID
        PostgreSQL-->>API: Product Entity
        API->>Redis: Save Product JSON (with TTL)
        API-->>Client: 200 OK (Product Detail)
    end
```

### 2. Fault-Tolerant Cache Degradation (Fail-Open Caching)
*   **The Problem:** If Redis crashes or experiences network failure, standard caching implementations might throw exceptions, causing API request failures and taking down the catalog service.
*   **The Solution:** The caching layer is wrapped in robust Exception Handling blocks. If Redis goes down, the connection failure is logged and the application automatically falls back to PostgreSQL (fail-open). Customers continue to receive catalog data, albeit with slightly higher latency, ensuring maximum uptime.

### 3. Distributed Rate Limiting (Fixed Window Counter)
*   **The Problem:** Distributed APIs are prone to abuse, Denial of Service (DoS) attempts, or brute-force requests from rogue clients that can crash backend resources.
*   **The Solution:** A Spring MVC `RateLimitInterceptor` checks incoming requests against client IP addresses using a Redis-backed fixed-window counter.
    *   **Algorithm:** Each unique client IP acts as a Redis key (`rate_limit:{ip}`). Upon a request, the value increments atomically. If the key is new, its TTL is set to the rate limit window (e.g., 60 seconds).
    *   **Threshold:** If a client exceeds the limit (e.g., 50 requests/min), the API rejects the request, returning a `429 Too Many Requests` status and a `Retry-After` header.
    *   **Fail-Open Rate Limiting:** If Redis is down, rate limiting fails open to ensure regular traffic is not blocked during cache-infrastructure outages.

```mermaid
flowchart TD
    A[Incoming Request] --> B{Get Client IP}
    B --> C[Increment IP Key in Redis]
    C -->|Redis Connection Fails| D[Fail-Open: Allow Request]
    C -->|Success| E{Count > Max Limit?}
    E -->|Yes| F[Throw RateLimitExceededException - HTTP 429]
    E -->|No| G[Allow Request]
```

### 4. Optimized Database Pagination
*   **The Problem:** Requesting all products from a large catalog loads thousands of records into the JVM heap memory, resulting in high garbage collection overhead and slow response times.
*   **The Solution:** Employs Native SQL limit-offset pagination (`LIMIT :limit OFFSET :offset`) executed directly inside PostgreSQL. This retrieves only the slice of products requested by the client, maintaining low memory footprints.

### 5. Automated Schema Migrations (Flyway)
*   **The Problem:** Manually updating database schemas across local, staging, and production environments is error-prone and leads to schema drift.
*   **The Solution:** Integrates **Flyway**. Upon service startup, Flyway checks the `src/main/resources/db/migration` directory and automatically applies missing versioned migrations (like `V1__init.sql`), guaranteeing database consistency across all environments.

---

## 🛠️ Technology Stack & Dependencies

*   **Java 21** - Modern language features (Virtual Threads, Records, enhanced pattern matching).
*   **Spring Boot 4.1.0** - Core application framework.
*   **Spring Data JPA / Hibernate** - Object-Relational Mapping (ORM) layer.
*   **Spring Security** - Configured for secure access endpoints.
*   **Lettuce & Spring Data Redis** - High-performance reactive Redis driver.
*   **Flyway Database Migrations** - Schema management.
*   **Lombok** - Boilerplate code reduction.
*   **OpenAPI 3 (Swagger UI)** - Automated API documentation.
*   **PostgreSQL 15** - Reliable relational storage.
*   **Docker & Docker Compose** - Containerization & orchestration.

---

## 🚀 Getting Started

### Prerequisites
*   [Docker](https://www.docker.com/products/docker-desktop/) and Docker Compose installed.
*   *Alternatively, if running locally:* Java 21 JDK and Maven 3.9+ installed, along with local PostgreSQL and Redis servers running.

### 1. Environment Configuration
Create a `.env` file in the root directory (based on `.env.example`):
```properties
# Database Configuration
DATABASE_URL=postgresql://catalog_user:securepassword@db:5432/product_catalog

# Redis Configuration
REDIS_URL=redis://redis:6379/0

# Application Configuration
PORT=8080

# Caching and Rate Limiting
CACHE_TTL_SECONDS=60
RATE_LIMIT_MAX_REQUESTS=50
RATE_LIMIT_WINDOW_SECONDS=60
```

### 2. Running with Docker Compose (Recommended)
You can start the entire infrastructure (Spring Boot API, PostgreSQL Database, and Redis Cache) with a single command:
```bash
docker-compose up --build
```
This command:
1.  Downloads PostgreSQL and Redis images and runs them.
2.  Applies the health checks to ensure DB and Redis are fully operational before launching the API.
3.  Initializes PostgreSQL using the `init.sql` seed script and Flyway migrations.
4.  Launches the Spring Boot catalog API on port `8080`.

To stop the services:
```bash
docker-compose down -v
```

### 3. Running Locally (Development Mode)
If you want to run the Java application directly on your host machine while using Docker only for dependencies (DB and Redis):

1.  Start Postgres and Redis:
    ```bash
    docker-compose up db redis
    ```
2.  Update the `DATABASE_URL` and `REDIS_URL` in `.env` to point to `localhost`:
    ```properties
    DATABASE_URL=postgresql://catalog_user:securepassword@localhost:5432/product_catalog
    REDIS_URL=redis://localhost:6379/0
    ```
3.  Run the Spring Boot application:
    ```bash
    mvn spring-boot:run
    ```

---

## 📖 API Endpoints & Usage

Once the application is running, you can access the interactive Swagger UI API documentation at:
👉 **[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)**

### Core Product Endpoints

| Method | Endpoint | Description | Cache Strategy |
|---|---|---|---|
| `POST` | `/api/v1/products` | Create a new product | Invalidates List Cache |
| `GET` | `/api/v1/products` | Get list of products (paginated) | Cached (`products:list:limit:offset`) |
| `GET` | `/api/v1/products/{id}` | Get product details by ID | Cached (`product:{id}`) |
| `PUT` | `/api/v1/products/{id}` | Update an existing product | Invalidates Product & List Cache |
| `DELETE` | `/api/v1/products/{id}`| Delete a product | Invalidates Product & List Cache |

### Request/Response Payload Examples

#### 1. Create a Product
*   **Request:** `POST /api/v1/products`
```json
{
  "name": "Mechanical Keyboard",
  "description": "Tactile brown switches, RGB backlit, USB-C",
  "price": 89.99,
  "stock_quantity": 120
}
```
*   **Response:** `201 Created`
```json
{
  "id": "a9c7b8d4-5390-4c48-842e-8d96d2b512c1",
  "name": "Mechanical Keyboard",
  "description": "Tactile brown switches, RGB backlit, USB-C",
  "price": 89.99,
  "stock_quantity": 120,
  "created_at": "2026-06-30T11:00:00",
  "updated_at": "2026-06-30T11:00:00"
}
```

#### 2. Get Product List (With Pagination)
*   **Request:** `GET /api/v1/products?limit=2&offset=0`
*   **Response:** `200 OK`
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Ergonomic Keyboard",
    "description": "Wireless split keyboard",
    "price": 129.99,
    "stock_quantity": 45,
    "created_at": "2026-06-30T10:00:00",
    "updated_at": "2026-06-30T10:00:00"
  },
  {
    "id": "650e8400-e29b-41d4-a716-446655440001",
    "name": "HD Monitor",
    "description": "27 inch 4K display",
    "price": 349.50,
    "stock_quantity": 12,
    "created_at": "2026-06-30T10:00:00",
    "updated_at": "2026-06-30T10:00:00"
  }
]
```

---

## 🛠️ Verification & Monitoring

### 1. Actuator Endpoints
For health checks and production monitoring, Spring Boot Actuator is enabled.
*   **Health Status:** `GET http://localhost:8080/actuator/health`
*   **Info:** `GET http://localhost:8080/actuator/info`

### 2. Checking Cache with Redis CLI
You can inspect the cache hits/misses and the stored keys by entering the Redis container:
```bash
docker exec -it <redis-container-id> redis-cli
```
Inside the CLI, list current cache keys:
```bash
127.0.0.1:6379> keys *
1) "product:550e8400-e29b-41d4-a716-446655440000"
2) "products:list:10:0"
3) "products:list_keys"
```
Or check the rate limits:
```bash
127.0.0.1:6379> keys rate_limit:*
1) "rate_limit:172.18.0.1"
```
