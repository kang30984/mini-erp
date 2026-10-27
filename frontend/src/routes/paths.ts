import type { Role } from '../types/auth'

export function homePath(role: Role): string {
  return role === 'ADMIN' ? '/admin' : '/market'
}
