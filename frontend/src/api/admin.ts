import axios from 'axios'

const http = axios.create({ baseURL: '/iam/admin/api', maxRedirects: 0 })

http.interceptors.request.use(cfg => {
  const t = localStorage.getItem('access_token')
  if (t) cfg.headers.Authorization = `Bearer ${t}`
  return cfg
})

// 全局错误处理：
// 1) 401 → 清 token 跳 /login
// 2) 4xx/5xx 且后端返回 { message: ... } → 自动 toast（前端业务代码可不再 catch）
// 3) 网络错误 → 自动 toast
// 注意：业务代码仍可通过 try/catch 拿到 err 做额外处理（如表单校验）
let lastToastAt = 0
http.interceptors.response.use(
  r => r,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('access_token')
      localStorage.removeItem('roles')
      if (!location.pathname.startsWith('/login')) location.href = '/login'
      return Promise.reject(err)
    }
    // 节流：1 秒内不重复 toast
    const now = Date.now()
    if (now - lastToastAt > 1000) {
      lastToastAt = now
      const msg = err.response?.data?.message
        || err.response?.data?.error
        || err.message
        || '请求失败'
      // 动态 import 避免循环依赖
      import('element-plus').then(m => m.ElMessage?.error?.(`操作失败：${msg}`))
    }
    return Promise.reject(err)
  }
)

export interface Page<T> { total: number; page: number; size: number; rows: T[] }

export interface UserRow {
  id: number
  username: string
  email: string
  phone: string
  tenant: string
  status: number
  mfaEnabled: boolean
  roles: string[]
}
export interface RolePerms { code: string; perms: string[] }
export interface UserRolesRes { id: number; roles: string[] }

export interface RoleRow { code: string; name: string; tenant: string }
export interface PermRow { code: string; type: string; name: string; resource: string; action: string; spel: string }
export interface TenantRow { id: number; code: string; name: string; isolationMode: string; schemaName: string; ldapUrl: string; ldapBase: string; enabled: boolean }
export interface ClientRow { clientId: string; grantTypes: string; redirectUris: string; scopes: string; createdAt: string }
export interface AuditRow { id: number; userId: number; tenant: string; action: string; result: string; principal: string; ip: string; detail: string; occurredAt: string; prevHash: string }
export interface ConfigItem { key: string; value: string; type: string; description?: string }
export interface ConfigResponse { items: ConfigItem[] }
export interface SamlIdpRow {
  id?: number
  tenantCode: string
  registrationId: string
  idpEntityId: string
  idpSsoUrl?: string
  idpMetadataUrl?: string
  idpMetadataXml?: string
  spEntityId?: string
  acsTemplate?: string
  enabled?: boolean
}

export const adminApi = {
  // users
  async listUsers(page = 1, size = 20, tenant?: string): Promise<Page<UserRow>> {
    const { data } = await http.get('/users', { params: { page, size, tenant } })
    return data.data
  },
  async createUser(b: Record<string, unknown>): Promise<UserRow> {
    const { data } = await http.post('/users', b)
    return data.data
  },
  async resetPassword(id: number, password: string): Promise<void> {
    await http.post(`/users/${id}/reset-password`, { password })
  },
  async setUserStatus(id: number, status: number): Promise<void> {
    await http.post(`/users/${id}/status`, { status })
  },
  async unlockUser(id: number): Promise<void> {
    await http.post(`/users/${id}/unlock`)
  },
  async deleteUser(id: number): Promise<void> {
    await http.delete(`/users/${id}`)
  },
  async assignRole(id: number, role: string): Promise<void> {
    await http.post(`/users/${id}/roles/${role}`)
  },
  async revokeRole(id: number, role: string): Promise<void> {
    await http.delete(`/users/${id}/roles/${role}`)
  },
  // roles
  async listRoles(page = 1, size = 20, tenant?: string): Promise<Page<RoleRow>> {
    const { data } = await http.get('/roles', { params: { page, size, tenant } })
    return data.data
  },
  async createRole(code: string, name: string, tenantCode?: string): Promise<void> {
    await http.post('/roles', { code, name, tenantCode })
  },
  async deleteRole(code: string): Promise<void> {
    await http.delete(`/roles/${code}`)
  },
  async listRolePermissions(role: string): Promise<string[]> {
    const { data } = await http.get(`/roles/${role}/permissions`)
    return data.data || []
  },
  // permissions
  async listPermissions(page = 1, size = 20): Promise<Page<PermRow>> {
    const { data } = await http.get('/permissions', { params: { page, size } })
    return data.data
  },
  async createPermission(b: Record<string, string>): Promise<void> {
    await http.post('/permissions', b)
  },
  async deletePermission(code: string): Promise<void> {
    await http.delete(`/permissions/${code}`)
  },
  async grantPermission(role: string, perm: string): Promise<void> {
    await http.post(`/roles/${role}/permissions/${perm}`)
  },
  async revokePermission(role: string, perm: string): Promise<void> {
    await http.delete(`/roles/${role}/permissions/${perm}`)
  },
  // tenants
  async listTenants(page = 1, size = 20): Promise<Page<TenantRow>> {
    const { data } = await http.get('/tenants', { params: { page, size } })
    return data.data
  },
  async upsertTenant(b: Record<string, unknown>): Promise<void> {
    await http.post('/tenants', b)
  },
  async deleteTenant(code: string): Promise<void> {
    const tenantCode = code.trim()
    if (!tenantCode) throw new Error('租户编码不能为空')
    await http.delete(`/tenants/${encodeURIComponent(tenantCode)}`)
  },
  // clients
  async listClients(page = 1, size = 20): Promise<Page<ClientRow>> {
    const { data } = await http.get('/oauth2/clients', { params: { page, size } })
    return data.data
  },
  async upsertClient(b: Record<string, string>): Promise<void> {
    await http.post('/oauth2/clients', b)
  },
  async deleteClient(clientId: string): Promise<void> {
    await http.delete(`/oauth2/clients/${clientId}`)
  },
  // SAML
  async listSamlIdps(tenant?: string): Promise<SamlIdpRow[]> {
    const { data } = await http.get('/saml/idps', { params: tenant ? { tenant } : {} })
    return data.data || []
  },
  async upsertSamlIdp(item: SamlIdpRow): Promise<void> {
    await http.post('/saml/idps', item)
  },
  async deleteSamlIdp(tenantCode: string, registrationId: string): Promise<void> {
    await http.delete(`/saml/idps/${encodeURIComponent(tenantCode)}/${encodeURIComponent(registrationId)}`)
  },
  // audit
  async listAudit(page = 1, size = 50, userId?: number): Promise<Page<AuditRow>> {
    const { data } = await http.get('/audit', { params: { page, size, userId } })
    return data.data
  },
  // config
  async config(): Promise<ConfigResponse> {
    const { data } = await http.get('/config')
    return data.data
  },
  async updateConfig(item: ConfigItem): Promise<void> {
    await http.put('/config', item)
  },
  async deleteConfig(key: string): Promise<void> {
    await http.delete(`/config/${encodeURIComponent(key)}`)
  }
}
