<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PlayingCard from '../components/PlayingCard.vue'
import type { PendingActionView, PlayerCardsView } from '../types'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'
import { useGameStore } from '../stores/game'
import { useGameSocket } from '../useGameSocket'

const BIG_JOKER = '大王'
const SMALL_JOKER = '小王'
const WHITE_CARD = '白牌'
const SPADE = '黑桃'
const HEART = '红桃'
const CLUB = '梅花'
const DIAMOND = '方块'
const JOKERS = [BIG_JOKER, SMALL_JOKER, WHITE_CARD]
const HIDDEN_HAND = ['?', '?', '?', '?']

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const game = useGameStore()

auth.hydrate()

const roomId = Number(route.params.roomId)
const group1 = ref<string[]>([])
const group2 = ref<string[]>([])
const loading = ref(false)
const selectedPendingIdleUserId = ref<number | null>(null)
const destroyingRoom = ref(false)
const roomDestroyedHandled = ref(false)

const state = computed(() => game.state)
const room = computed(() => state.value?.room)
const players = computed(() => state.value?.players ?? [])
const pendingActions = computed(() => state.value?.pendingActions ?? [])
const currentUserId = computed(() => auth.user?.id ?? 0)
const mySeat = computed(() => players.value.find((item) => item.userId === currentUserId.value) ?? null)
const globalSettle = computed(() => state.value?.globalSettle ?? null)

const isHouseOwner = computed(() => room.value?.houseOwnerId === currentUserId.value)
const isPlayerBankerRoom = computed(() => room.value?.bankerType === 1)
const isFinished = computed(() => state.value?.matchStatus === 4)
const hasUnfinishedMatch = computed(() => Boolean(state.value?.matchId && state.value?.matchStatus !== 4))
const isFirstMatchWaiting = computed(() => !state.value?.matchId)
const showStartPanel = computed(() => isHouseOwner.value && isPlayerBankerRoom.value && isFirstMatchWaiting.value)
const showAiAutoStartPanel = computed(() => isHouseOwner.value && !isPlayerBankerRoom.value && isFirstMatchWaiting.value)
const hasMinimumIdle = computed(() => (room.value?.currentIdle ?? 0) >= 1)
const aiBankerReady = computed(() => (room.value?.currentIdle ?? 0) >= (room.value?.maxIdle ?? 0))
const canStartMatch = computed(() => showStartPanel.value && hasMinimumIdle.value)

const showReadyPanel = computed(() => isFinished.value && Boolean(mySeat.value))
const readyCount = computed(() => players.value.filter((item) => item.readyNextRound).length)
const totalPlayerCount = computed(() => players.value.length)
const myReady = computed(() => mySeat.value?.readyNextRound ?? false)

const isBankerSeat = computed(() => mySeat.value?.userRole === 1)
const isIdleSeat = computed(() => mySeat.value?.userRole === 2)
const bankerPendingActions = computed(() => pendingActions.value.filter((item) => item.bankerUserId === currentUserId.value))
const myPendingAction = computed(() => pendingActions.value.find((item) => item.idleUserId === currentUserId.value) ?? null)
const selectedPendingAction = computed(() => {
  if (!isBankerSeat.value) return null
  return bankerPendingActions.value.find((item) => item.idleUserId === selectedPendingIdleUserId.value) ?? bankerPendingActions.value[0] ?? null
})
const activePendingAction = computed(() => (isBankerSeat.value ? selectedPendingAction.value : myPendingAction.value))
const mySettled = computed(() => state.value?.roundSettles.some((item) => item.idleUserId === currentUserId.value) ?? false)
const actionLocked = computed(() => Boolean(myPendingAction.value))
const waitingBankerGroup = computed(() => activePendingAction.value?.stage === 'WAIT_BANKER_GROUP')
const reportedFishStrongMode = computed(() => Boolean(mySeat.value?.reportedFish))
const shouldMaskBankerBeforeIdleDecision = computed(() => Boolean(
  isIdleSeat.value
    && myPendingAction.value?.stage === 'WAIT_IDLE_DECISION'
    && myPendingAction.value?.idleUserId === currentUserId.value,
))

const canEditGrouping = computed(() => {
  if (!mySeat.value || isFinished.value) return false
  if (isBankerSeat.value) {
    return !state.value?.bankerGroupLocked
  }
  return !mySettled.value && !myPendingAction.value
})

const canReportFish = computed(() => Boolean(
  mySeat.value
    && mySeat.value.grouped
    && !mySeat.value.reportedFish
    && !isFinished.value
    && (isBankerSeat.value || (!mySettled.value && !actionLocked.value)),
))

const canAttackNow = computed(() => Boolean(
  mySeat.value
    && isIdleSeat.value
    && mySeat.value.grouped
    && !mySettled.value
    && !actionLocked.value,
))

const canOpenHidden = computed(() => Boolean(mySeat.value?.grouped))
const myHandJokerCount = computed(() => (mySeat.value?.handCards ?? []).filter((card) => JOKERS.includes(card)).length)
const uniqueNormalRank = computed(() => {
  const normalCard = (mySeat.value?.handCards ?? []).find((card) => !JOKERS.includes(card))
  return normalCard ? stripSuit(normalCard) : ''
})

const roomModeText = computed(() => (isPlayerBankerRoom.value ? '玩家坐庄' : 'AI 庄家'))
const roundLabel = computed(() => (state.value?.currentIdleSort ? `闲家 ${state.value.currentIdleSort}` : '等待轮次'))
const tableHeadline = computed(() => {
  if (activePendingAction.value) return '当前有新的请求待处理'
  if (isFinished.value) return '本局已经结束，等待下一局准备'
  if (!state.value?.matchId) return '牌桌已就绪，等待开局'
  return '牌局进行中'
})
const tableDescription = computed(() => {
  if (activePendingAction.value) return pendingMessageText()
  if (isFinished.value) return '所有玩家准备后会自动进入下一局，AI 玩家默认同意。'
  if (!state.value?.matchId) return isPlayerBankerRoom.value ? '玩家庄家可以手动开启首局。' : 'AI 庄家满员后会自动开始。'
  return '新的请求、分组结果和结算都会同步推送到当前房间。'
})

const startMatchHint = computed(() => {
  if (!showStartPanel.value || canStartMatch.value) return ''
  return '玩家坐庄时，至少需要 1 名闲家才能开始首局。'
})

const aiAutoStartHint = computed(() => {
  if (!showAiAutoStartPanel.value || !room.value) return ''
  if (aiBankerReady.value) {
    return 'AI 庄家房间已经满员，系统正在自动开始首局。'
  }
  return `AI 庄家模式会在闲家满员后自动开始，当前 ${room.value.currentIdle}/${room.value.maxIdle}。`
})

const readyHint = computed(() => {
  if (!showReadyPanel.value) return ''
  return `已准备 ${readyCount.value}/${totalPlayerCount.value}，所有真人玩家点“开始下一局”后会自动开局。`
})

watch(
  () => mySeat.value,
  (seat) => {
    if (!seat) return
    group1.value = [...seat.group1]
    group2.value = [...seat.group2]
  },
  { immediate: true },
)

watch(
  () => bankerPendingActions.value,
  (actions) => {
    if (!actions.length) {
      selectedPendingIdleUserId.value = null
      return
    }
    if (!actions.some((item) => item.idleUserId === selectedPendingIdleUserId.value)) {
      selectedPendingIdleUserId.value = actions[0].idleUserId
    }
  },
  { immediate: true },
)

async function refreshState() {
  if (roomDestroyedHandled.value) return
  try {
    const nextState = await game.fetchState(roomId)
    if (!nextState.players.some((item) => item.userId === currentUserId.value)) {
      leaveDestroyedRoom('你已不在该房间，已返回大厅。')
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '同步房间状态失败')
  }
}

function leaveDestroyedRoom(message: string) {
  if (roomDestroyedHandled.value) return
  roomDestroyedHandled.value = true
  game.clear()
  ElMessage.warning(message)
  router.replace('/lobby')
}

useGameSocket(roomId, refreshState, (event) => {
  if (event.type === 'room_destroyed') {
    if (!destroyingRoom.value || !isHouseOwner.value) {
      leaveDestroyedRoom(event.message || '房主已解散房间。')
    }
    return false
  }
  return true
})

onMounted(refreshState)

function stripSuit(card: string) {
  return card.replace(SPADE, '').replace(HEART, '').replace(CLUB, '').replace(DIAMOND, '')
}

function toggleCard(card: string) {
  if (!canEditGrouping.value) return
  if (group1.value.includes(card)) {
    group1.value = group1.value.filter((item) => item !== card)
    return
  }
  if (group2.value.includes(card)) {
    group2.value = group2.value.filter((item) => item !== card)
    return
  }
  if (group1.value.length < 2) {
    group1.value = [...group1.value, card]
    return
  }
  if (group2.value.length < 2) {
    group2.value = [...group2.value, card]
  }
}

function groupHasJoker(cards: string[]) {
  return cards.some((card) => JOKERS.includes(card))
}

function groupAutoRank(cards: string[]) {
  if (myHandJokerCount.value === 3 && uniqueNormalRank.value) {
    return uniqueNormalRank.value
  }
  const pairedCard = cards.find((card) => !JOKERS.includes(card))
  return pairedCard ? stripSuit(pairedCard) : ''
}

function resetGrouping() {
  group1.value = []
  group2.value = []
}

async function submitGrouping() {
  if (!mySeat.value) return
  if (group1.value.length !== 2 || group2.value.length !== 2) {
    ElMessage.warning('请先把 4 张牌分成两组')
    return
  }
  if ((groupHasJoker(group1.value) && !groupAutoRank(group1.value))
    || (groupHasJoker(group2.value) && !groupAutoRank(group2.value))) {
    ElMessage.warning('变牌组必须有可跟的普通牌；只有三张变牌时才会统一跟唯一普通牌')
    return
  }
  loading.value = true
  try {
    await game.submitGroup({
      roomId,
      userId: currentUserId.value,
      group1: group1.value,
      group2: group2.value,
      changeCard1: null,
      changeCard2: null,
    })
    ElMessage.success('分组提交成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '分组提交失败')
  } finally {
    loading.value = false
  }
}

async function reportFish(fishType: number) {
  loading.value = true
  try {
    await game.reportFish({ roomId, userId: currentUserId.value, fishType })
    ElMessage.success('报水鱼成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '报水鱼失败')
  } finally {
    loading.value = false
  }
}

async function startMatch() {
  if (!canStartMatch.value) {
    ElMessage.warning(startMatchHint.value || '当前还不满足开局条件')
    return
  }
  loading.value = true
  try {
    await game.deal(roomId)
    ElMessage.success('首局已开始')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '开始失败')
  } finally {
    loading.value = false
  }
}

async function readyNextRound() {
  loading.value = true
  try {
    await game.readyNextRound({ roomId, userId: currentUserId.value })
    ElMessage.success(myReady.value ? '你已经确认过下一局' : '已确认开始下一局')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '准备下一局失败')
  } finally {
    loading.value = false
  }
}

async function attack(attackType: number) {
  loading.value = true
  try {
    await game.attack({ roomId, userId: currentUserId.value, attackType })
    ElMessage.success('进攻请求已提交')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '进攻提交失败')
  } finally {
    loading.value = false
  }
}

async function respond(bankerResponse: number) {
  if (!activePendingAction.value) {
    ElMessage.warning('请先选择一位待处理的闲家')
    return
  }
  loading.value = true
  try {
    await game.respond({
      roomId,
      userId: currentUserId.value,
      idleUserId: activePendingAction.value.idleUserId,
      bankerResponse,
    })
    ElMessage.success('庄家应对已提交')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '庄家应对提交失败')
  } finally {
    loading.value = false
  }
}

async function decide(acceptCallKill: boolean) {
  loading.value = true
  try {
    await game.decide({ roomId, userId: currentUserId.value, acceptCallKill })
    ElMessage.success('闲家决定已提交')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '闲家决定提交失败')
  } finally {
    loading.value = false
  }
}

async function processPending() {
  if (!activePendingAction.value) {
    ElMessage.warning('请先选择一位待处理的闲家')
    return
  }
  loading.value = true
  try {
    await game.processPending({
      roomId,
      userId: currentUserId.value,
      idleUserId: activePendingAction.value.idleUserId,
    })
    ElMessage.success('当前闲家请求已处理')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '处理闲家请求失败')
  } finally {
    loading.value = false
  }
}

function selectPending(idleUserId: number) {
  selectedPendingIdleUserId.value = idleUserId
}

async function destroyRoom() {
  if (!room.value) return
  destroyingRoom.value = true
  try {
    await api.destroyRoom({ roomId: room.value.roomId, userId: currentUserId.value })
    game.clear()
    ElMessage.success('房间已解散')
    router.replace('/lobby')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '解散房间失败')
  } finally {
    destroyingRoom.value = false
  }
}

function goHistory() {
  router.push(`/history?roomId=${roomId}`)
}

function goLobby() {
  if (hasUnfinishedMatch.value) {
    ElMessage.warning('本局尚未结束，不能返回大厅')
    return
  }
  router.push('/lobby')
}

function matchStatusText(value: number | null) {
  switch (value) {
    case 0:
      return '未发牌'
    case 1:
      return '已发牌'
    case 2:
      return '分组中'
    case 3:
      return '比牌中'
    case 4:
      return '已结束'
    default:
      return '等待中'
  }
}

function roleText(userRole: number, idleSort: number | null) {
  return userRole === 1 ? '庄家' : `闲家 ${idleSort ?? '-'}`
}

function fishTypeText(value: number | null) {
  switch (value) {
    case 1:
      return '普通水鱼'
    case 2:
      return '至尊水鱼'
    default:
      return '已报水鱼'
  }
}

function attackTypeText(value: number | string | null | undefined) {
  switch (value) {
    case 1:
    case 'STRONG':
      return '强攻'
    case 2:
    case 'ASK_RUN':
      return '求走'
    case 3:
    case 'HIDDEN':
      return '暗攻'
    case 4:
    case 'FISH':
      return '水鱼强攻'
    default:
      return '-'
  }
}

function resultText(value: number | null) {
  switch (value) {
    case 1:
      return '庄胜'
    case 2:
      return '闲胜'
    case 3:
      return '平局'
    default:
      return '-'
  }
}

function rateText(value: number | null | undefined) {
  return value && value > 0 ? `倍率 x${value}` : '无赔付'
}

function maxRateText(value: number | null | undefined) {
  return value && value > 0 ? `x${value}` : '-'
}

function pendingMessageText(action: PendingActionView | null = activePendingAction.value) {
  if (!action) return ''
  if (action.stage === 'WAIT_BANKER_RESPONSE') {
    return `等待庄家应对：${attackTypeText(action.attackType)}`
  }
  if (action.stage === 'WAIT_BANKER_GROUP') {
    return `等待庄家先分组后继续：${attackTypeText(action.attackType)}`
  }
  return `等待闲家做最终决定：${attackTypeText(action.attackType)}`
}

function changeLabel(changeCard: string | null) {
  if (!changeCard) return '未变牌'
  const [, rank = ''] = changeCard.split(':')
  return `变牌为 ${rank}`
}

function shouldMaskPlayerCards(player: PlayerCardsView) {
  return Boolean(
    shouldMaskBankerBeforeIdleDecision.value
      && player.userId === room.value?.bankerUserId
      && player.userRole === 1,
  )
}

function isPlayerFaceDown(player: PlayerCardsView) {
  return player.faceDown || shouldMaskPlayerCards(player)
}

function visibleHand(player: PlayerCardsView) {
  return isPlayerFaceDown(player) ? HIDDEN_HAND : player.handCards
}

function shouldShowGroups(player: PlayerCardsView) {
  return !isPlayerFaceDown(player) && (player.group1.length > 0 || player.group2.length > 0)
}

function playerPendingAction(userId: number) {
  return pendingActions.value.find((item) => item.idleUserId === userId) ?? null
}

function playerStatusText(player: PlayerCardsView) {
  if (isFinished.value && player.readyNextRound) return '已确认下一局'
  if (player.reportedFish) return `${fishTypeText(player.fishType)}，当前公开牌面`
  if (player.faceDown) return '当前仍为暗牌状态'
  if (player.grouped) return '已完成分组'
  return player.userRole === 1 ? '庄家待命中' : '等待分组或发起请求'
}

function seatStatusText(player: PlayerCardsView) {
  if (shouldMaskPlayerCards(player)) return '等待闲家做出最终决定后再公开庄家牌面'
  return playerStatusText(player)
}

function pendingSeatText(player: PlayerCardsView) {
  const pending = playerPendingAction(player.userId)
  if (!pending) return ''
  return `${attackTypeText(pending.attackType)}待处理`
}
</script>

<template>
  <main v-if="state" class="room-page">
    <section class="page-header room-header">
      <div>
        <p class="section-kicker">房间 {{ room?.roomCode }}</p>
        <h1>水鱼牌桌</h1>
        <p class="subtle-text">
          {{ roomModeText }} · 闲家 {{ room?.currentIdle ?? 0 }}/{{ room?.maxIdle ?? 0 }} ·
          当前轮次 {{ roundLabel }} · 状态 {{ matchStatusText(state.matchStatus) }}
        </p>
      </div>
      <div class="header-actions">
        <el-button plain @click="goHistory">历史记录</el-button>
        <el-button plain @click="goLobby">返回大厅</el-button>
        <el-button v-if="isHouseOwner" type="danger" plain @click="destroyRoom">解散房间</el-button>
      </div>
    </section>

    <section class="room-grid">
      <div class="table-panel premium-panel">
        <div class="table-banner">
          <div>
            <p class="section-kicker">当前桌况</p>
            <h2>{{ tableHeadline }}</h2>
            <p class="subtle-text">{{ tableDescription }}</p>
          </div>
          <div class="table-banner__meta">
            <span>{{ roomModeText }}</span>
            <span>{{ matchStatusText(state.matchStatus) }}</span>
            <span>本轮 {{ roundLabel }}</span>
          </div>
        </div>

        <div class="players-grid">
          <article
            v-for="player in players"
            :key="player.userId"
            class="player-seat"
            :class="{
              banker: player.userRole === 1,
              active: player.idleSort === state.currentIdleSort,
              pending: Boolean(playerPendingAction(player.userId)),
            }"
          >
            <header class="seat-header">
              <div class="seat-identity">
                <p class="seat-role">{{ roleText(player.userRole, player.idleSort) }}</p>
                <h3>{{ player.nickname }}</h3>
                <p class="seat-status">{{ seatStatusText(player) }}</p>
              </div>

              <div class="seat-tags">
                <el-tag v-if="player.grouped && !player.faceDown" type="success">已分组</el-tag>
                <el-tag v-if="player.reportedFish && !player.faceDown" type="warning">{{ fishTypeText(player.fishType) }}</el-tag>
                <el-tag v-if="player.readyNextRound && isFinished" type="success">已准备下一局</el-tag>
                <el-tag v-if="player.faceDown" type="info">暗牌</el-tag>
                <el-tag v-if="pendingSeatText(player)" type="danger">{{ pendingSeatText(player) }}</el-tag>
              </div>
            </header>

            <div v-if="player.handCards.length || isPlayerFaceDown(player)" class="hand-row">
              <PlayingCard
                v-for="(card, index) in visibleHand(player)"
                :key="`${player.userId}-${index}`"
                :card="isPlayerFaceDown(player) ? null : card"
                :face-down="isPlayerFaceDown(player)"
                size="table"
              />
            </div>

            <div v-if="shouldShowGroups(player)" class="group-row">
              <div class="group-box">
                <small>第一组</small>
                <div class="group-cards">
                  <PlayingCard
                    v-for="card in player.group1"
                    :key="`group-a-${player.userId}-${card}`"
                    :card="card"
                    size="mini"
                  />
                </div>
                <span class="group-note">{{ changeLabel(player.changeCard1) }}</span>
              </div>

              <div class="group-box">
                <small>第二组</small>
                <div class="group-cards">
                  <PlayingCard
                    v-for="card in player.group2"
                    :key="`group-b-${player.userId}-${card}`"
                    :card="card"
                    size="mini"
                  />
                </div>
                <span class="group-note">{{ changeLabel(player.changeCard2) }}</span>
              </div>
            </div>
          </article>
        </div>
      </div>

      <aside class="action-panel">
        <el-card class="surface-card">
          <template #header>
            <div class="card-header">
              <div>
                <p class="section-kicker">我的席位</p>
                <h3>{{ mySeat?.nickname ?? '未入座' }}</h3>
              </div>
              <span v-if="mySeat" class="surface-tag">{{ roleText(mySeat.userRole, mySeat.idleSort) }}</span>
            </div>
          </template>

          <template v-if="mySeat">
            <div class="action-block action-block--intro">
              <p class="subtle-text">
                {{
                  isIdleSeat
                    ? (
                      reportedFishStrongMode
                        ? '你已经报水鱼，当前牌面会公开给庄家和闲家，并且后续只能发起强攻。'
                        : (
                          canAttackNow
                            ? '你已经满足进攻条件，可以随时发起这轮操作。'
                            : (mySettled ? '你的本轮对局已经结算完成。' : '先完成分组，或者等待自己当前请求处理结束。')
                        )
                    )
                    : (
                      mySeat.reportedFish
                        ? '你已经摆出水鱼/至尊水鱼，这手会按报鱼牌型参与后续比牌，牌面也会对全桌公开。'
                        : (state.bankerGroupLocked
                          ? '庄家分组已经锁定，现在可以根据闲家请求继续处理，也可以在满足条件时摆水鱼。'
                          : '庄家可以先观察闲家的请求，需要开牌或比牌时再决定是否分组；完成分组后也可以摆水鱼。')
                    )
                }}
              </p>
            </div>

            <div v-if="showStartPanel" class="action-block">
              <h4>开始首局</h4>
              <p class="subtle-text">玩家坐庄时，由房主手动点击开始。只要至少有一位闲家，就可以开桌。</p>
              <el-button class="primary-btn" :loading="loading" :disabled="!canStartMatch" type="primary" @click="startMatch">
                开始首局
              </el-button>
              <p v-if="startMatchHint" class="subtle-text">{{ startMatchHint }}</p>
            </div>

            <div v-if="showAiAutoStartPanel" class="action-block">
              <h4>自动开局</h4>
              <p class="subtle-text">{{ aiAutoStartHint }}</p>
            </div>

            <div v-if="showReadyPanel" class="action-block">
              <h4>下一局准备</h4>
              <p class="subtle-text">{{ readyHint }}</p>
              <el-button class="primary-btn" :loading="loading" :disabled="myReady" type="primary" @click="readyNextRound">
                {{ myReady ? '已确认开始下一局' : '开始下一局' }}
              </el-button>
            </div>

            <div v-if="!isFinished && isBankerSeat && bankerPendingActions.length" class="action-block">
              <h4>待处理闲家</h4>
              <p class="subtle-text">庄家可以自由切换目标闲家，查看最新请求并决定先处理哪一位。</p>
              <div class="pending-switches">
                <button
                  v-for="item in bankerPendingActions"
                  :key="item.idleUserId"
                  type="button"
                  class="pending-switch"
                  :class="{ active: activePendingAction?.idleUserId === item.idleUserId }"
                  @click="selectPending(item.idleUserId)"
                >
                  <strong>{{ item.idleNickname || `闲家 ${item.idleSort ?? '-'}` }}</strong>
                  <span>{{ attackTypeText(item.attackType) }}</span>
                </button>
              </div>
            </div>

            <div v-if="mySeat.handCards.length && !isFinished && canEditGrouping" class="action-block">
              <h4>分组区</h4>
              <p class="subtle-text">
                {{
                  isBankerSeat
                    ? '庄家可以先不分组；一旦提交分组，这局就不能再修改。'
                    : (myHandJokerCount === 3
                      ? '当前拿到三张变牌，它们会统一变成手里唯一那张普通牌。'
                      : '请选择 2 张牌作为第一组，另外 2 张牌作为第二组。')
                }}
              </p>

              <div class="picker-row">
                <button
                  v-for="card in mySeat.handCards"
                  :key="card"
                  type="button"
                  class="picker-card-shell"
                  :class="{ selected: group1.includes(card) || group2.includes(card) }"
                  @click="toggleCard(card)"
                >
                  <PlayingCard
                    :card="card"
                    size="picker"
                    :selected="group1.includes(card) || group2.includes(card)"
                  />
                </button>
              </div>

              <div class="group-editor">
                <div class="group-box editable">
                  <strong>第一组</strong>
                  <div class="group-cards group-cards--editable">
                    <PlayingCard v-for="card in group1" :key="`edit-a-${card}`" :card="card" size="table" />
                    <span v-if="!group1.length" class="group-empty">点击上方牌面选入这一组</span>
                  </div>
                  <div v-if="groupHasJoker(group1)" class="auto-change-note">
                    自动变牌：{{ groupAutoRank(group1) || '等待同组另一张牌' }}
                  </div>
                </div>

                <div class="group-box editable">
                  <strong>第二组</strong>
                  <div class="group-cards group-cards--editable">
                    <PlayingCard v-for="card in group2" :key="`edit-b-${card}`" :card="card" size="table" />
                    <span v-if="!group2.length" class="group-empty">点击上方牌面选入这一组</span>
                  </div>
                  <div v-if="groupHasJoker(group2)" class="auto-change-note">
                    自动变牌：{{ groupAutoRank(group2) || '等待同组另一张牌' }}
                  </div>
                </div>
              </div>

              <div class="button-row">
                <el-button @click="resetGrouping">重置</el-button>
                <el-button class="primary-btn" :loading="loading" :disabled="!canEditGrouping" type="primary" @click="submitGrouping">
                  提交分组
                </el-button>
              </div>
            </div>

            <div v-if="!isFinished && mySeat.grouped" class="action-block">
              <h4>报水鱼</h4>
              <p class="subtle-text">
                {{
                  mySeat.reportedFish
                    ? (isIdleSeat
                      ? '已报水鱼，当前牌面所有玩家可见，后续只能强攻。'
                      : '庄家已摆水鱼，当前牌面所有玩家可见，后续会按报鱼牌型参与比牌。')
                    : (isIdleSeat
                      ? '闲家分组后可以报水鱼/至尊水鱼；一旦报鱼，后续只能发起强攻。'
                      : '庄家分组后也可以摆水鱼/至尊水鱼，后续比牌会按对应鱼型结算。')
                }}
              </p>
              <div class="button-row">
                <el-button :disabled="!canReportFish" @click="reportFish(1)">普通水鱼</el-button>
                <el-button :disabled="!canReportFish" @click="reportFish(2)">至尊水鱼</el-button>
              </div>
            </div>

            <div v-if="!isFinished && isIdleSeat" class="action-block">
              <h4>进攻操作</h4>
              <p class="subtle-text">
                {{
                  reportedFishStrongMode
                    ? '报水鱼后牌面公开，并且这轮只能发起强攻。'
                    : '闲家分组完成后即可发起进攻，不再受固定出手顺序限制。'
                }}
              </p>
              <div class="button-grid">
                <el-button :disabled="!canAttackNow" type="primary" @click="attack(1)">
                  {{ reportedFishStrongMode ? '水鱼强攻' : '强攻' }}
                </el-button>
                <el-button :disabled="!canAttackNow || reportedFishStrongMode" @click="attack(2)">求走</el-button>
                <el-button :disabled="!canAttackNow || reportedFishStrongMode" @click="attack(3)">暗攻</el-button>
              </div>
            </div>

            <div
              v-if="!isFinished && activePendingAction?.stage === 'WAIT_BANKER_RESPONSE' && activePendingAction.bankerUserId === currentUserId"
              class="action-block"
            >
              <h4>庄家应对</h4>
              <p class="subtle-text">当前目标：{{ activePendingAction.idleNickname || `闲家 ${activePendingAction.idleSort ?? '-'}` }}</p>
              <p v-if="activePendingAction.attackType === 'ASK_RUN'" class="subtle-text">
                求走时，庄家可以先看该闲家的牌，再决定给走还是叫杀。
              </p>
              <p v-else-if="activePendingAction.attackType === 'HIDDEN'" class="subtle-text">
                暗攻时，如果你要开牌，就必须先完成自己的分组；如果不开牌，则不需要分组。
              </p>
              <div class="button-row">
                <template v-if="activePendingAction.attackType === 'ASK_RUN'">
                  <el-button @click="respond(1)">给走</el-button>
                  <el-button type="danger" @click="respond(2)">叫杀</el-button>
                </template>
                <template v-else>
                  <el-button :disabled="!canOpenHidden" @click="respond(3)">开牌</el-button>
                  <el-button type="danger" @click="respond(4)">不开牌</el-button>
                </template>
              </div>
            </div>

            <div
              v-if="!isFinished && myPendingAction?.stage === 'WAIT_IDLE_DECISION' && myPendingAction.idleUserId === currentUserId"
              class="action-block"
            >
              <h4>闲家最终决定</h4>
              <p class="subtle-text">庄家已经做出回应，现在由你决定“服了”还是继续比牌。</p>
              <div class="button-row">
                <el-button @click="decide(true)">服了</el-button>
                <el-button type="primary" @click="decide(false)">不服，继续比</el-button>
              </div>
            </div>

            <div
              v-if="!isFinished && waitingBankerGroup && activePendingAction?.bankerUserId === currentUserId && activePendingAction?.attackType === 'ASK_RUN'"
              class="action-block"
            >
              <h4>等待庄家分组后继续比牌</h4>
              <p class="subtle-text">
                当前目标：{{ activePendingAction.idleNickname || `闲家 ${activePendingAction.idleSort ?? '-'}` }}。
                该闲家已经选择“不服，继续比”。请庄家先完成分组，提交后点击下方按钮，
                系统会直接使用庄家当前分组与该闲家比牌，不会再要求重新分组。
              </p>
              <el-button class="primary-btn" :disabled="!state.bankerGroupLocked" :loading="loading" type="primary" @click="processPending">
                按当前分组继续比牌
              </el-button>
            </div>

            <div
              v-if="!isFinished && waitingBankerGroup && activePendingAction?.bankerUserId === currentUserId && activePendingAction?.attackType !== 'ASK_RUN'"
              class="action-block"
            >
              <h4>等待庄家分组</h4>
              <p class="subtle-text">
                当前目标：{{ activePendingAction.idleNickname || `闲家 ${activePendingAction.idleSort ?? '-'}` }}。
                这个请求需要庄家先完成分组，然后再继续处理。
              </p>
              <el-button class="primary-btn" :disabled="!state.bankerGroupLocked" :loading="loading" type="primary" @click="processPending">
                继续处理当前闲家请求
              </el-button>
            </div>
          </template>

          <p v-else class="subtle-text">当前账号还没有进入这个房间。</p>
        </el-card>

        <el-card v-if="globalSettle" class="surface-card">
          <template #header>
            <div class="card-header">
              <div>
                <p class="section-kicker">全局结算</p>
                <h3>本房间累计结果</h3>
              </div>
            </div>
          </template>

          <div class="history-summary history-summary--compact">
            <article class="metric-card">
              <small>庄家胜</small>
              <strong>{{ globalSettle.bankerWinCount }}</strong>
            </article>
            <article class="metric-card">
              <small>闲家胜</small>
              <strong>{{ globalSettle.idleWinCount }}</strong>
            </article>
            <article class="metric-card">
              <small>平局</small>
              <strong>{{ globalSettle.drawCount }}</strong>
            </article>
            <article class="metric-card">
              <small>最高倍率</small>
              <strong>{{ maxRateText(globalSettle.maxRate) }}</strong>
            </article>
          </div>
        </el-card>

        <el-card class="surface-card">
          <template #header>
            <div class="card-header">
              <div>
                <p class="section-kicker">本局结算</p>
                <h3>已公开的回合结果</h3>
              </div>
            </div>
          </template>

          <div class="settle-list">
            <article
              v-for="item in state.roundSettles"
              :key="`${item.idleUserId}-${item.settleTime}`"
              class="settle-item"
            >
              <div class="settle-item__head">
                <strong>{{ item.idleNickname }}</strong>
                <span>{{ resultText(item.result) }}</span>
              </div>
              <span>{{ attackTypeText(item.attackType) }} · {{ rateText(item.rate) }}</span>
              <small>{{ item.settleTime }}</small>
            </article>
            <p v-if="!state.roundSettles.length" class="subtle-text">当前还没有你可见的比牌结果。</p>
          </div>
        </el-card>
      </aside>
    </section>
  </main>
</template>
