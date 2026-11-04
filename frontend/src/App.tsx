import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AuthProvider, RequireAuth, RequireOwner } from './auth'
import { AddStockPage } from './pages/AddStockPage'
import { CatalogPage } from './pages/CatalogPage'
import { ExpensesPage } from './pages/ExpensesPage'
import { ProductPage } from './pages/ProductPage'
import { InventoryPage } from './pages/InventoryPage'
import { LoginPage } from './pages/LoginPage'
import { NewSalePage } from './pages/NewSalePage'
import { SalesPage } from './pages/SalesPage'

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/inventory"
            element={
              <RequireAuth>
                <InventoryPage />
              </RequireAuth>
            }
          />
          <Route
            path="/catalog"
            element={
              <RequireAuth>
                <CatalogPage />
              </RequireAuth>
            }
          />
          <Route
            path="/products/:id"
            element={
              <RequireAuth>
                <ProductPage />
              </RequireAuth>
            }
          />
          <Route
            path="/stock/new"
            element={
              <RequireOwner>
                <AddStockPage />
              </RequireOwner>
            }
          />
          <Route
            path="/sales"
            element={
              <RequireAuth>
                <SalesPage />
              </RequireAuth>
            }
          />
          <Route
            path="/sales/new"
            element={
              <RequireOwner>
                <NewSalePage />
              </RequireOwner>
            }
          />
          <Route
            path="/expenses"
            element={
              <RequireAuth>
                <ExpensesPage />
              </RequireAuth>
            }
          />
          <Route path="*" element={<Navigate to="/inventory" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
