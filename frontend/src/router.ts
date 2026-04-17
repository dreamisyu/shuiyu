import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from './stores/auth'
import LoginView from './views/LoginView.vue'
import LobbyView from './views/LobbyView.vue'
import RoomView from './views/RoomView.vue'
import HistoryView from './views/HistoryView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/lobby' },
    { path: '/login', component: LoginView, meta: { guestOnly: true } },
    { path: '/lobby', component: LobbyView, meta: { auth: true } },
    { path: '/room/:roomId', component: RoomView, meta: { auth: true } },
    { path: '/history', component: HistoryView, meta: { auth: true } },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  auth.hydrate()

  if (to.meta.auth && !auth.isLoggedIn) {
    return '/login'
  }
  if (to.meta.guestOnly && auth.isLoggedIn) {
    return '/lobby'
  }
  return true
})

export default router
