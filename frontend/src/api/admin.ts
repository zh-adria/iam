import axios from 'axios'

const http = axios.create({ baseURL: '/iam/admin/api' })

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
      if (!location.pathname.startsWith('/login')) location.href = '/login'
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
  async listRoles(tenant?: string): Promise<RoleRow[]> {
    const { data } = await http.get('/roles', { params: { tenant } })
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
  async listPermissions(): Promise<PermRow[]> {
    const { data } = await http.get('/permissions')
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
  async listTenants(): Promise<TenantRow[]> {
    const { data } = await http.get('/tenants')
    return data.data
  },
  async upsertTenant(b: Record<string, unknown>): Promise<void> {
    await http.post('/tenants', b)
  },
  async deleteTenant(code: string): Promise<void> {
    await http.delete(`/tenants/${code}`)
  },
  // clients
  async listClients(): Promise<ClientRow[]> {
    const { data } = await http.get('/oauth2/clients')
    return data.data
  },
  async upsertClient(b: Record<string, string>): Promise<void> {
    await http.post('/oauth2/clients', b)
  },
  async deleteClient(clientId: string): Promise<void> {
    await http.delete(`/oauth2/clients/${clientId}`)
  },
  // audit
  async listAudit(page = 1, size = 50, userId?: number): Promise<Page<AuditRow>> {
    const { data } = await http.get('/audit', { params: { page, size, userId } })
    return data.data
  },
  // config
  async config(): Promise<Record<string, unknown>> {
    const { data } = await http.get('/config')
    return data.data
  }
}
