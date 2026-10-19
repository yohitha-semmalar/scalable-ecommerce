# ScaleShop API contract — original and service profiles

All 12 agreed method/path pairs are preserved below. **Payloads, response shapes, authentication policy and extension paths are proposed until accepted by their backend owners.** The frontend mock and HTTP adapters implement this contract. No real service has been connected or verified.

## Screenshot endpoints added — 19 October update

All original endpoints are retained. `VITE_API_CONTRACT=services` selects the new screenshot paths and payloads; `legacy` selects the original contract. No automatic fallback or duplicate POST is attempted when an endpoint fails.

| Method/path | Owner | Request / frontend support |
|---|---|---|
| POST `/api/auth/register` | Member 3, auth port 8082 | `{name,email,password}`; new auth adapter |
| POST `/api/auth/login` | Member 3, auth port 8082 | `{email,password}`; token adapter |
| GET `/api/auth/me` | Member 3, auth port 8082 | Profile lookup with Bearer token |
| GET `/api/cart` | Member 3, cart port 8083 | Retrieve shared cart |
| POST `/api/cart/items` | Member 3, cart port 8083 | `{productId,quantity}`; no `operation` in services profile |
| PATCH `/api/cart/items/{productId}` | Member 3, cart port 8083 | `{quantity}`; default update in services profile |
| DELETE `/api/cart/items/{productId}` | Member 3, cart port 8083 | Remove item; default in services profile |
| DELETE `/api/cart` | Member 3, cart port 8083 | Clear all items; confirmed Clear bag button; mock also supported |
| POST `/api/orders` | Member 3, order port 8084 | Exact screenshot shipping payload below; sends Idempotency-Key |
| GET `/api/orders` | Member 3, order port 8084 | Current customer history |
| GET `/api/orders/{orderId}` | Member 3, order port 8084 | Order detail |

```json
{
  "shippingAddress": {
    "fullName":"Test User", "phone":"9876543210", "line1":"12 Main Road",
    "city":"Chennai", "state":"Tamil Nadu", "postalCode":"600001"
  }
}
```

The new profile omits the legacy `paymentMethod` and shipping `email` fields from this POST. Checkout collects state/union territory and maps internal `name` → `fullName`, `address` → `line1`. Order responses using these fields are normalized back for the existing UI; older stored mock orders remain readable.

**Response contracts still need confirmation:** screenshots provide request bodies, not response bodies. Supported authentication response is `{token,user?}` or `{accessToken,user?}` where user is `{id,name,email}`. Token-only login fetches `/api/auth/me`, expected to return a direct User. Registration without a token performs one login request; login failure reports that registration already succeeded. No tokens are persisted to localStorage/sessionStorage. Reloading in bearer mode requires signing in again; refresh-token/session restoration is not invented. Cookie mode is still available with `VITE_AUTH_MODE=cookie`.

Bearer logout clears the frontend token only; no revocation endpoint was supplied. The original `POST /api/logout` remains available as `httpAdapter.logoutOnServer()` and is used automatically in cookie mode. A 401 from the active authenticated request clears the token and UI session.

Cart mutations accept a complete Cart response, or an empty/204 response followed by GET `/api/cart`. Other response envelopes need normalization once supplied. Order response fields other than the address still follow the original proposed Order schema.

Optional `VITE_AUTH_BASE_URL`, `VITE_CART_BASE_URL`, `VITE_ORDER_BASE_URL` accept origins only, e.g. `http://localhost:8082`; leave them blank to use `VITE_API_BASE_URL`. The deployed mock app does not contact localhost. Cross-origin local development needs backend CORS, including Authorization and Idempotency-Key; an HTTPS-hosted frontend cannot generally call unsecured HTTP services as if they were a hosted backend.

Explicit exports preserve both implementations: `legacyAuthApi`, `serviceAuthApi`, `legacyCartApi`, `serviceCartApi`. The normal `api` facade selects the configured profile in HTTP mode and remains mock-backed in demo mode.

## Conventions

- One gateway origin, configured by `VITE_API_BASE_URL`; no internal service ports in components.
- JSON requests/responses except the redirect endpoint. Monetary amounts are numeric INR rupees, not formatted strings. Identifiers are strings. Dates are ISO 8601 UTC.
- Product listing uses `q`, `category`, `min`, `max`, `stock=true`, `sale=true`, `sort=relevance|newest|price-asc|price-desc`, `page` (1-based), `limit` (default 12). Response `{items: Product[], total: number}`. Filtering and sorting occur before pagination. Relevance in mock mode preserves seeded order after case-insensitive substring filtering.
- Services profile defaults to in-memory Bearer authentication and `credentials: omit`. Legacy profile defaults to `credentials: include` cookie sessions. Expected server cookies are HttpOnly, Secure and use an appropriate SameSite policy. Neither HTTP profile persists tokens to browser storage.
- Authenticated operations require backend ownership checks. Frontend protected routes are navigation conveniences, not authorization.
- Proposed error: `{ "error": { "code": "OUT_OF_STOCK", "message": "Only 2 available." } }`; `{message}` is also accepted. Statuses: 400 validation/invalid credentials, 401 session required, 403 forbidden, 404 missing, 409 duplicate/conflict/stock, 429 rate limit, 503 unavailable.
- Rate limits return `Retry-After` seconds or HTTP date. Optional `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`. Cross-origin gateways must expose these headers.
- GETs accept AbortSignal; all HTTP calls have configurable timeout (default 10 seconds). Automatic retries are disabled, especially order POSTs; UI offers explicit retry. Cart mutations and auth have disabled pending controls. Checkout adds an in-flight ref guard.
- `Idempotency-Key` is a request nonce, not an order ID. The order service must atomically reserve stock, persist the order and record the key, scoped to authenticated customer. Replays return the original order. Proposed retention: at least 24 hours; owner must finalize.

## Domain schemas

The exact TypeScript interfaces are in `src/types/index.ts`.

```ts
type User = { id: string; name: string; email: string };
type Product = {
  id: string; name: string; category: string; price: number;
  originalPrice?: number; description: string; images: string[];
  stock: number; specifications: Record<string,string>;
  createdAt: string; featured: boolean;
};
type CartItem = { product: Product; quantity: number };
type Cart = { items: CartItem[]; subtotal: number; shipping: number; tax: number; total: number };
type Address = { name: string; email: string; phone: string; address: string; city: string; state?: string; postalCode: string };
type Order = Cart & {
  id: string; customerId: string; createdAt: string;
  status: string; shippingAddress: Address;
};
type ShortLink = { code: string; url: string; productId: string };
type CompetitorPrice = { source: string; price: number; available: boolean; checkedAt: string; simulated: boolean };
```

## Original agreed endpoints (all retained)

All rows: mock complete; HTTP adapter complete; real integration **pending**.

| Method/path | Owner | Authentication | Request | Success response |
|---|---|---|---|---|
| POST `/api/register` | Member 3 | Public, rate limited | `{name,email,password}` | 201 `{user:User}`; session established |
| POST `/api/login` | Member 3 | Public, rate limited | `{email,password}` | 200 `{user:User}`; session established |
| GET `/api/products` | Member 2 | Public | Listing query above | 200 `{items:Product[],total}` |
| GET `/api/search?q=lap` | Member 2 | Public | Same query/filter convention | 200 `{items:Product[],total}` |
| GET `/api/suggestions?q=lap` | Member 4 | Public | `q`, debounced 280 ms | 200 `[{id,label,productId?}]` |
| POST `/api/cart/items` | Member 3 | Guest session or customer, pending decision | `{productId,quantity,operation:'increment'|'set'}` | 200 complete `Cart` |
| GET `/api/cart` | Member 3 | Guest session or customer | None | 200 `Cart` |
| POST `/api/orders` | Member 3 | Customer required | `{shippingAddress:Address,paymentMethod:'demo-no-payment'}` + `Idempotency-Key` | 201 `Order` with service-generated ID |
| POST `/api/share/{product_id}` | Member 4 | Public, rate limited | No body | 201 `ShortLink` |
| GET `/s/{code}` | Member 4 / gateway | Public | Path code | 302 `Location: <storefront>/products/<id>`; 404 invalid/expired |
| GET `/api/prices/{product_id}` | Member 5 | Public | Product ID | 200 `CompetitorPrice[]`; empty if none |
| GET `/api/health` | Gateway / Member 1 coordination | Deployment policy | None | 200 `{status:'healthy'|'degraded'|'unhealthy'|'unavailable',simulated:boolean,services?:[{name,status}]}` |

### Cart mutation decision

Legacy `VITE_CART_WRITE_MODE=post` preserves the original proposal: `increment` for add, `set` for exact quantity and `set, quantity:0` for delete through the agreed POST path. This is **not an established backend behavior**. The new services profile defaults to `patch`, matching the screenshot PATCH and DELETE endpoints. Set `VITE_CART_WRITE_MODE` explicitly to override either profile. Both must validate stock and return server-computed totals. Guest merge, expiry and ownership must be finalized by Member 3.

### Example register and order

```json
{"name":"Demo Shopper","email":"shopper@example.com","password":"Example123"}
```

```json
{"user":{"id":"CUS-948217","name":"Demo Shopper","email":"shopper@example.com"}}
```

```json
{
  "shippingAddress": {
    "name":"Demo Shopper","email":"shopper@example.com","phone":"9876543210",
    "address":"123 Example Road","city":"Chennai","postalCode":"600001"
  },
  "paymentMethod":"demo-no-payment"
}
```

```json
{
  "id":"ORD-74128","customerId":"CUS-948217","createdAt":"2026-10-19T10:00:00Z",
  "status":"Confirmed","shippingAddress":{"name":"Demo Shopper","email":"shopper@example.com","phone":"9876543210","address":"123 Example Road","city":"Chennai","postalCode":"600001"},
  "items":[{"product":{"id":"prd-001","name":"Studio wireless headphones","category":"electronics","price":4990,"description":"Balanced wireless audio","images":["/assets/headphones.jpg"],"stock":8,"specifications":{"Warranty":"12 months"},"createdAt":"2026-10-19T00:00:00Z","featured":true},"quantity":1}],
  "subtotal":4990,"shipping":0,"tax":0,"total":4990
}
```

## Original extensions and their compatibility status

| Method/path | Owner | Response/behavior | Status |
|---|---|---|---|
| GET `/api/products/{id}` | Member 2 | `Product`; 404 absent | Mock + HTTP implemented |
| PATCH `/api/cart/items/{product_id}` | Member 3 | `{quantity}` → `Cart` | Configurable HTTP + equivalent mock |
| DELETE `/api/cart/items/{product_id}` | Member 3 | `Cart` | Configurable HTTP + equivalent mock |
| GET `/api/orders` | Member 3 | `Order[]` for current customer | Mock + HTTP implemented |
| GET `/api/orders/{id}` | Member 3 | `Order`, enforce ownership | Mock + HTTP implemented |
| GET `/api/users/me` | Member 3 | `User` or `null`; 401 also treated as signed out | Mock + HTTP implemented |
| POST `/api/logout` | Member 3 | 204, expire session cookie | Mock + HTTP implemented |
| GET `/api/metrics` | Member 5 / gateway | `SystemMetrics` below | Mock + HTTP implemented |
| Shard read/admin endpoints | Member 2 | To be designed, authorization mandatory | Not called; isolated local simulation only |
| Production cache metrics / per-service health | Owners / gateway | Optional additional monitoring contract | Not supplied; no invented live metrics |

`SystemMetrics` is `{source:string,simulated:boolean,results:Metric[]}`. `Metric` requires `scenario`, `users`, `throughput` (requests/sec), `average`, `median`, `p95`, `p99` (all ms), `errorRate` (percentage 0–100). See `BENCHMARK_EXAMPLE.json` and the validated JSON importer. Imported “measured” metadata is source-claimed, not independently verified. CPU/memory and failure/recovery have no finalized schema and are shown as unavailable, not fabricated.

## Redirect integration

Gateway routes `/s/*` before SPA fallback. In API mode, same-origin navigation to `/s/code` must reach the backend; for a separate gateway origin, the adapter navigates to that origin. Do not return a JSON body from the real redirect endpoint. Mock links resolve using the local SPA route and only work with the issuing browser’s stored dataset.

## Open decisions to resolve before integration

1. Confirm response JSON for new registration/login/profile; current adapter supports token/accessToken and performs login after tokenless registration.
2. Guest cart session and merge behavior. New services use PATCH/DELETE; legacy POST increment/set remains available.
3. Cookie domain, SameSite, CORS allowlist, CSRF mechanism and exposed rate-limit headers.
4. Product detail and monitoring extension paths, profile response shape and history pagination. New auth/profile and order-history paths now match the screenshots.
5. Order atomicity, stock reservation, idempotency storage and key retention.
6. Error envelope and field-error details.
7. Whether the backend returns absolute storefront short URLs or gateway short URLs.
8. Price freshness/source attribution and missing-price behavior.
9. Monitoring schema, authorization, and any actual administrative shard operations.

No production authorization, Redis, MongoDB, background crawler or real microservice deployment is implemented in this frontend.
