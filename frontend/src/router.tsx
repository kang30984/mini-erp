import { Navigate, createBrowserRouter } from 'react-router'
import AppLayout from './components/AppLayout'
import MarketLayout from './components/MarketLayout'
import LoginPage from './pages/LoginPage'
import AdminHome from './pages/admin/AdminHome'
import AdminOrderDetailPage from './pages/admin/OrderDetailPage'
import AdminOrdersPage from './pages/admin/OrdersPage'
import SettlementDetailPage from './pages/admin/SettlementDetailPage'
import SettlementsPage from './pages/admin/SettlementsPage'
import PartnersPage from './pages/admin/PartnersPage'
import ProductsPage from './pages/admin/ProductsPage'
import RebatePoliciesPage from './pages/admin/RebatePoliciesPage'
import CartPage from './pages/market/CartPage'
import OrderDetailPage from './pages/market/OrderDetailPage'
import OrdersPage from './pages/market/OrdersPage'
import MarketProductsPage from './pages/market/ProductsPage'
import RequireAuth from './routes/RequireAuth'

const adminMenus = [
  { to: '/admin', label: '운영 홈' },
  { to: '/admin/orders', label: '주문 관리', matchChildren: true },
  { to: '/admin/settlements', label: '월 정산', matchChildren: true },
  { to: '/admin/products', label: '상품' },
  { to: '/admin/partners', label: '거래처' },
  { to: '/admin/rebate-policies', label: '리베이트 정책' },
]

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/admin',
    element: <RequireAuth role="ADMIN" />,
    children: [
      {
        element: <AppLayout menus={adminMenus} />,
        children: [
          { index: true, element: <AdminHome /> },
          { path: 'orders', element: <AdminOrdersPage /> },
          { path: 'orders/:id', element: <AdminOrderDetailPage /> },
          { path: 'settlements', element: <SettlementsPage /> },
          { path: 'settlements/:yyyymm/:partnerId', element: <SettlementDetailPage /> },
          { path: 'products', element: <ProductsPage /> },
          { path: 'partners', element: <PartnersPage /> },
          { path: 'rebate-policies', element: <RebatePoliciesPage /> },
        ],
      },
    ],
  },
  {
    path: '/market',
    element: <RequireAuth role="PARTNER" />,
    children: [
      {
        element: <MarketLayout />,
        children: [
          { index: true, element: <Navigate to="products" replace /> },
          { path: 'products', element: <MarketProductsPage /> },
          { path: 'cart', element: <CartPage /> },
          { path: 'orders', element: <OrdersPage /> },
          { path: 'orders/:id', element: <OrderDetailPage /> },
        ],
      },
    ],
  },
  // 로그인 페이지가 로그인 상태면 권한별 홈으로 보내 준다
  { path: '*', element: <Navigate to="/login" replace /> },
])
