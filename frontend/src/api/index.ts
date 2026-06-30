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

const http = axios.create({ baseURL: '/iam/api' })

http.interceptors.request.use(cfg => {
  const t = localStorage.getItem('access_token')
  if (t) cfg.headers.Authorization = `Bearer ${t}`
  return cfg
})

http.interceptors.response.use(
  r => r,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('access_token')
      localStorage.removeItem('roles')
      location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export const api = {
  async login(payload: { username: string; password: string; tenantCode?: string }): Promise<LoginResp> {
    const { data } = await http.post('/auth/login', { grantType: 'password', clientId: 'iam-self', ...payload })
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
