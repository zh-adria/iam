import axios from 'axios'

export interface LoginResp {
  accessToken: string
  refreshToken?: string
  idToken?: string
  expiresIn: number
  mfaRequired: boolean
  mfaToken?: string
  roles?: string[]
  permissions?: string[]
}

// 后端 Spring Security 在部分场景（未认证、SAML 跳转）会返回 302 重定向。
// 让浏览器/XHR 不顺从，直接暴露真实 status code，便于排查显示错误提示。
const http = axios.create({ baseURL: '/iam/api', maxRedirects: 0 })

http.interceptors.request.use(cfg => {
  const t = localStorage.getItem('access_token')
  if (t) cfg.headers.Authorization = `Bearer ${t}`
  return cfg
})

http.interceptors.response.use(
  r => r,
  err => {
    // 401 → 清 token 跳登录。但只在非 /login 路径时跳，避免 login 失败时循环重定向。
    if (err.response?.status === 401
        && !location.pathname.startsWith('/login')
        && !location.pathname.startsWith('/mfa')) {
      localStorage.removeItem('access_token')
      localStorage.removeItem('roles')
      location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export const api = {
  async login(payload: { username: string; password: string; tenantCode?: string }): Promise<LoginResp> {
    // ponytail: 登录请求强制无 token，避免残留旧 token 导致 Spring Security 拒请求(401/403) 或走错 auth 路径。
    const { data } = await http.post('/auth/login', { grantType: 'password', clientId: 'iam-self', ...payload },
      { headers: { Authorization: '' } })
    return data.data
  },
  async verifyMfa(mfaToken: string, code: string): Promise<LoginResp> {
    const { data } = await http.post('/auth/mfa/verify', { mfaToken, code })
    return data.data
  },
  async logout(): Promise<void> {
    await http.post('/auth/logout', {})
    localStorage.removeItem('access_token')
    localStorage.removeItem('roles')
  },
  async me(): Promise<Record<string, unknown>> {
    const { data } = await http.get('/users/me')
    return data.data
  },
  async setupMfa(): Promise<{ secret: string; otpauth: string }> {
    const { data } = await http.post('/users/mfa/setup', {})
    return data.data
  },
  async confirmMfa(code: string): Promise<void> {
    await http.post('/users/mfa/confirm', { code })
  },
  // social
  async socialAuthorize(provider: string): Promise<string> {
    const { data } = await http.get(`/auth/social/${provider}/authorize`)
    return data.data
  },
  // sms
  async smsSend(phone: string): Promise<void> {
    await http.post('/auth/sms/send', { phone })
  },
  async smsLogin(phone: string, code: string): Promise<LoginResp> {
    const { data } = await http.post('/auth/sms/login', { phone, code })
    return data.data
  },
  // magic link
  async magicSend(email: string): Promise<void> {
    await http.post('/auth/magic/send', { email })
  },
  async magicVerify(token: string): Promise<LoginResp> {
    const { data } = await http.get('/auth/magic/verify', { params: { token } })
    return data.data
  },
  // cas
  async casAuthorize(): Promise<string> {
    const { data } = await http.get('/auth/cas/authorize')
    return data.data
  }
}

export function saveSession(r: LoginResp): void {
  localStorage.setItem('access_token', r.accessToken)
  if (r.refreshToken) localStorage.setItem('refresh_token', r.refreshToken)
  if (r.roles?.length) localStorage.setItem('roles', JSON.stringify(r.roles))
}

export function hasRole(role: string): boolean {
  try {
    const roles: string[] = JSON.parse(localStorage.getItem('roles') || '[]')
    return roles.includes(role)
  } catch {
    return false
  }
}
