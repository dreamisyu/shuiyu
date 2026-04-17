<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import type { GameStateView, HistoryDetailView, HistoryItemView } from '../types'

const route = useRoute()
const router = useRouter()
const rows = ref<HistoryItemView[]>([])
const detail = ref<HistoryDetailView | null>(null)
const activeRoomState = ref<GameStateView | null>(null)
const loading = ref(false)
const detailVisible = ref(false)

const activeRoomId = computed(() => {
  const value = route.query.roomId
  const roomId = Number(Array.isArray(value) ? value[0] : value)
  return Number.isFinite(roomId) && roomId > 0 ? roomId : null
})

const canReturnToRoom = computed(() => Boolean(activeRoomId.value))
const hasUnfinishedMatch = computed(() => Boolean(activeRoomState.value?.matchId && activeRoomState.value.matchStatus !== 4))
const summary = computed(() => rows.value.reduce((result, item) => ({
  matchCount: result.matchCount + 1,
  bankerWinCount: result.bankerWinCount + item.bankerWinCount,
  idleWinCount: result.idleWinCount + item.idleWinCount,
  maxRate: Math.max(result.maxRate, item.maxRate),
}), {
  matchCount: 0,
  bankerWinCount: 0,
  idleWinCount: 0,
  maxRate: 0,
}))

async function loadHistory() {
  loading.value = true
  try {
    rows.value = await api.fetchHistory()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载历史记录失败')
  } finally {
    loading.value = false
  }
}

async function loadActiveRoomState() {
  if (!activeRoomId.value) {
    activeRoomState.value = null
    return
  }
  try {
    activeRoomState.value = await api.fetchState(activeRoomId.value)
  } catch {
    activeRoomState.value = null
  }
}

async function openDetail(matchId: number) {
  try {
    detail.value = await api.fetchHistoryDetail(matchId)
    detailVisible.value = true
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载对局详情失败')
  }
}

function goBackToRoom() {
  if (!activeRoomId.value) return
  router.push(`/room/${activeRoomId.value}`)
}

function goLobby() {
  if (hasUnfinishedMatch.value) {
    ElMessage.warning('当前牌局还没结束，请先返回牌局继续游戏')
    return
  }
  router.push('/lobby')
}

function attackTypeText(value: number | null) {
  switch (value) {
    case 1:
      return '强攻'
    case 2:
      return '求走'
    case 3:
      return '暗攻'
    case 4:
      return '水鱼强攻'
    default:
      return '-'
  }
}

function bankerResponseText(value: number | null) {
  switch (value) {
    case 1:
      return '给走'
    case 2:
      return '叫杀'
    case 3:
      return '开牌'
    case 4:
      return '不开牌'
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
  return value && value > 0 ? `x${value}` : '无赔付'
}

function maxRateText(value: number | null | undefined) {
  return value && value > 0 ? `x${value}` : '-'
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
      return '-'
  }
}

onMounted(async () => {
  await Promise.all([loadHistory(), loadActiveRoomState()])
})
</script>

<template>
  <main class="history-page">
    <section class="history-hero premium-panel">
      <div class="history-hero__copy">
        <p class="section-kicker">牌局档案</p>
        <h1>历史对局与回放记录</h1>
        <p class="hero-copy">
          所有已经完成的牌局都会沉淀在这里。你可以查看结算结果、追溯每一轮的攻防，也可以直接返回当前未完成的房间继续打。
        </p>
        <p v-if="hasUnfinishedMatch" class="hero-note">
          当前房间 {{ activeRoomState?.room.roomCode }} 仍在进行中，请优先返回牌局继续。
        </p>
      </div>

      <div class="history-summary">
        <article class="metric-card">
          <small>已记录对局</small>
          <strong>{{ summary.matchCount }}</strong>
        </article>
        <article class="metric-card">
          <small>庄家胜场</small>
          <strong>{{ summary.bankerWinCount }}</strong>
        </article>
        <article class="metric-card">
          <small>闲家胜场</small>
          <strong>{{ summary.idleWinCount }}</strong>
        </article>
        <article class="metric-card">
          <small>最高倍率</small>
          <strong>{{ maxRateText(summary.maxRate) }}</strong>
        </article>
      </div>
    </section>

    <section class="page-header page-header--compact">
      <div>
        <p class="section-kicker">操作</p>
        <h2>查看、回放或回到当前牌桌</h2>
      </div>
      <div class="header-actions">
        <el-button v-if="canReturnToRoom" type="primary" @click="goBackToRoom">返回牌局</el-button>
        <el-button plain @click="goLobby">返回大厅</el-button>
      </div>
    </section>

    <el-card class="surface-card table-card">
      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="matchId" label="对局 ID" width="110" />
        <el-table-column prop="roomCode" label="房间码" width="140" />
        <el-table-column prop="startTime" label="开始时间" min-width="180" />
        <el-table-column prop="endTime" label="结束时间" min-width="180" />
        <el-table-column prop="bankerWinCount" label="庄家胜" width="100" />
        <el-table-column prop="idleWinCount" label="闲家胜" width="100" />
        <el-table-column prop="drawCount" label="平局" width="90" />
        <el-table-column label="最高倍率" width="110">
          <template #default="{ row }">
            {{ maxRateText(row.maxRate) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.matchId)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="detailVisible" title="对局详情" width="960px">
      <template v-if="detail">
        <p class="subtle-text">
          房间 {{ detail.latestState.room.roomCode }} |
          最终状态：{{ matchStatusText(detail.latestState.matchStatus) }}
        </p>

        <div class="history-summary history-summary--dialog">
          <article class="metric-card">
            <small>庄家胜</small>
            <strong>{{ detail.globalSettle?.bankerWinCount ?? 0 }}</strong>
          </article>
          <article class="metric-card">
            <small>闲家胜</small>
            <strong>{{ detail.globalSettle?.idleWinCount ?? 0 }}</strong>
          </article>
          <article class="metric-card">
            <small>平局</small>
            <strong>{{ detail.globalSettle?.drawCount ?? 0 }}</strong>
          </article>
          <article class="metric-card">
            <small>最高倍率</small>
            <strong>{{ maxRateText(detail.globalSettle?.maxRate ?? 0) }}</strong>
          </article>
        </div>

        <el-table :data="detail.roundSettles">
          <el-table-column prop="idleNickname" label="闲家" />
          <el-table-column prop="idleSort" label="座位" width="80" />
          <el-table-column label="进攻类型" width="110">
            <template #default="{ row }">
              {{ attackTypeText(row.attackType) }}
            </template>
          </el-table-column>
          <el-table-column label="庄家应对" width="130">
            <template #default="{ row }">
              {{ bankerResponseText(row.bankerResponse) }}
            </template>
          </el-table-column>
          <el-table-column label="结果" width="110">
            <template #default="{ row }">
              {{ resultText(row.result) }}
            </template>
          </el-table-column>
          <el-table-column label="倍率" width="100">
            <template #default="{ row }">
              {{ rateText(row.rate) }}
            </template>
          </el-table-column>
          <el-table-column prop="settleTime" label="结算时间" min-width="170" />
        </el-table>
      </template>
    </el-dialog>
  </main>
</template>
