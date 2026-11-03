import { createContext, useCallback, useContext, useEffect, useState, ReactNode } from 'react'
import { api, getToken, setToken, User } from './api'

interface AuthCtx {
  user: User | null
  notRead: number
  login: (user: User, token: string) => void
  logout: () => void
  refreshNotRead: () => void
}
const Ctx = createContext<AuthCtx>(null as any)
export const useAuth = () => useContext(Ctx)

const USER_KEY = 'pybbs_user'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => {
    try {
      return getToken() ? JSON.parse(localStorage.getItem(USER_KEY) || 'null') : null
    } catch {
      return null
    }
  })
  const [notRead, setNotRead] = useState(0)

  const login = (u: User, token: string) => {
    setToken(token)
    localStorage.setItem(USER_KEY, JSON.stringify(u))
    setUser(u)
  }
  const logout = () => {
    setToken(null)
    localStorage.removeItem(USER_KEY)
    setUser(null)
    setNotRead(0)
  }
  const refreshNotRead = useCallback(() => {
    if (!getToken()) return
    api.get<number>('/api/notification/notRead').then(setNotRead).catch(() => {})
  }, [])

  useEffect(() => {
    if (user) refreshNotRead()
  }, [user, refreshNotRead])

  return <Ctx.Provider value={{ user, notRead, login, logout, refreshNotRead }}>{children}</Ctx.Provider>
}
