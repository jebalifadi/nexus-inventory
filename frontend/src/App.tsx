import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AuthProvider, RequireAuth } from './auth'
import { InventoryPage } from './pages/InventoryPage'
import { LoginPage } from './pages/LoginPage'

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
          <Route path="*" element={<Navigate to="/inventory" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
