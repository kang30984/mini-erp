import { Navigate, createBrowserRouter } from 'react-router'
import AppLayout from './components/AppLayout'
import LoginPage from './pages/LoginPage'
import AdminHome from './pages/admin/AdminHome'
import MarketHome from './pages/market/MarketHome'
import RequireAuth from './routes/RequireAuth'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/admin',
    element: <RequireAuth role="ADMIN" />,
    children: [
      {
        element: <AppLayout menus={[{ to: '/admin', label: '운영 홈' }]} />,
        children: [{ index: true, element: <AdminHome /> }],
      },
    ],
  },
  {
    path: '/market',
    element: <RequireAuth role="PARTNER" />,
    children: [
      {
        element: <AppLayout menus={[{ to: '/market', label: '주문 홈' }]} />,
        children: [{ index: true, element: <MarketHome /> }],
      },
    ],
  },
  // 로그인 페이지가 로그인 상태면 권한별 홈으로 보내 준다
  { path: '*', element: <Navigate to="/login" replace /> },
])
