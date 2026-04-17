import axios from 'axios'
import type {
  ApiEnvelope,
  AuthResponse,
  GameStateView,
  HistoryDetailView,
  HistoryItemView,
  RoomView,
} from './types'

const API_BASE_URL = '/api'

const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('shuiyu-token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export const apiBaseUrl = API_BASE_URL

function unwrap<T>(payload: ApiEnvelope<T>) {
  if (!payload.success) {
    throw new Error(payload.message)
  }
  return payload.data
}

export const api = {
  register(payload: { username: string; password: string; nickname: string }) {
    return http.post<ApiEnvelope<AuthResponse>>('/auth/register', payload).then(({ data }) => unwrap(data))
  },
  login(payload: { username: string; password: string }) {
    return http.post<ApiEnvelope<AuthResponse>>('/auth/login', payload).then(({ data }) => unwrap(data))
  },
  createRoom(payload: { userId: number; bankerType: number; maxIdle: number; idleTypeList: number[] }) {
    return http.post<ApiEnvelope<RoomView>>('/room/create', payload).then(({ data }) => unwrap(data))
  },
  joinRoom(payload: { roomCode: string; userId: number }) {
    return http.post<ApiEnvelope<RoomView>>('/room/join', payload).then(({ data }) => unwrap(data))
  },
  destroyRoom(payload: { roomId: number; userId: number }) {
    return http.post<ApiEnvelope<null>>('/room/destroy', payload).then(({ data }) => unwrap(data))
  },
  deal(roomId: number) {
    return http.post<ApiEnvelope<GameStateView>>('/game/deal', null, { params: { roomId } }).then(({ data }) => unwrap(data))
  },
  fetchState(roomId: number) {
    return http.get<ApiEnvelope<GameStateView>>('/game/state', { params: { roomId } }).then(({ data }) => unwrap(data))
  },
  submitGroup(payload: {
    roomId: number
    userId: number
    group1: string[]
    group2: string[]
    changeCard1?: string | null
    changeCard2?: string | null
  }) {
    return http.post<ApiEnvelope<GameStateView>>('/game/group', payload).then(({ data }) => unwrap(data))
  },
  reportFish(payload: { roomId: number; userId: number; fishType: number }) {
    return http.post<ApiEnvelope<GameStateView>>('/game/reportFish', payload).then(({ data }) => unwrap(data))
  },
  attack(payload: {
    roomId: number
    userId: number
    attackType: number
    bankerResponse?: number | null
    acceptCallKill?: boolean | null
  }) {
    return http.post<ApiEnvelope<GameStateView>>('/game/attack', payload).then(({ data }) => unwrap(data))
  },
  respond(payload: { roomId: number; userId: number; idleUserId: number; bankerResponse: number }) {
    return http.post<ApiEnvelope<GameStateView>>('/game/respond', payload).then(({ data }) => unwrap(data))
  },
  decide(payload: { roomId: number; userId: number; acceptCallKill: boolean }) {
    return http.post<ApiEnvelope<GameStateView>>('/game/decide', payload).then(({ data }) => unwrap(data))
  },
  processPending(payload: { roomId: number; userId: number; idleUserId: number }) {
    return http.post<ApiEnvelope<GameStateView>>('/game/processPending', payload).then(({ data }) => unwrap(data))
  },
  readyNextRound(payload: { roomId: number; userId: number }) {
    return http.post<ApiEnvelope<GameStateView>>('/game/readyNextRound', payload).then(({ data }) => unwrap(data))
  },
  fetchHistory() {
    return http.get<ApiEnvelope<HistoryItemView[]>>('/history/list').then(({ data }) => unwrap(data))
  },
  fetchHistoryDetail(matchId: number) {
    return http.get<ApiEnvelope<HistoryDetailView>>(`/history/detail/${matchId}`).then(({ data }) => unwrap(data))
  },
}
