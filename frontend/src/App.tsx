import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AdminPage } from './pages/AdminPage'
import { CashBookPage } from './pages/CashBookPage'
import { ContactsPage } from './pages/ContactsPage'
import { DashboardPage } from './pages/DashboardPage'
import { NewPurchasePage } from './pages/NewPurchasePage'
import { PurchasesPage } from './pages/PurchasesPage'
import { ShipmentsPage } from './pages/ShipmentsPage'
import { WarrantyPage } from './pages/WarrantyPage'
import { AuthProvider, RequireAuth, RequireOwner } from './auth'
import { AddStockPage } from './pages/AddStockPage'
import { CapitalPage } from './pages/CapitalPage'
import { CatalogPage } from './pages/CatalogPage'
import { ExchangeRatesPage } from './pages/ExchangeRatesPage'
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
          <Route
            path="/exchange-rates"
            element={
              <RequireAuth>
                <ExchangeRatesPage />
              </RequireAuth>
            }
          />
          <Route
            path="/capital"
            element={
              <RequireAuth>
                <CapitalPage />
              </RequireAuth>
            }
          />
          <Route
            path="/dashboard"
            element={
              <RequireAuth>
                <DashboardPage />
              </RequireAuth>
            }
          />
          <Route
            path="/purchases"
            element={
              <RequireAuth>
                <PurchasesPage />
              </RequireAuth>
            }
          />
          <Route
            path="/purchases/new"
            element={
              <RequireOwner>
                <NewPurchasePage />
              </RequireOwner>
            }
          />
          <Route
            path="/shipments"
            element={
              <RequireAuth>
                <ShipmentsPage />
              </RequireAuth>
            }
          />
          <Route
            path="/warranty"
            element={
              <RequireAuth>
                <WarrantyPage />
              </RequireAuth>
            }
          />
          <Route
            path="/cash-book"
            element={
              <RequireAuth>
                <CashBookPage />
              </RequireAuth>
            }
          />
          <Route
            path="/contacts"
            element={
              <RequireAuth>
                <ContactsPage />
              </RequireAuth>
            }
          />
          <Route
            path="/admin"
            element={
              <RequireOwner>
                <AdminPage />
              </RequireOwner>
            }
          />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
