# Cart Service
Spring Boot 3.5, Java 17, MongoDB, JWT and rate limiting. Default port 8083.

PowerShell (set these in the same terminal used to run Maven):
```powershell
$env:MONGODB_URI="YOUR_ATLAS_URI"
$env:MONGODB_DATABASE="ecommerce"
$env:JWT_SECRET_BASE64="SAME_BASE64_SECRET_AS_AUTH_SERVICE"
$env:PRODUCT_SERVICE_URL="http://localhost:8081"
mvn spring-boot:run
```

All cart routes require `Authorization: Bearer <token>`. Routes: `GET /api/cart`, `POST /api/cart/items` body `{"productId":"p101","quantity":2}`, `PATCH /api/cart/items/{productId}` with quantity, `DELETE /api/cart/items/{productId}`, `DELETE /api/cart`; `GET /health` is public.

Product integration assumes `GET {PRODUCT_SERVICE_URL}/api/products/{productId}` returns JSON with `name` (or `title`), `price`, `imageUrl`, `available`. Confirm the actual route/fields with your product-service teammate. Cart display calls Product Service for current details.

Default rate limit is 60 requests per 60 seconds per remote IP and path; over limit returns 429 and Retry-After. This simple in-memory limiter is per instance, not distributed; use Redis for multiple replicas. JWT secret must be the same Base64 key as Authentication Service and decode to at least 32 bytes. Do not commit real secrets. Order Service must revalidate stock and price at checkout. Source has not been compiled or tested against your team's actual services.
