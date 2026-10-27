import { NavLink, Outlet, useNavigate } from 'react-router'
import { useLogout, useMe } from '../api/auth'

export interface MenuItem {
  to: string
  label: string
  /** 메뉴 옆에 숫자로 보여 줄 값 (예: 장바구니 품목 수) */
  count?: number
  /** true면 하위 경로에서도 메뉴를 선택 상태로 표시 */
  matchChildren?: boolean
}

interface Props {
  menus: MenuItem[]
}

export default function AppLayout({ menus }: Props) {
  const { data: me } = useMe()
  const logout = useLogout()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout.mutate(undefined, {
      onSettled: () => navigate('/login', { replace: true }),
    })
  }

  return (
    <div className="shell">
      <header className="shell-header">
        <span className="shell-brand">mini-erp</span>
        <nav className="shell-nav" aria-label="주 메뉴">
          {menus.map((menu) => (
            <NavLink key={menu.to} to={menu.to} end={!menu.matchChildren}>
              {menu.label}
              {menu.count ? <span className="nav-count">{menu.count}</span> : null}
            </NavLink>
          ))}
        </nav>
        <div className="shell-user">
          <span>
            {me?.partnerName && <span className="shell-partner">{me.partnerName}</span>}
            {me?.name}
          </span>
          <button
            type="button"
            className="button button-secondary"
            onClick={handleLogout}
            disabled={logout.isPending}
          >
            로그아웃
          </button>
        </div>
      </header>
      <main className="shell-main">
        <Outlet />
      </main>
    </div>
  )
}
