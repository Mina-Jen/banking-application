import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AppLayout } from './components/AppLayout'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { RequireAdmin } from './auth/RequireAdmin'
import { RequireAuth } from './auth/RequireAuth'
import { AccountPage } from './pages/AccountPage'
import { AdminDashboardPage } from './pages/AdminDashboardPage'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { RegisterPage } from './pages/RegisterPage'
import { ReviewQueuePage } from './pages/ReviewQueuePage'

/**
 * The route table.
 *
 * The pathless route wrapping the pages below is the protected area: its
 * element is <RequireAuth>, so every child inherits the guard without repeating
 * it, and AppLayout's <Outlet/> is where the matched child renders.
 *
 * `/` renders one of two dashboards, picked by role: the customer portfolio, or
 * the admin operations overview. The real protection is the backend's own role
 * checks, so a customer typing /review is simply sent home by <RequireAdmin>.
 *
 * Child paths are written relative to the layout route on purpose — an absolute
 * path inside a pathless parent makes React Router complain.
 */
function DashboardOrAdmin() {
  const { isAdmin } = useAuth()
  return isAdmin ? <AdminDashboardPage /> : <DashboardPage />
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Public: both pages redirect away if a session already exists. */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          <Route
            element={
              <RequireAuth>
                <AppLayout />
              </RequireAuth>
            }
          >
            <Route index element={<DashboardOrAdmin />} />
            <Route path="accounts/:accountNumber" element={<AccountPage />} />
            <Route
              path="review"
              element={
                <RequireAdmin>
                  <ReviewQueuePage />
                </RequireAdmin>
              }
            />
          </Route>

          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
