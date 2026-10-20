# Member 2 — Product Search and Consistent Hashing

Standalone Java 17 / Spring Boot microservice. This module is based on the uploaded `scalable-ecommerce.zip`: the repository currently contains `authentication-service` and `frontend`, but no product backend. This module uses the same JWT contract as that auth service and stores product documents on **independent MongoDB Atlas deployments**. It contains no frontend and does not use Docker for MongoDB.

## What it implements
- Product browsing and text search (name, description, category, brand).
- Consistent hashing with virtual nodes, using product ID as the key.
- Product ID lookup routes to one shard; text search queries every active shard and merges results.
- Category is a searchable product field; it is not the shard key.
- Optional Redis response caching (5-minute TTL, best-effort if Redis is not running).
- Add/remove physical Atlas shards through authenticated Postman requests, with product redistribution and movement metrics.
- GET browse/search/health are public. Product writes and shard management require a valid JWT.

## Important architecture notes
This is **application-level sharding across independent Atlas deployments**, not MongoDB's native sharded-cluster feature. Each shard is a separate Atlas deployment and the Java service owns routing and migration. Creating multiple databases on the same Atlas deployment does not create independent physical shards.

The runtime shard registry is held in memory. On restart, only the shards configured by environment variables are registered. A shard added through Postman must be re-added after restart unless its URI is configured as an initial shard. The demo migration copies a product to its new owner then deletes the old copy. Pause product writes while adding/removing nodes; this is not a fully transactional live-migration protocol. Use only test data during the demo.

## 1. Configure Atlas
For each initial shard, create an independent Atlas deployment, a database user with read/write access, and add your machine's current public IP under Network Access. Avoid `0.0.0.0/0`. Copy the Java driver URI (`mongodb+srv://...`) and URL-encode special characters in the password. Atlas free-tier deployment limits depend on the current plan and project restrictions.

Set environment variables in PowerShell (replace placeholders):
```powershell
$env:MONGO_SHARD1_URI = "mongodb+srv://USER:PASSWORD@CLUSTER1_HOST/products?retryWrites=true&w=majority"
$env:MONGO_SHARD1_DATABASE = "products"
$env:MONGO_SHARD2_URI = "mongodb+srv://USER:PASSWORD@CLUSTER2_HOST/products?retryWrites=true&w=majority"
$env:MONGO_SHARD2_DATABASE = "products"
$env:MONGO_SHARD3_URI = "mongodb+srv://USER:PASSWORD@CLUSTER3_HOST/products?retryWrites=true&w=majority"
$env:MONGO_SHARD3_DATABASE = "products"
```
If you have fewer initial clusters, leave unused URI variables blank. At least one shard is required at startup. Startup pings each configured deployment, so URI, credentials, allowlist, or connectivity errors prevent that shard from registering.

## 2. Configure JWT validation
The team's auth service uses `JWT_SECRET_BASE64`, signs JWTs, and puts the user ID in `sub`. Set the product service's `JWT_SECRET_BASE64` to the **exact same value** used by authentication-service. Do not generate a different secret if the auth service is already issuing tokens. Never commit the real key.

If both services are being configured from scratch, generate one secret in PowerShell and configure the same result in both:
```powershell
$key = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($key)
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($key)
```

## 3. Run the service
Requirements: Java 17 and Maven. Start Redis separately if you want caching (defaults to `localhost:6379`); search still works if Redis is unavailable. From this directory:
```powershell
$env:SERVER_PORT = "8085"
$env:JWT_SECRET_BASE64 = "PASTE_THE_SAME_SECRET_USED_BY_AUTH"
mvn spring-boot:run
```
The auth service runs at `http://localhost:8082`; this product service runs at `http://localhost:8085`.

## 4. Postman workflow
### Get a token from the auth service
If needed, register:
`POST http://localhost:8082/api/auth/register`
```json
{"name":"Demo User","email":"demo@example.com","password":"DemoPassword123"}
```
Then log in:
`POST http://localhost:8082/api/auth/login`
```json
{"email":"demo@example.com","password":"DemoPassword123"}
```
Copy the returned `accessToken`. In Postman, select **Authorization → Bearer Token** and paste it for protected requests. The product service verifies the token signature and expiry using the shared secret; it does not call the auth service on every request or check whether a user has since been deleted.

### Check service / shards
- `GET http://localhost:8085/api/health` — public.
- `GET http://localhost:8085/api/shards` — Bearer JWT required.
- `GET http://localhost:8085/api/shards/distribution` — Bearer JWT required; returns product counts.

### Add a product
`POST http://localhost:8085/api/products` — Bearer JWT required, Body → raw → JSON:
```json
{
  "id": "P2001",
  "name": "ThinkPad Laptop",
  "description": "Business laptop with 16 GB RAM",
  "category": "Laptops",
  "brand": "Lenovo",
  "price": 74999,
  "stock": 12
}
```
Response includes `storedOnShard`. Product IDs must be unique.

### Search
- `GET http://localhost:8085/api/products`
- `GET http://localhost:8085/api/search?q=laptop`
- `GET http://localhost:8085/api/search?q=phone`
- `GET http://localhost:8085/api/products/P2001`

### Add a new Atlas shard
Create a **new independent Atlas deployment**, set up its DB user/IP allowlist, then send `POST http://localhost:8085/api/shards` with Bearer JWT and raw JSON:
```json
{
  "name": "shard4",
  "uri": "mongodb+srv://USER:PASSWORD@NEW_CLUSTER_HOST/products?retryWrites=true&w=majority",
  "database": "products"
}
```
The API connects and pings the deployment, adds it to the ring, re-routes products whose owner changed, and reports `productsMoved`, `movementPercent`, and distribution. The request contains a database credential; do not commit or share an exported Postman collection with the real URI.

### Remove a shard
`DELETE http://localhost:8085/api/shards/shard4` with Bearer JWT. Products on that shard are moved to their new ring owners before the node is unregistered. The last active shard cannot be removed. Then check `GET /api/shards/distribution` and search for products to confirm they remain available.

## Endpoints
| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/health` | No | Health |
| GET | `/api/products` | No | Browse all products |
| GET | `/api/products/{id}` | No | Direct ID lookup via hash ring |
| GET | `/api/search?q=...` | No | Search all shards |
| POST | `/api/products` | Yes | Insert product on hash-selected shard |
| GET | `/api/shards` | Yes | Shard details and counts |
| GET | `/api/shards/distribution` | Yes | Product counts per shard |
| POST | `/api/shards` | Yes | Add shard and rebalance |
| DELETE | `/api/shards/{name}` | Yes | Remove shard and migrate products |

## Security / prototype limitations
Shard management is JWT-authenticated but not role-restricted; any valid token from the shared issuer can call it. For a real deployment, require an admin role. Migration does not coordinate concurrent writes and does not provide distributed transactions, replication, or automatic failover. Do not use production data for the demo. Redis caching is best-effort and invalidated after product writes and shard changes.
