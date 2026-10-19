# Authentication Microservice

Spring Boot authentication service for the e-commerce project. It provides registration, login, JWT-based user identification, `/me`, and Bucket4j rate limiting.

## Requirements

- Java 17 or newer
- Maven 3.9+ (or add the Maven Wrapper)
- MongoDB Atlas cluster or local MongoDB
- Internet access on first build so Maven can download dependencies

## Endpoints

Base URL: `http://localhost:8082/api/auth`

| Method | Endpoint | Auth required? | Purpose |
|---|---|---:|---|
| POST | `/register` | No | Create account |
| POST | `/login` | No | Validate credentials and issue JWT |
| GET | `/me` | Yes | Return the current user's profile |
| POST | `/logout` | No | Client-side logout acknowledgement; does not revoke JWT |

### Register

`POST http://localhost:8082/api/auth/register`

```json
{
  "name": "Test User",
  "email": "test@example.com",
  "password": "TestPassword123"
}
```

Returns `201 Created` with the public user fields (never the password/hash).

### Login

`POST http://localhost:8082/api/auth/login`

```json
{
  "email": "test@example.com",
  "password": "TestPassword123"
}
```

Returns `200 OK`:

```json
{
  "accessToken": "<signed-jwt>",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600,
  "user": {
    "id": "<mongo-user-id>",
    "name": "Test User",
    "email": "test@example.com",
    "createdAt": "..."
  }
}
```

### Identify the current user

Call `GET http://localhost:8082/api/auth/me` with:

`Authorization: Bearer <accessToken>`

The JWT subject (`sub`) contains the MongoDB user ID. Other microservices can validate the signature and read the subject if they share the same JWT secret and agree on the token contract. Do not trust a user ID sent in the request body for authorization.

## Bucket4j rate limit

The filter applies to registration and login, keyed by client IP + endpoint. Current demo policy:
- Capacity: 5 requests
- Refill: 5 tokens every 60 seconds
- Rejected requests: HTTP `429 Too Many Requests`
- `Retry-After: 60` response header

This uses **in-memory** buckets. It is suitable for a single local instance/demo, but limits are reset when the app restarts and are not shared across multiple service instances. For a distributed deployment, configure a compatible distributed Bucket4j backend (for example, a supported Redis integration) and agree on a shared key policy.

For real deployments, tune limits based on expected traffic and use a trusted proxy configuration before relying on forwarded client-IP headers. Avoid logging passwords or tokens.

## Configure and run

### 1. Configure MongoDB

In MongoDB Atlas, create a database user, allow the machine's current public IP in Network Access, and copy the Java driver connection string.

Set environment variables. In PowerShell:

```powershell
$env:MONGODB_URI="mongodb+srv://DB_USER:DB_PASSWORD@YOUR_CLUSTER/ecommerce?retryWrites=true&w=majority"
$env:MONGODB_DATABASE="ecommerce"
```

Replace the placeholders. URL-encode special characters in the database password where required.

### 2. Generate a JWT signing key

Run this once in PowerShell to generate a 256-bit (or longer) random secret encoded as Base64:

```powershell
$key = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($key)
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($key)
```

For local testing, keep the same value in the environment whenever you restart the app. If you regenerate it, all previously issued JWTs become invalid. For team integration, securely share/configure one consistent key across services that validate the same tokens. Never commit the real key to GitHub.

Set optional values:

```powershell
$env:JWT_EXPIRATION_SECONDS="3600"
$env:SERVER_PORT="8082"
```

### 3. Run the application

From the directory containing `pom.xml`:

```powershell
mvn spring-boot:run
```

If Maven is not installed, install Maven or generate/use a Maven Wrapper (`mvnw.cmd`). The application listens on port `8082`.

## Test with curl

Register:

```powershell
curl.exe -X POST http://localhost:8082/api/auth/register `
  -H "Content-Type: application/json" `
  -d "{\"name\":\"Test User\",\"email\":\"test@example.com\",\"password\":\"TestPassword123\"}"
```

Login:

```powershell
curl.exe -X POST http://localhost:8082/api/auth/login `
  -H "Content-Type: application/json" `
  -d "{\"email\":\"test@example.com\",\"password\":\"TestPassword123\"}"
```

Copy the returned `accessToken`, then:

```powershell
curl.exe http://localhost:8082/api/auth/me `
  -H "Authorization: Bearer PASTE_TOKEN_HERE"
```

## Status codes

- `201 Created`: registration succeeded
- `200 OK`: login or profile retrieval succeeded
- `400 Bad Request`: validation failed
- `401 Unauthorized`: invalid credentials, missing/invalid token, or deleted user
- `409 Conflict`: email already registered
- `429 Too Many Requests`: Bucket4j limit exceeded

## Integration notes

- Frontend origin may need CORS configuration when the browser calls this service from another port. Add only the agreed frontend origin; do not enable wildcard origins with credentials.
- JWT logout is stateless in this starter. The client must discard the token. If server-side revocation is required, implement a token denylist or use short-lived access tokens with refresh-token rotation.
- This service uses MongoDB collection `users`. Passwords are stored using BCrypt hashes.
- This project has not been built or run in your environment; run the build and test it against your Atlas settings before integration.
