export interface ApiEnvelope<T> {
  success: boolean
  message: string
  data: T
}

export interface UserProfile {
  id: number
  username: string
  nickname: string
  avatar: string
  userType: number
}

export interface AuthResponse {
  token: string
  user: UserProfile
}

export interface RoomMemberView {
  userId: number
  nickname: string
  avatar: string
  userType: number
  userRole: number
  idleSort: number | null
  idleType: number | null
  currentTurn: boolean
  online: boolean
}

export interface RoomView {
  roomId: number
  roomCode: string
  houseOwnerId: number
  bankerUserId: number
  bankerType: number
  roomStatus: number
  maxIdle: number
  currentIdle: number
  members: RoomMemberView[]
}

export interface PlayerCardsView {
  userId: number
  nickname: string
  userRole: number
  idleSort: number | null
  faceDown: boolean
  grouped: boolean
  reportedFish: boolean
  readyNextRound: boolean
  fishType: number | null
  handCards: string[]
  group1: string[]
  group2: string[]
  changeCard1: string | null
  changeCard2: string | null
}

export interface PendingActionView {
  stage: 'WAIT_BANKER_RESPONSE' | 'WAIT_IDLE_DECISION' | 'WAIT_BANKER_GROUP'
  attackType: 'STRONG' | 'ASK_RUN' | 'HIDDEN' | 'FISH'
  bankerUserId: number
  idleUserId: number
  idleNickname: string
  idleSort: number
  message: string
}

export interface RoundSettleView {
  idleUserId: number
  idleNickname: string
  idleSort: number | null
  attackType: number
  bankerResponse: number | null
  result: number
  rate: number
  drinkUserId: number | null
  settleTime: string
}

export interface GlobalSettleView {
  bankerWinCount: number
  idleWinCount: number
  drawCount: number
  maxRate: number
  settleTime: string
  settleDetail: string
}

export interface GameStateView {
  room: RoomView
  matchId: number | null
  matchStatus: number | null
  currentIdleSort: number | null
  bankerGroupLocked: boolean
  players: PlayerCardsView[]
  roundSettles: RoundSettleView[]
  globalSettle: GlobalSettleView | null
  pendingActions: PendingActionView[]
  pendingAction: PendingActionView | null
}

export interface HistoryItemView {
  matchId: number
  roomCode: string
  startTime: string | null
  endTime: string | null
  bankerWinCount: number
  idleWinCount: number
  drawCount: number
  maxRate: number
}

export interface HistoryDetailView {
  latestState: GameStateView
  roundSettles: RoundSettleView[]
  globalSettle: GlobalSettleView | null
}
