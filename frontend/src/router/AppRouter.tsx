import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { AppShell } from '../components/layout/AppShell'
import { Spinner } from '../components/ui/Spinner'
import { AdminRoute, GuestRoute, ProtectedRoute } from './guards'
import { ForbiddenPage, NotFoundPage } from '../pages/ErrorPages'
import { LoginPage } from '../pages/auth/LoginPage'
import { RegisterPage } from '../pages/auth/RegisterPage'
import { PostsPage } from '../pages/posts/PostsPage'
import { PostDetailPage } from '../pages/posts/PostDetailPage'
import { LeaderboardPage } from '../pages/leaderboard/LeaderboardPage'
import { UserDirectoryPage } from '../pages/users/UserDirectoryPage'
import { UserProfilePage } from '../pages/users/UserProfilePage'
import { AccountSettingsPage } from '../pages/users/AccountSettingsPage'
import { AdminDashboardPage } from '../pages/admin/AdminDashboardPage'

export function AppRouter() {
  const { status } = useAuth()

  if (status === 'restoring') {
    return (
      <div className="flex min-h-screen items-center justify-center bg-lb-background" role="status" aria-label="Restoring session">
        <Spinner />
      </div>
    )
  }

  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<Navigate to="/posts" replace />} />

        <Route element={<GuestRoute />}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
        </Route>

        <Route path="/posts" element={<PostsPage />} />
        <Route path="/posts/:postId" element={<PostDetailPage />} />
        <Route path="/leaderboard" element={<LeaderboardPage />} />

        <Route element={<ProtectedRoute />}>
          <Route path="/users" element={<UserDirectoryPage />} />
          <Route path="/users/:id" element={<UserProfilePage />} />
          <Route path="/account" element={<AccountSettingsPage />} />
        </Route>

        <Route element={<AdminRoute />}>
          <Route path="/admin" element={<AdminDashboardPage />} />
        </Route>

        <Route path="/403" element={<ForbiddenPage />} />
        <Route path="/404" element={<NotFoundPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AppShell>
  )
}