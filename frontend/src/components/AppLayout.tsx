import { NavLink, Outlet, useNavigate } from 'react-router'
import { useLogout, useMe } from '../api/auth'

interface MenuItem {
  to: string
  label: string
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
            <NavLink key={menu.to} to={menu.to} end>
              {menu.label}
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
