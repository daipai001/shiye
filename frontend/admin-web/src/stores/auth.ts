import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  clearSession,
  getAccessToken,
  loadAdmin,
  saveAdmin,
  saveSession,
} from '../api/authStorage'
import type { AdminSummary, AdminTokenResponse } from '../api/types'

export const useAdminAuthStore = defineStore('admin-auth', () => {
  const token = ref(getAccessToken())
  const admin = ref<AdminSummary | null>(loadAdmin())
  const isLoggedIn = computed(() => Boolean(token.value))
  const isSuperAdmin = computed(() => admin.value?.role === 'SUPER_ADMIN')
  const canWrite = computed(() => admin.value?.role !== 'VIEWER')

  function setSession(payload: AdminTokenResponse) {
    saveSession(payload)
    token.value = payload.accessToken
    admin.value = payload.admin
  }

  function updateAdmin(next: AdminSummary) {
    saveAdmin(next)
    admin.value = next
  }

  function clear() {
    clearSession()
    token.value = ''
    admin.value = null
  }

  return {
    token,
    admin,
    isLoggedIn,
    isSuperAdmin,
    canWrite,
    setSession,
    updateAdmin,
    clear,
  }
})
