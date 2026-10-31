# Nexus frontend

React 19 + TypeScript web app for Nexus, built with Vite.

```bash
npm install     # first time only
npm run dev     # http://localhost:5173 (the backend must run on :8080)
npm run build   # type-check and build into dist/
npm run lint    # oxlint
```

## How it fits together

- `vite.config.ts`: in development, requests to `/api` are forwarded to `http://localhost:8080`.
- `src/api.ts`: the only place that calls the backend. It adds the login token to every request,
  and turns error responses into readable messages.
- `src/auth.tsx`: who is logged in. The token is kept in `sessionStorage` (gone when the tab
  closes) and is dropped when it expires or the backend rejects it.
- `src/pages/`: one file per screen (`LoginPage`, `InventoryPage`).
