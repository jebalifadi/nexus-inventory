# Changelog

What changed in each version of Nexus. Versions follow [Semantic Versioning](https://semver.org):
while Nexus is below 1.0, the middle number goes up for new features and the last one for fixes.

## 0.3.0 - 2026-11-09

The web app is complete, and Phase 3 (Insight) has started.

### Added

- Web screens for every feature: dashboard, inventory, catalog, purchases, shipments, sales and
  payments, warranty and returns, expenses, cash book, capital, exchange rates, contacts and search.
- Settings with users, trash and audit log, a new design with light and dark themes, and English,
  German, French and Arabic.
- Period picker on the dashboard, with comparison against the previous period.
- Write-offs: damaged or lost goods count as a loss.
- Backend reports, so every screen shares the same figures.
- The dashboard shows everything in the Settings currency. Activity converts at the rate of its own
  date, stock and open balances at today's rate. When a rate is missing, the dashboard names it.
- Official EUR and USD rates from the Central Bank of Tunisia, checked every hour while Nexus runs.
  Rates entered by hand take priority.
- Scripts to start, stop, back up, restore and reset Nexus.
- Browser tests for login, purchases, sales, payments, write-offs and the dashboard.
- The running version is shown in Settings.

### Fixed

- The profit comparison only appeared when a sale had an unknown profit.

## 0.2.0 - 2026-10-31

Phase 1: the core domain, without screens.

### Added

- Catalog with owner-defined categories and fields, and stock tracked as units or lots.
- Purchases, shipments with shared costs split fairly, and sales with FIFO costing and profit.
- Payments and money owed, expenses, exchange rates, warranty claims and returns.
- Cash book, capital and business overview, soft delete with restore.
- Login with roles, audit log and API documentation.

## 0.1.0 - 2026-10-13

Phase 0: repository, backend skeleton, database and CI.
