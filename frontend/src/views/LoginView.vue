<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PlayingCard from '../components/PlayingCard.vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)
const activeTab = ref<'login' | 'register'>('login')

const loginForm = reactive({
  username: '',
  password: '',
})

const registerForm = reactive({
  username: '',
  password: '',
  nickname: '',
})

const heroCards = ['黑桃A', '红桃K', '方块Q', '大王']
const quickNotes = ['真人庄家与 AI 庄家双模式', '完整覆盖分组、报水鱼、求走、暗攻与结算', '房间状态实时同步，不用盯着刷新']
const heroStats = [
  { label: '对局节奏', value: '实时同步' },
  { label: '牌桌模式', value: 'AI / 真人' },
  { label: '历史追溯', value: '完整回放' },
]

async function submitLogin() {
  loading.value = true
  try {
    await auth.login(loginForm.username, loginForm.password)
    ElMessage.success('登录成功')
    router.push('/lobby')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    loading.value = false
  }
}

async function submitRegister() {
  loading.value = true
  try {
    await auth.register(registerForm.username, registerForm.password, registerForm.nickname)
    ElMessage.success('注册成功，已自动登录')
    router.push('/lobby')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-hero premium-panel">
      <div class="auth-copy">
        <p class="section-kicker">水鱼扑克</p>
        <h1>把线上牌局做得像真正坐上牌桌一样</h1>
        <p class="hero-copy">
          从建房、发牌、分组到报水鱼和结算，整套流程都能在一个统一桌面里完成。登录之后，我们就能直接进入大厅继续开桌。
        </p>

        <div class="hero-rule-list">
          <span v-for="item in quickNotes" :key="item">{{ item }}</span>
        </div>

        <div class="hero-stats">
          <article v-for="stat in heroStats" :key="stat.label" class="metric-card">
            <small>{{ stat.label }}</small>
            <strong>{{ stat.value }}</strong>
          </article>
        </div>
      </div>

      <div class="hero-stage">
        <div class="hero-stage__halo" />
        <p class="hero-stage__label">今晚开桌</p>
        <div class="card-fan auth-fan">
          <PlayingCard
            v-for="(card, index) in heroCards"
            :key="card"
            :card="card"
            size="hero"
            :style="{ '--fan-index': index }"
          />
        </div>
        <div class="hero-stage__footer">
          <span>分组对局</span>
          <span>历史回放</span>
          <span>牌桌级同步</span>
        </div>
      </div>
    </section>

    <section class="auth-card premium-panel">
      <div class="panel-heading">
        <div>
          <p class="section-kicker">进入系统</p>
          <h2>{{ activeTab === 'login' ? '登录继续牌局' : '创建新账号' }}</h2>
        </div>
        <p class="subtle-text">建议使用固定昵称，历史记录里会更容易识别你的座位与战绩。</p>
      </div>

      <el-segmented
        v-model="activeTab"
        class="mode-toggle"
        :options="[
          { label: '登录', value: 'login' },
          { label: '注册', value: 'register' },
        ]"
      />

      <el-form v-if="activeTab === 'login'" class="auth-form" @submit.prevent="submitLogin">
        <el-form-item label="用户名">
          <el-input v-model="loginForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="loginForm.password"
            type="password"
            show-password
            placeholder="请输入密码"
          />
        </el-form-item>
        <el-button class="primary-btn" :loading="loading" type="primary" @click="submitLogin">
          登录进入大厅
        </el-button>
      </el-form>

      <el-form v-else class="auth-form" @submit.prevent="submitRegister">
        <el-form-item label="用户名">
          <el-input v-model="registerForm.username" placeholder="请输入登录用户名" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="registerForm.nickname" placeholder="请输入游戏昵称" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="registerForm.password"
            type="password"
            show-password
            placeholder="请输入密码"
          />
        </el-form-item>
        <el-button class="primary-btn" :loading="loading" type="primary" @click="submitRegister">
          注册并进入大厅
        </el-button>
      </el-form>
    </section>
  </main>
</template>
