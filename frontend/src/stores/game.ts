import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '../api'
import type { GameStateView } from '../types'

export const useGameStore = defineStore('game', () => {
  const state = ref<GameStateView | null>(null)
  const loading = ref(false)

  async function fetchState(roomId: number) {
    loading.value = true
    try {
      state.value = await api.fetchState(roomId)
      return state.value
    } finally {
      loading.value = false
    }
  }

  async function deal(roomId: number) {
    state.value = await api.deal(roomId)
    return state.value
  }

  async function submitGroup(payload: {
    roomId: number
    userId: number
    group1: string[]
    group2: string[]
    changeCard1?: string | null
    changeCard2?: string | null
  }) {
    state.value = await api.submitGroup(payload)
    return state.value
  }

  async function reportFish(payload: { roomId: number; userId: number; fishType: number }) {
    state.value = await api.reportFish(payload)
    return state.value
  }

  async function attack(payload: {
    roomId: number
    userId: number
    attackType: number
    bankerResponse?: number | null
    acceptCallKill?: boolean | null
  }) {
    state.value = await api.attack(payload)
    return state.value
  }

  async function respond(payload: { roomId: number; userId: number; idleUserId: number; bankerResponse: number }) {
    state.value = await api.respond(payload)
    return state.value
  }

  async function decide(payload: { roomId: number; userId: number; acceptCallKill: boolean }) {
    state.value = await api.decide(payload)
    return state.value
  }

  async function processPending(payload: { roomId: number; userId: number; idleUserId: number }) {
    state.value = await api.processPending(payload)
    return state.value
  }

  async function readyNextRound(payload: { roomId: number; userId: number }) {
    state.value = await api.readyNextRound(payload)
    return state.value
  }

  function clear() {
    state.value = null
  }

  return {
    state,
    loading,
    fetchState,
    deal,
    submitGroup,
    reportFish,
    attack,
    respond,
    decide,
    processPending,
    readyNextRound,
    clear,
  }
})
