import { Navigate, Outlet, useLocation } from 'react-router'
import { useMe } from '../api/auth'
import type { Role } from '../types/auth'
import { homePath } from './paths'

interface Props {
  role: Role
}

export default function RequireAuth({ role }: Props) {
  const { data: me, isPending, isError, refetch } = useMe()
  const location = useLocation()

  if (isPending) {
    return <p className="page-status">로그인 상태를 확인하고 있습니다.</p>
  }
  if (isError) {
    return (
      <div className="page-status">
        <p>로그인 상태를 확인하지 못했습니다.</p>
        <button type="button" className="button button-secondary" onClick={() => refetch()}>
          다시 확인
        </button>
      </div>
    )
  }
  if (!me) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  if (me.role !== role) {
    return <Navigate to={homePath(me.role)} replace />
  }
  return <Outlet />
}
