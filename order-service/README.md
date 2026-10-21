# Order Service

Java 17 / Spring Boot 3.5.6 microservice for checkout and order history. Default port: **8084**. MongoDB collection: `orders`.

## Features
- Stateless JWT validation using the same Base64 HMAC secret as Auth/Cart Service (JJWT 0.12.6).
- Authenticated checkout sourced from Cart Service; authoritative product name/price and availability fetched from Product Service.
- Backend `BigDecimal` calculations and persisted order-item snapshots.
- User-scoped list/detail/cancel operations; cancel only `PLACED` orders.
- IP-based in-memory fixed-window rate limiter, JSON errors and `Retry-After`.
- Optional `Idempotency-Key`, enforced by a MongoDB unique sparse index on a user-scoped lookup key. Send the same key when retrying the same checkout.
- Explicit `cartCleared` and `checkoutMessage` fields expose the non-atomic order-save/cart-clear outcome.

## Assumptions to verify with teammates
1. Auth Service signs JWTs with an HMAC algorithm compatible with the shared secret and uses the Base64 value in `JWT_SECRET_BASE64`; `sub` is the user ID.
2. `GET /api/cart` returns `{ "items": [{ "productId": "...", "productName": "...", "quantity": 2 }] }`. Extra fields are ignored; aliases for product ID/name are included in `CartResponse`.
3. `DELETE /api/cart` clears the authenticated user's cart and accepts the forwarded Bearer token.
4. Product Service exposes `GET /api/products/{productId}` with JSON fields `productId` (or `id`), `name` (or `title`/`productName`), `price`, optional `available`, optional `stock`. Adjust `ProductResponse` aliases if your teammate differs.
5. Product Service's `price` is the current authoritative unit price. The service does not invent inventory reservations or stock decrements.

## Checkout consistency and limitations
Saving an order and clearing a cart are separate remote/database operations, not a distributed transaction. If saving succeeds but clearing fails, the service returns `201` with the persisted order, `cartCleared:false`, and a warning in `checkoutMessage`; it does not report that the order failed. The unique MongoDB index protects concurrent duplicate requests that use the same `Idempotency-Key` for the same user. For reliable retries after partial success, clients must reuse that key. Without a key, duplicate checkout cannot be prevented. A repeated key returns the original order; it does not automatically attempt cart clearing again. Payment, refund processing, inventory reservation, and stock decrement are not implemented. Rate limits are process-local and are not shared between replicas.

## Configuration
Copy `.env.example` as a reference only. Spring Boot does **not** automatically load `.env`; export variables in PowerShell or set them in IntelliJ. Never commit secrets.

PowerShell (in the same terminal session):
```powershell
$env:SERVER_PORT = "8084"
$env:MONGODB_URI = "mongodb+srv://<username>:<password>@<cluster-host>/?retryWrites=true&w=majority"
$env:MONGODB_DATABASE = "ecommerce"
$env:JWT_SECRET_BASE64 = "<exact Base64 secret used by Auth Service>"
$env:CART_SERVICE_URL = "http://localhost:8083"
$env:PRODUCT_SERVICE_URL = "http://localhost:8081"
$env:RATE_LIMIT_REQUESTS = "60"
$env:RATE_LIMIT_SECONDS = "60"
mvn clean test
mvn spring-boot:run
```
Configure Atlas network access and a database user before starting. In IntelliJ: **Run > Edit Configurations > Environment variables**, add the same variables. URL-encode special characters in MongoDB URI credentials as needed. Never put a real secret in source control.

## Build
Requires Maven and JDK 17:
```powershell
mvn clean test
mvn spring-boot:run
```
`GET http://localhost:8084/health` should return `{"status":"UP","service":"order-service"}`. Health is public; every other route requires Bearer JWT.

## API
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/health` | No | Health check |
| POST | `/api/orders` | Yes | Empty body; checkout current user's cart |
| GET | `/api/orders` | Yes | Current user's orders, newest first |
| GET | `/api/orders/{orderId}` | Yes | Own order only; other user's order is 404 |
| PATCH | `/api/orders/{orderId}/cancel` | Yes | Only `PLACED` can be cancelled |

Error body: `{"timestamp":"...","status":409,"error":"EMPTY_CART","message":"The cart is empty."}`. Expected statuses include 400, 401, 404, 409, 422, 429, 500, 502 and 503.

## Postman guide
Set collection variables:
- `baseUrl`: `http://localhost:8084`
- `token`: accessToken returned by `POST http://localhost:8082/api/auth/login`
- `orderId`: order `id` returned by checkout

For each protected request choose **Authorization > Bearer Token** and use `{{token}}`.

1. **Health** — `GET {{baseUrl}}/health`; expect `200` and status UP. No token.
2. **Checkout** — `POST {{baseUrl}}/api/orders`; no body; add `Idempotency-Key: checkout-001`; expect `201`. Response contains `items`, `totalAmount`, `status`, `cartCleared`. Check total equals sum of subtotals and cart is empty after success. Save `id` as `orderId`.
3. **List** — `GET {{baseUrl}}/api/orders`; expect `200` and array; empty history is `[]`.
4. **Detail** — `GET {{baseUrl}}/api/orders/{{orderId}}`; expect `200`. Use a different user's order ID to verify it returns `404`.
5. **Cancel** — `PATCH {{baseUrl}}/api/orders/{{orderId}}/cancel`; no body; expect `200` and status `CANCELLED`. Repeat and expect `409`. Try cancelling another user's ID and expect `404`.
6. **Missing token** — repeat any protected request with no Authorization header; expect `401` JSON.
7. **Invalid/expired token** — use a malformed token or expired Auth token; expect `401`.
8. **Empty cart** — checkout with an empty cart; expect `409 EMPTY_CART`, and verify no order was saved.
9. **Invalid product/quantity** — place a malformed cart item or make a product unavailable; expect `409` for invalid cart data or `422` for missing/unavailable product.
10. **Remote services down** — stop Cart Service; checkout should return `503`. Stop Product Service; checkout should return `503`.
11. **Idempotency retry** — repeat checkout with the *same* `Idempotency-Key`; it returns the original order instead of creating another. Do not test retries without the key as duplicate-safe.
12. **Rate limit** — send more than `RATE_LIMIT_REQUESTS` requests from the same client IP within `RATE_LIMIT_SECONDS`; excess requests return `429` and `Retry-After`.

Example checkout response:
```json
{
  "id": "MongoDB_ORDER_ID",
  "userId": "MONGODB_USER_ID",
  "items": [{"productId":"P101","productName":"Wireless Mouse","quantity":2,"unitPrice":799.00,"subtotal":1598.00}],
  "totalAmount": 1598.00,
  "status": "PLACED",
  "createdAt": "2026-10-20T14:30:00Z",
  "updatedAt": "2026-10-20T14:30:00Z",
  "cartCleared": true,
  "checkoutMessage": "Order placed and cart cleared."
}
```

## Add to existing Git repository (PowerShell)
```powershell
Set-Location "E:\Sem5\SD-lab\Semester-Project\scalable-ecommerce"
git status
git branch --show-current
# Copy this order-service directory into this repository; do not replace other folders.
git status
git add -- order-service
git diff --cached --stat
git diff --cached
git commit -m "Add order service"
git push -u origin (git branch --show-current)
```
If your team requires a feature branch or pull request, create/switch to the agreed branch first and push that branch. Inspect `git status` before staging; stage only `order-service` so teammate changes are not included.
