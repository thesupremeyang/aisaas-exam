<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import * as THREE from 'three'
import HALO from 'vanta/dist/vanta.halo.min'

const host = ref<HTMLElement | null>(null)
let effect: ReturnType<typeof HALO> | null = null

onMounted(() => {
  if (!host.value) return
  effect = HALO({
    el: host.value,
    THREE,
    mouseControls: true,
    touchControls: true,
    gyroControls: false,
    minHeight: 220,
    minWidth: 280,
    scale: 1,
    scaleMobile: 1,
    backgroundColor: 0xeaf3ff,
    baseColor: 0x256fd1,
    color2: 0x6aa9ed,
    amplitudeFactor: 1.35,
    ringFactor: 1.15,
    rotationFactor: 1.1,
    xOffset: 0,
    yOffset: 0,
    size: 1.25,
    speed: 1.05,
  })
})

onBeforeUnmount(() => {
  effect?.destroy()
  effect = null
})
</script>

<template>
  <div ref="host" class="creator-halo-visual" aria-hidden="true" />
</template>

<style scoped>
.creator-halo-visual {
  width: min(320px, calc(100% - 40px));
  height: 236px;
  overflow: hidden;
  border: 1px solid #c7dcf5;
  border-radius: 28px;
  background: #eaf3ff;
  box-shadow:
    0 28px 64px #2f73d91f,
    inset 0 1px 0 #ffffff;
}

.creator-halo-visual :deep(canvas) {
  border-radius: inherit;
}
</style>
