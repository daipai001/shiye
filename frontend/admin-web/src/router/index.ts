import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import LoginView from '../views/LoginView.vue'
import OverviewView from '../views/OverviewView.vue'
import UsageView from '../views/UsageView.vue'
import UsersView from '../views/UsersView.vue'
import ImportsView from '../views/ImportsView.vue'
import TasksView from '../views/TasksView.vue'
import ModelsView from '../views/ModelsView.vue'
import SettingsView from '../views/SettingsView.vue'
import AuditView from '../views/AuditView.vue'
import EvalView from '../views/EvalView.vue'
import AdminsView from '../views/AdminsView.vue'
import { loadAdmin } from '../api/authStorage'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView, meta: { public: true } },
    {
      path: '/',
      component: AdminLayout,
      redirect: '/overview',
      children: [
        { path: 'overview', component: OverviewView },
        { path: 'usage', component: UsageView },
        { path: 'users', component: UsersView },
        { path: 'imports', component: ImportsView },
        { path: 'tasks', component: TasksView },
        { path: 'models', component: ModelsView, meta: { superAdmin: true } },
        { path: 'settings', component: SettingsView, meta: { superAdmin: true } },
        { path: 'eval', component: EvalView },
        { path: 'audit', component: AuditView },
        { path: 'admins', component: AdminsView, meta: { superAdmin: true } },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const token = localStorage.getItem('shiye_admin_token')
  if (!to.meta.public && !token) return { path: '/login' }
  if (to.path === '/login' && token) return { path: '/overview' }
  const admin = loadAdmin()
  if (to.meta.superAdmin && admin?.role !== 'SUPER_ADMIN') {
    return { path: '/overview' }
  }
  return true
})

export default router
