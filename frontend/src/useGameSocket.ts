import { onBeforeUnmount } from 'vue'

type GameSocketEvent = {
  type?: string
  message?: string
  roomId?: number
}

function buildWsUrl(roomId: number) {
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://127.0.0.1:8088'
  const url = new URL(apiBaseUrl)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  url.pathname = `/ws/game/${roomId}`
  url.search = ''
  return url.toString()
}

export function useGameSocket(
  roomId: number,
  onMessage: () => void,
  onEvent?: (event: GameSocketEvent) => boolean | void,
) {
  let socket: WebSocket | null = null
  let reconnectTimer: number | null = null

  const clearReconnect = () => {
    if (reconnectTimer !== null) {
      window.clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  const scheduleReconnect = () => {
    if (reconnectTimer !== null) return
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = null
      connect()
    }, 1000)
  }

  const connect = () => {
    clearReconnect()
    socket?.close()
    socket = new WebSocket(buildWsUrl(roomId))
    socket.onopen = () => onMessage()
    socket.onmessage = (event) => {
      let shouldRefresh = true
      try {
        const payload = JSON.parse(event.data) as GameSocketEvent
        if (onEvent) {
          shouldRefresh = onEvent(payload) !== false
        }
      } catch {
        shouldRefresh = true
      }
      if (shouldRefresh) {
        onMessage()
      }
    }
    socket.onerror = () => scheduleReconnect()
    socket.onclose = () => scheduleReconnect()
  }

  const disconnect = () => {
    clearReconnect()
    socket?.close()
    socket = null
  }

  connect()
  onBeforeUnmount(disconnect)

  return {
    reconnect() {
      connect()
    },
  }
}
