# ScaleShop

A runnable React commerce frontend for a five-person System Design project. Warm editorial retail design, 24 products across six categories, end-to-end browser-persistent mock commerce, typed HTTP integration and an interactive engineering lab. No real payments are collected.

## Run locally

Requires Node.js 22.13+ and pnpm 11.25 (or a compatible version). From the project directory:

```bash
corepack enable
corepack prepare pnpm@11.25.0 --activate
pnpm install --frozen-lockfile
cp .env.example .env.local
pnpm dev
```

Open the Vite URL shown in your terminal. A standard npm install is also possible in a fresh copy, but pnpm is the tested locked dependency setup. The `install:ci` script is specific to the managed hosting environment; local developers should use `pnpm install` directly.

```bash
pnpm test
pnpm build
pnpm exec vite preview
```

## Start the demonstration

1. Create an account at `/register`, using a fictional email and a unique demo password (8+ characters, including a number). Registration signs you in. No preconfigured credentials are needed.
2. Browse the homepage or `/products`; filter, sort, search and use keyboard autocomplete.
3. Open a product, inspect comparison prices, generate/copy/open its short link, and add it to your bag.
4. Change quantities at `/cart`, then checkout using fictional shipping details. A 10-digit Indian mobile number and 6-digit PIN are validated. No card data is collected.
5. Place the demo order, inspect its service-assigned demo ID and open `/orders`.
6. Open `/system-lab`. Add/remove a shard, inspect remapped keys, simulate cache hits/misses, send six isolated rate-limit requests, and compare low/medium/high load examples.
7. Use Reset Demo Data to remove all browser-local demo state. The action requires confirmation.

## Features and routes

| Route | Behavior |
|---|---|
| `/` | Editorial collection, central-data product cards, arrivals and sale selection |
| `/products` | Category, price, stock, sale and name filtering; sort; pagination; URL state |
| `/category/:slug` | Category-specific descriptions and catalogue |
| `/search?q=` | Editable search, filters, result count and empty state |
| `/products/:id` | Images/zoom, stock, quantity, add, short-link sharing, competitor prices, related products |
| `/register`, `/login` | Validated forms, pending/error/429 states, intended-route redirect |
| `/account` | Profile, returned customer ID, logout and order links |
| `/cart`, `/checkout` | Shared cart state, shipping policy, validation, guarded no-payment checkout |
| `/orders`, `/orders/:id` | Per-customer history and confirmation with returned order ID |
| `/s/:code` | Mock redirect resolution; real redirect belongs to gateway |
| `/system-lab` | Architecture, deterministic sharding, cache/rate simulations, IDs, metrics and health |
| `/help`, `/policies` | Honest demo shopping, delivery, returns and privacy content |

## Technology

React + Vite + strict TypeScript; React Router; TanStack Query; centralized Fetch client; React Context; React Hook Form + Zod; Lucide; Recharts; Tailwind v4 with bespoke CSS; Vitest + React Testing Library. No payment SDK, direct Redis connection or MongoDB driver.

## Project structure

- `src/app`: route composition, lazy page imports, global state and error boundary.
- `src/components`: shell, autocomplete and shared product/feedback components.
- `src/features`: homepage, catalogue, product, auth, commerce, utility and lab pages.
- `src/services`: HTTP transport, API contracts/adapters and mode selection.
- `src/mocks`: coherent product seed, persistent async adapter, failure and rate controls.
- `src/types`: typed domain models.
- `src/lib`: totals and deterministic hashing algorithms.
- `src/styles`: brand tokens, layout, responsive and accessible interaction rules.
- `src/tests`: service, component and routed workflow tests.

The implementation groups commerce components in one feature file; it does not introduce one wrapper module per API solely for structure. All network calls still reside in the service layer.

## Mock and API modes

Default is `VITE_DATA_MODE=mock`. This is a build-time Vite setting. Browser localStorage stores demo profiles with salted SHA-256 digests (not plaintext passwords), session pointer, carts, stock, orders and links. This is deliberately not production authentication. Mock IDs use a local sequence, not a globally unique algorithm. Use fictional details only. The async adapter has cancellation, configurable latency, fail-next and rate-limit behavior.

For real services:

```dotenv
VITE_DATA_MODE=api
VITE_API_BASE_URL=https://your-gateway.example
VITE_API_TIMEOUT_MS=10000
VITE_API_CONTRACT=services
VITE_AUTH_MODE=bearer
VITE_CART_WRITE_MODE=patch
```

Rebuild/restart after changes. Use an empty base URL for a same-origin gateway. There is no fallback from failed real calls to mock data. Real mode supports the new bearer-token services contract and the original cookie-session contract, and records client-observed HTTP status/latency. Bearer tokens stay in memory, so a page reload requires sign-in again. Never put secrets in Vite environment variables. See `API_CONTRACT.md` and `INTEGRATION_GUIDE.md` before connecting a service.

All 12 original endpoints are retained, and every auth/cart/order endpoint in the new screenshots is represented. The new profile uses `/api/auth/register`, `/api/auth/login`, `/api/auth/me`, PATCH/DELETE cart items and DELETE cart. Product detail and metrics extensions and all response bodies still need backend confirmation. The adapter isolates path/payload changes from the pages.

## Keep old endpoints or connect the new services

The new profile is the default for HTTP mode. Mock mode remains the default data mode. A confirmed **Clear bag** action now calls `clearCart()` and resets the shared cart. Checkout now includes State / Union territory and maps the new order address fields.

To restore the exact original route selection and cart mutation policy:

```dotenv
VITE_API_CONTRACT=legacy
VITE_AUTH_MODE=cookie
VITE_CART_WRITE_MODE=post
```

To call the screenshot services directly during local development (origins only):

```dotenv
VITE_DATA_MODE=api
VITE_API_CONTRACT=services
VITE_AUTH_MODE=bearer
VITE_CART_WRITE_MODE=patch
VITE_AUTH_BASE_URL=http://localhost:8082
VITE_CART_BASE_URL=http://localhost:8083
VITE_ORDER_BASE_URL=http://localhost:8084
```

Leave service origins empty to route through your gateway. Other features continue using `VITE_API_BASE_URL`. Local service URLs do not make the backend remotely hosted; use a local frontend for this configuration and configure backend CORS. Screenshots did not include response JSON, so `API_CONTRACT.md` specifies the expected response shapes and remaining integration decisions.

## Architecture and calculations

Frontend → gateway → independent authentication, product, cart/order, links/autocomplete and crawler/pricing services. The topology is planned, not a claim that services are deployed. Each service owns its data. Replication and sharding are separate concepts.

Sharding uses product IDs, a deterministic unsigned 32-bit FNV-derived hash with avalanche mixing, 32 virtual nodes per physical shard, sorted ring points and clockwise lookup. The UI computes actual before/after assignments for all 24 seeded keys and compares modulo hashing. It changes no backend infrastructure. The visual ring and distribution react to additions/removals.

Demo cart policy: INR prices include taxes, shipping ₹99 below ₹2,500, otherwise free; empty cart costs zero. API-mode totals come from the server. Demo order creation validates stock and clears the cart only after persistence. Same-key replay returns the original order. Real distributed atomicity/idempotency belongs to Member 3.

## Testing and verification

`pnpm test` executes 51 tests covering product filtering/sorting/search, keyboard suggestions, registration/login, cart operations and totals, checkout validation and duplicate submissions, order IDs/history, short links, comparison prices, invalid routes, adapter switching, rate limits, metrics, menu interactions and deterministic shard reassignment. `pnpm build` checks TypeScript then generates static assets.

The current environment supports jsdom tests but has no supported browser QA capability. Therefore Playwright, real browser workflows, visual checks at desktop/tablet/mobile widths, image availability and accessibility audits are **not claimed as passed**. See `QA_CHECKLIST.md` for those remaining checks.

## Known limitations and honest boundaries

- Backend services, API credentials, production auth and actual integration tests are pending.
- Demo accounts/links are browser-local, not shared across devices. Browser storage is not a secure database. Multi-tab concurrent mutation is not transactional.
- No real purchase, delivery, refund or payment processing.
- Product photography is remotely hosted with product-name fallback; image availability and exact visual match need browser review before a polished presentation. Products currently have one seeded image each; gallery code supports multiple service-supplied images.
- No wishlist or profile-edit controls are shown because those contracts are not defined.
- Cache/rate/shard controls are clearly isolated simulations even in API mode. No real admin mutations.
- Benchmarks support normalized JSON import (not arbitrary Locust CSV). CPU/memory and recovery fields are unavailable until a schema is agreed. Source claims are not independently verified.
- Mock suggestion matching is a case-insensitive substring search; real prefix indexing belongs to Member 4.
- UI is responsive by CSS and menu interactions are tested in jsdom, but viewport visual QA and touch-device review remain outstanding.
- Static hosting must serve `index.html` for storefront paths. A real gateway must intercept `/api/*` and `/s/*` first.

## Source handoff

`scaleshop-source.zip` includes application code, lockfile, environment example, tests and integration documents, excluding dependencies, credentials and host-specific identity. The hosted source repository is the canonical maintained version.
