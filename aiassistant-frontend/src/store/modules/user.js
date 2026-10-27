import { defineStore } from 'pinia'
import { login as apiLogin, register as apiRegister, logout as apiLogout } from '@/api/auth'
import { getToken, setToken, setUser, getUser, clearAuth } from '@/utils/storage'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken(),
    userInfo: getUser()
  }),

  getters: {
    isLoggedIn: state => !!state.token,
    userId: state => state.userInfo?.userId,
    username: state => state.userInfo?.username
  },

  actions: {
    async login(form) {
      const res = await apiLogin(form)
      this.token = res.data.token
      this.userInfo = {
        userId: res.data.userId,
        username: res.data.username,
        nickname: res.data.nickname
      }
      setToken(this.token)
      setUser(this.userInfo)
      return res.data
    },

    async register(form) {
      await apiRegister(form)
    },

    async logout() {
      try {
        await apiLogout()
      } catch {
        // ignore
      }
      this.token = null
      this.userInfo = null
      clearAuth()
    }
  }
})
