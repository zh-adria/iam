<template>
  <div class="admin-wrap">
    <el-container>
      <el-aside width="200px">
        <el-menu :default-active="tab" @select="t => tab = t">
          <el-menu-item index="users">用户管理</el-menu-item>
          <el-menu-item index="roles">角色管理</el-menu-item>
          <el-menu-item index="perms">权限管理</el-menu-item>
          <el-menu-item index="clients">OAuth2 客户端</el-menu-item>
          <el-menu-item index="tenants">租户管理</el-menu-item>
          <el-menu-item index="audit">审计日志</el-menu-item>
          <el-menu-item index="config">系统配置</el-menu-item>
        </el-menu>
      </el-aside>
      <el-main>
        <component :is="views[tab]" />
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, defineComponent } from 'vue'
import UsersPane from './panes/UsersPane.vue'
import RolesPane from './panes/RolesPane.vue'
import PermsPane from './panes/PermsPane.vue'
import ClientsPane from './panes/ClientsPane.vue'
import TenantsPane from './panes/TenantsPane.vue'
import AuditPane from './panes/AuditPane.vue'
import ConfigPane from './panes/ConfigPane.vue'

const tab = ref('users')
const views: Record<string, ReturnType<typeof defineComponent>> = {
  users: UsersPane,
  roles: RolesPane,
  perms: PermsPane,
  clients: ClientsPane,
  tenants: TenantsPane,
  audit: AuditPane,
  config: ConfigPane
}
</script>

<style scoped>
.admin-wrap { min-height: 100vh; }
.el-aside { background: #fff; border-right: 1px solid #eee; }
</style>
