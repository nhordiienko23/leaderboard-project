import type { ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

interface GuardProps {
  children?: ReactNode;
}

export function ProtectedRoute({ children }: GuardProps) {
  const { status, isAuthenticated } = useAuth();
  const location = useLocation();
  if (status === 'restoring') return null;
  if (!isAuthenticated) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <>{children ?? <Outlet />}</>;
}

export function GuestRoute({ children }: GuardProps) {
  const { status, isAuthenticated } = useAuth();
  if (status === 'restoring') return null;
  if (isAuthenticated) return <Navigate to="/posts" replace />;
  return <>{children ?? <Outlet />}</>;
}

export function AdminRoute({ children }: GuardProps) {
  const { status, isAuthenticated, isAdmin } = useAuth();
  if (status === 'restoring') return null;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (!isAdmin) return <Navigate to="/403" replace />;
  return <>{children ?? <Outlet />}</>;
}

