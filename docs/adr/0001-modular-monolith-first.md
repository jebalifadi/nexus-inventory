# ADR-0001: Start with a modular monolith, extract services later

- **Status:** Accepted
- **Date:** 2026-10-10

## Context

Nexus targets an architecture of several services (core, exchange rates, analytics, audit,
notifications, AI). Building them as separate services from day one would mean solving
network calls, distributed data and deployment before the business rules are proven.

## Decision

Phases 1–4 build **one Spring Boot application** (`core-service`) split into modules
(catalog, inventory, purchasing, sales, payments). Modules interact only through their public
APIs. In Phase 5, services are extracted where the boundary is clean: exchange rates,
analytics, audit and notifications. Inventory, sales and payments stay together, because their
invariants must be enforced in a single database transaction.

## Consequences

- Faster delivery, simpler debugging and transactional consistency for financial rules.
- Module boundaries must stay strict, or later extraction becomes costly. Spring Modulith
  tests enforce them from Phase 1.
- The service extraction is driven by the target architecture (independent deployment and
  scaling of supporting capabilities), not by current load.
