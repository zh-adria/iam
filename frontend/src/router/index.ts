import { createRouter, createWebHistory } from 'vue-router'
import { hasRole } from '../api'

const guard = (to: any, _from: any, next: any) => {
  const token = localStorage.getItem('access_token')
  if (to.meta.auth && !token) return next('/login')
  if (to.meta.admin && !hasRole('ROLE_ADMIN')) return next('/dashboard')
  next()
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: () => import('../views/LoginView.vue') },
    { path: '/dashboard', component: () => import('../views/DashboardView.vue'), meta: { auth: true } },
    { path: '/mfa', component: () => import('../views/MfaView.vue'), meta: { auth: true } },
    {
      path: '/admin',
      component: () => import('../views/admin/AdminView.vue'),
      meta: { auth: true, admin: true }
    },
    { path: '/magic-callback', component: () => import('../views/MagicCallbackView.vue') },
    { path: '/callback', component: () => import('../views/OAuth2CallbackView.vue') },
    { path: '/social-callback', component: () => import('../views/SocialCallbackView.vue') }
  ]
})

router.beforeEach(guard)
export default router
