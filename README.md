# Nexus

**Inventory management for trading companies.** A company buys goods from suppliers in any
country, stores them and sells them wherever it decides. Nexus tracks every product from
purchase to sale, with its real cost and real profit.

> **Status:** Phase 1 (core domain) complete: catalog, stock, purchases, shipping, sales, payments,
> expenses, exchange rates, warranty, cash, login with roles and audit log. Phase 2 (React frontend)
> started: login, inventory list with SKUs, adding products and stock, and editing or deleting
> categories and products. See the [roadmap](#roadmap).

## Features

- Catalog with owner-defined categories and fields (for example *Phones: Modell, Speicher, Farbe*)
- Stock tracked as individual **units** (serial numbers) or **lots** (quantities), per warehouse
- Purchases and sales with lines, partial payments and money owed
- Real cost per item: FIFO stock costing and shared costs (shipping, customs) split fairly
- Business overview: cash, stock, money owed, profit, business value (dashboard UI planned)
- Several currencies, with exchange rates frozen on each transaction
- Warranty claims, returns and refunds; login with roles; append-only audit log (alerts planned)
- AI chat assistant that answers from live data and drafts entries for the owner to confirm (planned)

## Tech stack

Java 25 · Spring Boot 4.1 · PostgreSQL 18 · Flyway · Testcontainers · Docker · GitHub Actions ·
React 19 · TypeScript · Vite (later: Redis, RabbitMQ, Kubernetes, Python/FastAPI with a local AI model)

## Repository layout

```
nexus-inventory/
├── services/core-service/   Spring Boot backend (inventory, sales, payments)
├── frontend/                React web app (Vite + TypeScript)
├── deploy/docker/           Docker Compose for local development
├── docs/                    Architecture decision records (adr/)
└── .github/workflows/       CI pipeline
```

## Run it locally (macOS)

You need **Java 25**, **Docker Desktop**, **Node.js 22+** (for the frontend) and **Git**. Check with:

```bash
java -version     # should say 25
docker --version
node --version    # 22 or newer; install with: brew install node
git --version
```

1. Start the database:

   ```bash
   cd deploy/docker
   docker compose up -d
   ```

2. Start the backend (first run downloads Gradle and the libraries, so it takes a few minutes):

   ```bash
   cd services/core-service
   ./gradlew bootRun
   ```

3. Open in your browser:
   - <http://localhost:8080/actuator/health> → `{"status":"UP"}`
   - <http://localhost:8080/api/v1/system/info> → the application name and its currencies

4. Start the frontend (in a second terminal, while the backend runs):

   ```bash
   cd frontend
   npm install       # first time only
   npm run dev
   ```

   Open <http://localhost:5173> and sign in. Locally the first owner is `owner` / `change-me-please`
   (see `NEXUS_OWNER_*` in `.env.example`). The dev server forwards `/api` calls to the backend on
   port 8080, so the backend needs no CORS setup.

5. Run the tests (Docker must be running, because the tests start their own PostgreSQL):

   ```bash
   cd services/core-service
   ./gradlew test
   ```

   For the frontend, `npm run build` type-checks and builds, and `npm run lint` checks the code.

## Environment variables

Copy `.env.example` to `.env` to override the local defaults. `.env` is never committed.

| Variable | Default (local only) |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/nexus` |
| `DATABASE_USER` | `nexus` |
| `DATABASE_PASSWORD` | `nexus_dev_password` |

## Database migrations

Schema changes are SQL files in `services/core-service/src/main/resources/db/migration`,
applied automatically by Flyway at startup. Never edit a migration that already ran; add a new one.

## Roadmap

| Phase | Goal |
|---|---|
| 0 · Foundations | Repository, backend skeleton, database, CI |
| 1 · Core domain | Catalog, units and lots, purchases, sales, payments, FIFO, landed cost |
| **2 · Frontend** ← now | React app for daily work |
| 3 · Insight | Dashboard, analytics, exchange rates, alerts, import |
| 4 · DevOps | Containers for everything, full CI/CD |
| 5 · Services | Extract services, events with RabbitMQ, API gateway |
| 6 · AI | Chat assistant with a local model |
| 7 · Kubernetes | Deployment, monitoring, load tests |

## License

[MIT](LICENSE) © 2026 Fadi Jebali
