<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PlayingCard from '../components/PlayingCard.vue'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
auth.hydrate()

const router = useRouter()
const currentUserId = computed(() => auth.user?.id ?? 0)
const showcaseCards = ['黑桃A', '方块10', '红桃Q']
const lobbyTips = ['房主可以自由选择庄家模式', 'AI 庄家满员后自动开局', '房间状态和牌局变化会实时推送']

const createForm = reactive({
  bankerType: 2,
  maxIdle: 4,
  aiIdleCount: 2,
})

const joinForm = reactive({
  roomCode: '',
})

async function createRoom() {
  const availableSeats = createForm.maxIdle - (createForm.bankerType === 2 ? 1 : 0)
  const aiIdleCount = Math.max(0, Math.min(createForm.aiIdleCount, availableSeats))
  const idleTypeList = Array.from(
    { length: availableSeats },
    (_, index) => (index < aiIdleCount ? 2 : 1),
  )

  try {
    const room = await api.createRoom({
      userId: currentUserId.value,
      bankerType: createForm.bankerType,
      maxIdle: createForm.maxIdle,
      idleTypeList,
    })
    ElMessage.success(`房间创建成功，房间码：${room.roomCode}`)
    router.push(`/room/${room.roomId}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建房间失败')
  }
}

async function joinRoom() {
  try {
    const room = await api.joinRoom({ roomCode: joinForm.roomCode.trim(), userId: currentUserId.value })
    ElMessage.success('加入房间成功')
    router.push(`/room/${room.roomId}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加入房间失败')
  }
}

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <main class="lobby-page">
    <section class="lobby-hero premium-panel">
      <div class="lobby-hero__copy">
        <p class="section-kicker">牌局大厅</p>
        <h1>欢迎回来，{{ auth.user?.nickname }}</h1>
        <p class="hero-copy">
          这里是你的牌桌控制台。可以马上创建新桌、加入朋友的房间，也可以先看看历史牌局，决定今晚从哪一局继续。
        </p>

        <div class="hero-rule-list">
          <span v-for="item in lobbyTips" :key="item">{{ item }}</span>
        </div>
      </div>

      <div class="lobby-hero__table">
        <div class="table-stage">
          <div class="table-stage__glow" />
          <div class="card-fan lobby-fan">
            <PlayingCard
              v-for="(card, index) in showcaseCards"
              :key="card"
              :card="card"
              size="hero"
              :style="{ '--fan-index': index }"
            />
          </div>
        </div>
        <div class="table-caption">
          <span>实时对局</span>
          <span>多闲家房间</span>
          <span>历史追溯</span>
        </div>
      </div>
    </section>

    <section class="page-header page-header--compact">
      <div>
        <p class="section-kicker">当前账号</p>
        <h2>{{ auth.user?.nickname }}</h2>
        <p class="subtle-text">创建或加入房间后，会直接进入当前牌桌并持续同步最新状态。</p>
      </div>
      <div class="header-actions">
        <el-button plain @click="router.push('/history')">历史记录</el-button>
        <el-button type="danger" plain @click="logout">退出登录</el-button>
      </div>
    </section>

    <section class="lobby-grid">
      <el-card class="surface-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="section-kicker">创建牌桌</p>
              <h3>新的房间设置</h3>
            </div>
            <span class="surface-tag">房主控场</span>
          </div>
        </template>

        <el-form class="stack-form" label-position="top">
          <el-form-item label="庄家模式">
            <el-radio-group v-model="createForm.bankerType">
              <el-radio-button :value="1">玩家坐庄</el-radio-button>
              <el-radio-button :value="2">AI 庄家</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="闲家人数">
            <el-slider v-model="createForm.maxIdle" :min="1" :max="10" show-input />
          </el-form-item>

          <el-form-item label="AI 闲家人数">
            <el-input-number
              v-model="createForm.aiIdleCount"
              :min="0"
              :max="Math.max(0, createForm.maxIdle - (createForm.bankerType === 2 ? 1 : 0))"
            />
          </el-form-item>

          <el-alert type="info" :closable="false">
            AI 庄家模式下，当前账号会自动占用一个闲家座位；玩家坐庄模式下，房主就是庄家。
          </el-alert>

          <el-button class="primary-btn" type="primary" @click="createRoom">
            创建并进入房间
          </el-button>
        </el-form>
      </el-card>

      <el-card class="surface-card">
        <template #header>
          <div class="card-header">
            <div>
              <p class="section-kicker">加入牌桌</p>
              <h3>输入已有房间码</h3>
            </div>
            <span class="surface-tag">快速入桌</span>
          </div>
        </template>

        <el-form class="stack-form" label-position="top">
          <el-form-item label="房间码">
            <el-input v-model="joinForm.roomCode" maxlength="8" placeholder="请输入 6 到 8 位房间码" />
          </el-form-item>

          <el-button class="primary-btn" type="primary" @click="joinRoom">
            加入当前房间
          </el-button>

          <div class="lobby-tips">
            <p>入桌前提醒</p>
            <ul>
              <li>玩家坐庄时由庄家手动开启首局，AI 庄家房满员自动开始。</li>
              <li>牌局未结束时不能直接返回大厅，避免对局中途丢失上下文。</li>
              <li>历史记录页支持返回未完成牌局，适合中断后继续。</li>
            </ul>
          </div>
        </el-form>
      </el-card>
    </section>
  </main>
</template>
