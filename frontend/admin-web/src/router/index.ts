import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { loadAdmin } from '../api/authStorage'

const LoginView = () => import('../views/LoginView.vue')
const OverviewView = () => import('../views/OverviewView.vue')
const UsageView = () => import('../views/UsageView.vue')
const UsersView = () => import('../views/UsersView.vue')
const TeamsView = () => import('../views/TeamsView.vue')
const ImportsView = () => import('../views/ImportsView.vue')
const TasksView = () => import('../views/TasksView.vue')
const ModelsView = () => import('../views/ModelsView.vue')
const SettingsView = () => import('../views/SettingsView.vue')
const EvalView = () => import('../views/EvalView.vue')
const AuditView = () => import('../views/AuditView.vue')
const TeamAuditView = () => import('../views/TeamAuditView.vue')
const AdminsView = () => import('../views/AdminsView.vue')

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
        { path: 'teams', component: TeamsView },
        { path: 'imports', component: ImportsView },
        { path: 'tasks', component: TasksView },
        { path: 'models', component: ModelsView, meta: { superAdmin: true } },
        { path: 'settings', component: SettingsView, meta: { superAdmin: true } },
        { path: 'eval', component: EvalView },
        { path: 'audit', component: AuditView },
        { path: 'team-audit', component: TeamAuditView },
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
