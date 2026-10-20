# ScaleShop · Web Crawler & Real-Time Price Comparison Microservice
**Member 5 — Web Crawler, Competitor Price Normalization & Performance Evaluation**

---

## 1. Executive Summary & Architecture

The **Web Crawler Microservice** is a high-throughput, fault-tolerant Spring Boot component within the **ScaleShop** distributed e-commerce platform. It provides real-time competitor price intelligence, multi-retailer search, conservative product matching, distributed request coordination, and comprehensive system performance metrics.

### System Architecture Diagram
```
                     +---------------------------------------------+
                     |         ScaleShop React Frontend            |
                     |  (/compare & ProductDetail Competitor View) |
                     +----------------------+----------------------+
                                            |
                                            | HTTP REST API
                                            v
+------------------------------------------------------------------------------------+
|                  Web Crawler Microservice (Spring Boot · Port 8081)                 |
|                                                                                    |
|  [REST Controllers]                                                                |
|    - PriceComparisonController (/api/prices/*)                                     |
|    - CrawlerController (/api/crawler/*)                                            |
|    - MetricsController (/api/metrics, /api/health)                                 |
|                                                                                    |
|  [Core Services]                                                                   |
|    - PriceComparisonService (Multi-source parallel orchestration via WebClient)    |
|    - ProductNormalizerService (Conservative token matching & variant isolation)   |
|    - BackgroundCrawlerService (Scheduled & manual background crawl jobs)           |
|                                                                                    |
|  [Request Management & Resiliency Layer]                                           |
|    - RedisCacheService (TTL: 60s, graceful bypass if Redis offline)               |
|    - RedisRateLimiterService (Atomic Lua sliding window: 5 RPS per retailer)       |
|    - DuplicateCrawlLockService (Distributed single-flight lock with owner token)   |
|    - MetricsCollectorService (Micrometer Prometheus + SystemLab JSON output)       |
+-------------------+-----------------------------------+----------------------------+
                    |                                   |
         Redis (Port 6379)                              | Non-blocking HTTP (WebClient)
  - Search Cache (TTL: 60s)                             v
  - Rate Limit Sorted Sets        +-----------------------------------------------+
  - Single-Flight Locks (TTL: 15s)|             Retailer Sources Layer            |
  - Job Status Hashes (TTL: 1h)   |                                               |
                                  | [Simulated Competitor Services (Node.js/Exp)] |
                                  |   - Amazon India Mock       (Port 8085)       |
                                  |   - Flipkart Mock           (Port 8086)       |
                                  |   - Meesho Mock             (Port 8087)       |
                                  |   - Swiggy Instamart Mock   (Port 8088)       |
                                  |                                               |
                                  | [Live Authorized External API]                |
                                  |   - Open Food Facts API     (Live Open Data)  |
                                  +-----------------------------------------------+
```

---

## 2. Port & Service Matrix

| Service | Technology | Port | Purpose / Role |
| :--- | :--- | :--- | :--- |
| **web-crawler-service** | Spring Boot 3.3.4 (Java 17) | `8081` | Core crawler, normalizer, and comparison REST API |
| **redis** | Redis Alpine 7.x | `6379` | Search caching, sliding-window rate limits, distributed locks |
| **amazon-mock** | Node.js Express | `8085` | Simulates Amazon India search API & HTML catalog |
| **flipkart-mock** | Node.js Express | `8086` | Simulates Flipkart search API & HTML catalog |
| **meesho-mock** | Node.js Express | `8087` | Simulates Meesho search API & HTML catalog |
| **instamart-mock** | Node.js Express | `8088` | Simulates Swiggy Instamart search API & HTML catalog |
| **open-food-facts** | Public REST API | `443` | Authorized live food & FMCG product catalog |

---

## 3. Product Normalization & Conservative Matching Algorithm

To eliminate false positives across retailers with differing title conventions:
1. **Title Tokenization & Stop-Word Removal**: Strips promotional phrases (`"Deal of the day"`, `"Refurbished"`, `"Special Price"`) and punctuation.
2. **Strict Generation Matching**: Prevents pairing different generations (e.g., `AirPods Pro (2nd Gen)` vs `AirPods Pro (1st Gen)` or `AirPods (3rd Gen)`).
3. **Storage / Variant Strictness**: Distinguishes variant capacities (`128GB` vs `256GB` vs `512GB`) so lower capacity prices do not inaccurately undercut higher capacity items.
4. **Normalized Offer Model**: Extracts price, original list price, discount percentage, delivery estimate, rating, review count, in-stock status, and canonical product URL.

---

## 4. Redis Caching & Distributed Request Management

### Key Naming Conventions & TTL
- **Search Query Cache**: `crawler:search:<normalized_query>` (TTL: **60 seconds**)
- **Single-Flight Crawl Lock**: `crawler:lock:<normalized_query>` (TTL: **15 seconds**, released via Lua token match)
- **Source Rate Limiters**: `crawler:ratelimit:<retailer_id>` (Sliding window sorted set, 5 requests per 1000ms)
- **Background Jobs**: `crawler:job:<job_id>` (Hash storage, TTL: **3600 seconds**)

### Lua Script Atomic Protections
1. **Atomic Sliding Window Rate Limiting**:
   ```lua
   -- Removes old timestamps, counts active requests in 1000ms window, adds new timestamp if under limit
   redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1] - ARGV[2])
   local current = redis.call('ZCARD', KEYS[1])
   if current < tonumber(ARGV[3]) then
       redis.call('ZADD', KEYS[1], ARGV[1], ARGV[4])
       redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[2]))
       return 1
   else
       return 0
   end
   ```
2. **Safe Lock Release**:
   ```lua
   -- Only releases lock if value matches token to prevent releasing locks taken by another process
   if redis.call('get', KEYS[1]) == ARGV[1] then
       return redis.call('del', KEYS[1])
   else
       return 0
   end
   ```
3. **Graceful Redis Degradation**:
   If Redis becomes unavailable, `RedisCacheService` automatically catches exceptions, logs warnings, and passes requests directly to live fetchers without service downtime.

---

## 5. API Reference & Sample Requests

### 1. Multi-Retailer Price Search
`GET /api/prices/search`

**Parameters:**
- `q` (required): Search keyword (e.g., `AirPods`, `iPhone 15`, `Mixer Grinder`)
- `minPrice` (optional): Minimum price filter
- `maxPrice` (optional): Maximum price filter
- `inStockOnly` (optional, default: `false`): Filter out-of-stock items
- `refresh` (optional, default: `false`): Bypass cache and force fresh crawl
- `limit` (optional, default: `20`): Maximum results to return

**Header Options:**
- `X-Bypass-Cache: true`: Forces bypass of the Redis cache for benchmarking.

**Sample Request:**
```bash
curl -X GET "http://localhost:8081/api/prices/search?q=AirPods"
```

**Sample Response:**
```json
{
  "query": "airpods",
  "totalMatches": 4,
  "sourcesPolled": ["amazon", "flipkart", "meesho", "instamart", "openfoodfacts"],
  "sourcesSucceeded": ["amazon", "flipkart", "meesho", "instamart"],
  "sourcesFailed": [],
  "cached": false,
  "executionTimeMs": 142,
  "products": [
    {
      "id": "prod_apple_airpods_pro_2nd_gen_mag_491a9217",
      "canonicalTitle": "Apple AirPods Pro (2nd Gen) with MagSafe Case (USB-C)",
      "brand": "Apple",
      "model": "AirPods Pro",
      "generation": "2nd gen",
      "bestPrice": 18990.0,
      "bestSource": "Amazon India",
      "offersCount": 3,
      "offers": [
        {
          "sourceId": "amazon",
          "sourceName": "Amazon India",
          "retailerProductTitle": "Apple AirPods Pro (2nd Generation) with MagSafe Case (USB-C)",
          "price": 18990.0,
          "originalPrice": 24900.0,
          "discountPercent": 24,
          "currency": "INR",
          "availability": true,
          "rating": 4.7,
          "reviewCount": 14250,
          "deliveryInfo": "Next-day delivery with Prime",
          "productUrl": "http://localhost:8085/p/amz-airpods-pro-2",
          "simulated": true,
          "crawledAt": "2026-10-19T23:14:00Z"
        },
        {
          "sourceId": "flipkart",
          "sourceName": "Flipkart",
          "retailerProductTitle": "Apple AirPods Pro (2nd Gen) with MagSafe Case (USB-C)",
          "price": 19490.0,
          "originalPrice": 24900.0,
          "discountPercent": 22,
          "currency": "INR",
          "availability": true,
          "rating": 4.6,
          "reviewCount": 9820,
          "deliveryInfo": "Delivery in 2 days",
          "productUrl": "http://localhost:8086/p/fk-airpods-pro-2",
          "simulated": true,
          "crawledAt": "2026-10-19T23:14:00Z"
        }
      ]
    }
  ]
}
```

---

### 2. Product Detail Competitor Prices
`GET /api/prices/{product_id}`

Returns prices tailored for the ScaleShop product detail view (`CompetitorPriceDto`):
```bash
curl -X GET "http://localhost:8081/api/prices/prod-1"
```
**Response:**
```json
[
  {
    "source": "Amazon India",
    "price": 18990.0,
    "available": true,
    "checkedAt": "2026-10-19T23:14:00Z",
    "simulated": true
  },
  {
    "source": "Flipkart",
    "price": 19490.0,
    "available": true,
    "checkedAt": "2026-10-19T23:14:00Z",
    "simulated": true
  }
]
```

---

### 3. Background Crawler Management
- **Trigger Crawl**: `POST /api/crawler/run` (Body: `{"targets": ["AirPods", "iPhone 15"], "stores": ["amazon", "flipkart"]}`)
- **Crawler Health & Queue Status**: `GET /api/crawler/status`
- **Job Status**: `GET /api/crawler/jobs/{job_id}`

---

### 4. Metrics & ScaleShop SystemLab Compatibility
- `GET /api/metrics`: ScaleShop `SystemMetricsDto` format (`{ source, simulated, results: [...] }`) with Prometheus and JVM metrics.
- `GET /api/health`: Comprehensive health check validating Spring Boot, Redis connectivity, and all 4 mock competitor endpoints.

---

## 6. How to Run Locally

### Prerequisites
- Java 17+ (JDK)
- Maven 3.8+
- Node.js 18+
- Redis (running locally on port 6379, or via Docker: `docker run -d -p 6379:6379 redis:alpine`)

### Step 1: Start Mock Competitor Stores
```powershell
cd web_crawler/mock_stores
npm install
node server.js
```
*Stores will listen on ports 8085, 8086, 8087, and 8088.*

### Step 2: Start the Web Crawler Service
```powershell
cd web_crawler
mvn spring-boot:run
```
*Crawler service will run on `http://localhost:8081`.*

### Step 3: Run Automated Tests
```powershell
cd web_crawler
mvn test
```

---

## 7. How to Run with Docker Compose

To orchestrate Redis, all 4 mock competitor stores, and the Crawler service simultaneously:

```bash
cd web_crawler
docker compose up --build
```

---

## 8. Automated Performance Benchmarks & JMeter Tests

### Running the Python Load Runner
Simulates Low (10 users), Medium (100 users), High (500 users), cold cache, warm cache, and direct upstream crawl stress:
```bash
python web_crawler/benchmarks/benchmark_runner.py
```
This generates:
- `web_crawler/benchmarks/benchmark_results.json` (ScaleShop SystemLab schema)
- `web_crawler/benchmarks/benchmark_results.csv`
- Publication-ready charts in `web_crawler/benchmarks/graphs/`:
  - `latency_percentiles.png`
  - `throughput_rps.png`
  - `cache_performance.png`
  - `system_resilience.png`

### Running Apache JMeter
Open `web_crawler/benchmarks/jmeter/crawler_load_test.jmx` in GUI mode or run headless:
```bash
jmeter -n -t web_crawler/benchmarks/jmeter/crawler_load_test.jmx -l results.jtl -e -o report/
```

---

## 9. Frontend Integration

A full-featured price comparison dashboard is integrated into ScaleShop:
- **Component**: [`frontend/src/features/PriceComparison.tsx`](file:///frontend/src/features/PriceComparison.tsx)
- **Route**: `http://localhost:5173/compare` (lazily loaded via [`App.tsx`](file:///frontend/src/app/App.tsx))
- **Navigation**: "Compare prices" link in header and footer of [`Layout.tsx`](file:///frontend/src/components/Layout.tsx).
- **Design Tokens**: Styled using ScaleShop's existing TailwindCSS utility system, with visual indicators for lowest prices, savings calculations, stock badges, and fresh vs cached response indicators.
