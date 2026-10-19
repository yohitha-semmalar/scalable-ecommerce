# Member 1 integration handoff

## Team ownership

| Member | Owns | Next concrete integration task |
|---|---|---|
| 1 | Frontend, gateway coordination, contracts, demo | Confirm response schemas and run the eight journeys in API mode |
| 2 | Products, search, consistent hashing, MongoDB shards, Redis cache | Return typed product lists/detail; confirm query parameters and provide measured shard/cache results |
| 3 | Auth, rate limits, Redis cart, orders, IDs | Agree sessions/guest merge, cart mutation semantics, order atomicity and idempotency |
| 4 | Short links and autocomplete | Return suggestions and short URL; gateway redirect must reach correct storefront product |
| 5 | Crawler, prices, performance | Supply timestamped price records and normalize Locust output into benchmark schema |

## Connect incrementally

1. Agree `API_CONTRACT.md` request/response shapes first. Endpoints are preserved, but payloads are proposals.
2. Start backend services behind one gateway. Serve `/api/*` to owners and `/s/*` to URL shortener. Serve all other unknown application paths with the SPA index.
3. Choose same-origin sessions where possible. For cross-origin, configure exact CORS origin, credentials, OPTIONS handling and exposed Retry-After. Decide CSRF protection with Member 3 before production use; add the agreed CSRF header in `apiClient.ts`.
4. Configure `.env.local`, restart Vite, and register a development account. A missing `/api/auth/me` (or `/api/users/me` in legacy mode) is an integration blocker for session restoration, not a reason to silently fake a user.
5. Product team: verify catalogue, category, search, sorting, pagination, detail and stock. If your envelope differs, normalize it in `src/services/index.ts`.
6. Cart team: new services use POST `{productId,quantity}`, PATCH `{quantity}`, DELETE item and DELETE cart. Legacy POST increment/set is preserved behind `VITE_API_CONTRACT=legacy` and `VITE_CART_WRITE_MODE=post`. Return complete Cart, or an empty success body followed by a supported GET cart.
7. Order team: accept `Idempotency-Key`, enforce current-user ownership, calculate totals and check stock atomically. Never trust frontend prices. Do not automatically retry order POSTs. Add server-side idempotency before enabling retry after unknown outcomes.
8. URL team: `POST /api/share/:id` returns `{code,url,productId}`. `url` must be resolvable by the gateway, not an internal service hostname. Real GET redirects with Location; do not return JSON to the frontend route.
9. Pricing team: return a timestamp and `simulated` flag per source. Do not label seeded data as live crawling.
10. Health: only report per-service status if actually measured. Missing metrics appear as unavailable. The app does not infer live Redis hits or shard state from shopping behavior.

## Service-layer extension points

- `apiClient.ts`: gateway/service origins, cookie or in-memory Bearer authentication, timeout, cancellation, errors and recent measured transport timings.
- `index.ts`: all endpoint methods and DTO normalization. `selectAdapter` switches mock/HTTP.
- `types/index.ts`: shared TypeScript DTO contracts (language-agnostic JSON on the wire).
- `mocks/adapter.ts`: prototype business behavior. Do not move this into a real frontend authentication scheme.
- `SystemLab.tsx`: safe local shard/cache/rate experiments; no production mutations. Any future admin endpoint needs server authorization and explicit integration work.

## Benchmark handoff

Export Locust aggregate values into `BENCHMARK_EXAMPLE.json` shape. Convert seconds to milliseconds if necessary; errors are percentages, not fractions. Set `simulated:false` only for a supplied measurement and put scenario/test run details in `source`. Import through Performance → Import JSON. The frontend validates fields and labels the source claim. Raw CSV import is not currently implemented.

## Deployment

```bash
pnpm install --frozen-lockfile
pnpm test
pnpm build
```

Deploy `dist/` to a static SPA host, with gateway routing as above. The source `.openai/hosting.json` is hosting metadata for the maintained Site; downloaded source excludes its project identity so it can be used independently.

## Acceptance sequence

New account purchase → search-driven purchase → sharing → comparison → returning customer → isolated 429 → deterministic shard change → monitoring. Use `QA_CHECKLIST.md`, and record the backend commit and benchmark provenance. None of the real backend flows have been validated yet.

## Updated screenshot contract configuration

Use `VITE_API_CONTRACT=services`, `VITE_AUTH_MODE=bearer`, `VITE_CART_WRITE_MODE=patch`. Registration/login/profile use `/api/auth/*`. The original `/api/register`, `/api/login`, `/api/users/me`, `/api/logout` and POST cart behavior are retained in the legacy profile. All product/search/suggestions/sharing/pricing/health/metrics endpoints are preserved.

Set optional origin-only `VITE_AUTH_BASE_URL=http://localhost:8082`, `VITE_CART_BASE_URL=http://localhost:8083`, `VITE_ORDER_BASE_URL=http://localhost:8084` for local frontend development, or leave blank for gateway routing.

Expected login response: `{token,user?}` or `{accessToken,user?}`. Token-only responses fetch a direct `{id,name,email}` profile. Tokens stay in memory only; no refresh contract was supplied, so reload requires sign-in again. Cookie sessions can be explicitly selected. Bearer logout is local token disposal, not server revocation.

New order request maps `name` to `fullName`, `address` to `line1`, includes `state`, and sends exactly the screenshot shippingAddress object plus the Idempotency-Key header. Response address normalization supports both formats. Responses for cart/order were not shown; validate them against API_CONTRACT.md before claiming end-to-end backend integration.
