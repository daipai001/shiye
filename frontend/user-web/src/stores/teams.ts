import { ref } from 'vue'
import { defineStore } from 'pinia'
import api from '../api/client'
import type { ApiResult, Team } from '../api/types'

export const useTeamStore = defineStore('teams', () => {
  const teams = ref<Team[]>([])
  const loading = ref(false)

  async function load() {
    loading.value = true
    try {
      const { data } = await api.get<ApiResult<Team[]>>('/teams')
      teams.value = data.data
    } catch {
      teams.value = []
    } finally {
      loading.value = false
    }
  }

  return { teams, loading, load }
})
