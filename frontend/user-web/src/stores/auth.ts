import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  clearSession,
  getAccessToken,
  loadUser,
  saveSession,
  saveUser,
} from '../api/authStorage'
import type { TokenResponse, UserSummary } from '../api/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(getAccessToken())
  const user = ref<UserSummary | null>(loadUser())
  const isLoggedIn = computed(() => Boolean(token.value))

  function setSession(payload: TokenResponse) {
    saveSession(payload)
    token.value = payload.accessToken
    user.value = payload.user
  }

  function updateUser(next: UserSummary) {
    saveUser(next)
    user.value = next
  }

  function clear() {
    clearSession()
    token.value = ''
    user.value = null
  }

  return { token, user, isLoggedIn, setSession, updateUser, clear }
})
