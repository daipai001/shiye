import { createRouter, createWebHistory } from 'vue-router'
import BasicLayout from '../layouts/BasicLayout.vue'

const LoginView = () => import('../views/LoginView.vue')
const VerifyEmailView = () => import('../views/VerifyEmailView.vue')
const ResetPasswordView = () => import('../views/ResetPasswordView.vue')
const LibraryView = () => import('../views/LibraryView.vue')
const ChatView = () => import('../views/ChatView.vue')
const TeamsView = () => import('../views/TeamsView.vue')
const UsageView = () => import('../views/UsageView.vue')
const SettingsView = () => import('../views/SettingsView.vue')
const PluginView = () => import('../views/PluginView.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView, meta: { public: true } },
    { path: '/verify-email', component: VerifyEmailView, meta: { public: true } },
    { path: '/reset-password', component: ResetPasswordView, meta: { public: true } },
    {
      path: '/',
      component: BasicLayout,
      redirect: '/library',
      children: [
        { path: 'library', component: LibraryView },
        { path: 'chat', component: ChatView },
        { path: 'teams', component: TeamsView },
        { path: 'usage', component: UsageView },
        { path: 'settings', component: SettingsView },
        { path: 'plugin', component: PluginView },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const token = localStorage.getItem('shiye_user_token')
  if (!to.meta.public && !token) return { path: '/login' }
  if (to.path === '/login' && token) return { path: '/library' }
  return true
})

export default router
