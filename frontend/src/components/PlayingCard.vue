<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  card?: string | null
  faceDown?: boolean
  size?: 'mini' | 'table' | 'picker' | 'hero'
  selected?: boolean
}>(), {
  card: null,
  faceDown: false,
  size: 'table',
  selected: false,
})

const assets = import.meta.glob('../assets/cards/**/*.{png,svg}', {
  eager: true,
  import: 'default',
}) as Record<string, string>

const assetByName = Object.fromEntries(
  Object.entries(assets).map(([path, url]) => [path.split('/').pop() ?? path, url]),
) as Record<string, string>

const suitMap: Record<string, string> = {
  黑桃: 'spades',
  红桃: 'hearts',
  梅花: 'clubs',
  方块: 'diamonds',
}

const rankMap: Record<string, string> = {
  A: 'ace',
  J: 'jack',
  Q: 'queen',
  K: 'king',
}

function resolveAssetName(card: string | null | undefined, faceDown: boolean) {
  if (faceDown) return 'back.png'
  if (!card) return 'white_card.svg'
  if (card === '大王') return 'red_joker.png'
  if (card === '小王') return 'black_joker.png'
  if (card === '白牌') return 'white_card.svg'

  const suit = Object.keys(suitMap).find((item) => card.startsWith(item))
  if (!suit) return 'white_card.svg'

  const rankLabel = card.slice(suit.length)
  const rank = rankMap[rankLabel] ?? rankLabel.toLowerCase()
  return `${rank}_of_${suitMap[suit]}.png`
}

const imageSrc = computed(() => {
  const assetName = resolveAssetName(props.card, props.faceDown)
  return assetByName[assetName] ?? assetByName['white_card.svg']
})

const accessibleLabel = computed(() => {
  if (props.faceDown) return '暗牌'
  return props.card || '扑克牌'
})
</script>

<template>
  <div class="playing-card" :class="[size && `size-${size}`, { selected, 'is-face-down': faceDown }]">
    <img class="playing-card__image" :src="imageSrc" :alt="accessibleLabel" draggable="false" />
  </div>
</template>

<style scoped>
.playing-card {
  position: relative;
  aspect-ratio: 223 / 324;
  border-radius: 11px;
  overflow: hidden;
  border: 1px solid rgba(102, 82, 48, 0.14);
  background: #fffdfa;
  box-shadow:
    0 8px 16px rgba(0, 0, 0, 0.12),
    0 1px 2px rgba(0, 0, 0, 0.08);
  transform: translateZ(0);
  transition:
    transform 180ms ease,
    box-shadow 180ms ease,
    filter 180ms ease;
}

.playing-card::before {
  content: '';
  position: absolute;
  inset: 3px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid rgba(125, 101, 63, 0.05);
  pointer-events: none;
}

.playing-card::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.04), transparent 26%);
  pointer-events: none;
}

.playing-card.is-face-down {
  background: #faf7f0;
}

.playing-card.selected {
  transform: translateY(-6px) rotate(-1.4deg);
  box-shadow:
    0 16px 24px rgba(0, 0, 0, 0.16),
    0 0 0 2px rgba(222, 191, 121, 0.58);
}

.playing-card__image {
  position: relative;
  z-index: 1;
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.size-mini {
  width: 46px;
}

.size-table {
  width: clamp(56px, 7vw, 86px);
}

.size-picker {
  width: clamp(78px, 10vw, 104px);
}

.size-hero {
  width: clamp(118px, 14vw, 176px);
}
</style>
