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
  To use another backend: `API_TARGET=http://localhost:8081 npm run dev`.
- `src/api.ts`: the only place that calls the backend. It adds the login token to every request,
  and turns error responses into readable messages.
- `src/auth.tsx`: who is logged in. The token is kept in `sessionStorage` (gone when the tab
  closes) and is dropped when it expires or the backend rejects it.
- `src/pages/`: one file per screen.
  - `LoginPage`: sign in.
  - `InventoryPage`: stock per product and location, grouped by category, with each product field
    as a labeled column, a location filter and search.
  - `ProductPage` (`/products/:id`): a product's details, its stock per location, and its pieces
    (units, with their per-piece fields, cost, location and status) or batches (lots).
  - `CatalogPage`: categories with their products. The owner can edit a category (its name, and
    which fields form product names, in order) or delete it, and edit (SKU, product fields) or
    delete a product. Fields that are not part of the name are shown as their own columns. Deleting asks first and offers Undo,
    because deleted records go to the trash and can be restored.
  - `AddStockPage` (owner only): pick or create a product, then add stock to a location.
    Lot products take a quantity; unit products are added one piece at a time with their item fields.
- `src/catalog.ts`: helpers shared by the screens: name fields vs column fields, money and date
  formatting, unit status labels.
- `src/components/`: pieces shared by screens: the top bar (`Layout`), modal dialogs and the
  delete confirmation (`Dialog`), an input for each
  category field type (`FieldInput`), and the small create forms for categories, products and
  locations. These forms are shown next to the main form, never inside it (HTML forms cannot nest).

If the dev server answers `/api` calls with 404, restart it (Ctrl+C, then `npm run dev`). Vite
sometimes loses the proxy when it restarts by itself after a config file changes.
