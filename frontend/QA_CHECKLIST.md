# Verification record and remaining browser QA

## Executed automated checks

- TypeScript strict compilation: passed before final production build.
- Vitest / React Testing Library: 51 tests passed.
- Real browser / Playwright: not run; supported browser-control capability unavailable.
- Real backend integration: not run; no services supplied.

## Manual review to perform in a browser

Use desktop 1440×900, tablet 768×1024 and mobile 390×844. These are a checklist, not claimed results.

1. Home: inspect campaign photos, text wrapping, product image matches, fallback images and CTAs.
2. Catalogue: category/price/stock/sale filters, all sorting modes, pagination, clear filters, back/forward URL restoration, empty results.
3. Search: type quickly, arrow keys, Enter, Escape, click suggestions, clear search; verify no stale results after navigation.
4. Product: image enlargement and Escape/focus return, stock limits, add pending state, copied link, browser-native share, invalid short code.
5. Auth: validation, duplicate email, bad password, login/logout, intended checkout redirect, reload session restoration, expired API session.
6. Cart: add from multiple pages, quantity limits, removal, consistent header count, empty state, shipping threshold.
7. Checkout: fictional validated details; double-click Place order; order ID shown once; history and order ownership after switching accounts.
8. System Lab: all seven sections; add/remove shards; inspect actual moved keys; cache miss then hit; six 429 requests and recovery; health error/retry; import valid/invalid benchmark JSON; reset confirmation.
9. Failure controls: set 1-second latency, fail-next then trigger a fresh request; observe pending/error/retry, not false success.
10. Accessibility: keyboard-only traversal, focus visibility, dialogue focus management, form error associations, screen-reader cart announcement, no horizontal overflow, 44px practical touch targets.
11. Images/fonts: inspect every seeded photo for product match and service availability; replace mismatches with approved local assets.
12. Real mode: test all 12 agreed endpoints and finalize extension paths; verify session cookies, CORS/CSRF, idempotency, rate-limit headers and redirect ownership.

## Explicit gaps

No browser screenshots or viewport measurements were available. The gallery supports multiple image URLs but each mock product has one supplied photograph. Benchmark import accepts normalized JSON only. Production cache metrics, shard mutations, CPU/memory and recovery measurements are pending owner-supplied contracts/data. Demo persistence is single-browser and not transactional across tabs.

The endpoint update adds automated checks for both auth profiles, exact cart methods/payloads, clear-cart 204/refetch, bearer headers/session clearing, service-specific origins, both order address formats, all unchanged service routes, and the Clear bag UI. Real backend and browser QA remain unverified.
