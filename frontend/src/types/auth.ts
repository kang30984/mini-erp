export type Role = 'ADMIN' | 'PARTNER'

export interface Me {
  id: number
  loginId: string
  name: string
  role: Role
  partnerId: number | null
  partnerName: string | null
}

export interface LoginForm {
  loginId: string
  password: string
}
