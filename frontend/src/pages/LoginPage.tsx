import { useForm } from 'react-hook-form'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { useLogin, useMe } from '../api/auth'
import { homePath } from '../routes/paths'
import type { LoginForm } from '../types/auth'

const FLOW = ['거래처 주문 요청', '승인과 재고 차감', '출고 처리', '월 정산 마감']

export default function LoginPage() {
  const { data: me } = useMe()
  const login = useLogin()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginForm>({ defaultValues: { loginId: '', password: '' } })

  if (me) {
    return <Navigate to={homePath(me.role)} replace />
  }

  const onSubmit = handleSubmit((form) => {
    login.mutate(form, {
      onSuccess: (user) => {
        const home = homePath(user.role)
        // 권한이 다른 화면으로 돌아가지 않도록 자기 영역 경로일 때만 원래 위치로 보낸다
        navigate(from && from.startsWith(home) ? from : home, { replace: true })
      },
    })
  })

  return (
    <div className="login">
      <section className="login-intro" aria-labelledby="login-title">
        <h1 id="login-title" className="login-title">mini-erp</h1>
        <p className="login-summary">
          IT 유통 거래처의 주문부터 월 정산 마감까지 처리하는 업무 시스템입니다.
        </p>
        <ol className="login-flow">
          {FLOW.map((step) => (
            <li key={step}>{step}</li>
          ))}
        </ol>
      </section>

      <section className="login-panel">
        <form className="login-form" onSubmit={onSubmit} noValidate>
          <h2 className="login-form-title">로그인</h2>

          <div className="field">
            <label htmlFor="loginId">아이디</label>
            <input
              id="loginId"
              autoComplete="username"
              aria-invalid={errors.loginId ? 'true' : 'false'}
              aria-describedby={errors.loginId ? 'loginId-error' : undefined}
              {...register('loginId', { required: '아이디를 입력해 주세요.' })}
            />
            {errors.loginId && (
              <p id="loginId-error" className="field-error">{errors.loginId.message}</p>
            )}
          </div>

          <div className="field">
            <label htmlFor="password">비밀번호</label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              aria-invalid={errors.password ? 'true' : 'false'}
              aria-describedby={errors.password ? 'password-error' : undefined}
              {...register('password', { required: '비밀번호를 입력해 주세요.' })}
            />
            {errors.password && (
              <p id="password-error" className="field-error">{errors.password.message}</p>
            )}
          </div>

          {login.isError && (
            <p className="form-error" role="alert">{login.error.message}</p>
          )}

          <button type="submit" className="button button-primary" disabled={login.isPending}>
            {login.isPending ? '로그인 중' : '로그인'}
          </button>
        </form>
      </section>
    </div>
  )
}
