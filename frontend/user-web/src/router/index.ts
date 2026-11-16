import { createRouter, createWebHistory } from 'vue-router'
import BasicLayout from '../layouts/BasicLayout.vue'
import LoginView from '../views/LoginView.vue'
import LibraryView from '../views/LibraryView.vue'
import ChatView from '../views/ChatView.vue'
import UsageView from '../views/UsageView.vue'
import SettingsView from '../views/SettingsView.vue'
import PluginView from '../views/PluginView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView, meta: { public: true } },
    {
      path: '/',
      component: BasicLayout,
      redirect: '/library',
      children: [
        { path: 'library', component: LibraryView },
        { path: 'chat', component: ChatView },
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
