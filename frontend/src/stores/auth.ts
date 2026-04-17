import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api } from '../api'
import type { UserProfile } from '../types'

const TOKEN_KEY = 'shuiyu-token'
const USER_KEY = 'shuiyu-user'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const user = ref<UserProfile | null>(null)

  const isLoggedIn = computed(() => Boolean(token.value && user.value))

  function hydrate() {
    if (!token.value) {
      token.value = localStorage.getItem(TOKEN_KEY)
    }
    if (!user.value) {
      const raw = localStorage.getItem(USER_KEY)
      user.value = raw ? JSON.parse(raw) as UserProfile : null
    }
  }

  async function login(username: string, password: string) {
    const response = await api.login({ username, password })
    persist(response.token, response.user)
  }

  async function register(username: string, password: string, nickname: string) {
    const response = await api.register({ username, password, nickname })
    persist(response.token, response.user)
  }

  function persist(nextToken: string, nextUser: UserProfile) {
    token.value = nextToken
    user.value = nextUser
    localStorage.setItem(TOKEN_KEY, nextToken)
    localStorage.setItem(USER_KEY, JSON.stringify(nextUser))
  }

  function logout() {
    token.value = null
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return {
    token,
    user,
    isLoggedIn,
    hydrate,
    login,
    register,
    logout,
  }
})
