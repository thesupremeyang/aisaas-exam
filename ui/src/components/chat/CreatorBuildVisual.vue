<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import {
  CodeOutlined,
  RobotOutlined,
  ThunderboltOutlined,
  ToolOutlined,
} from '@ant-design/icons-vue'
import { tsParticles, type Container } from '@tsparticles/engine'
import { loadSlim } from '@tsparticles/slim'

let instanceCount = 0
const canvasId = `creator-particles-${++instanceCount}`
let container: Container | undefined

onMounted(async () => {
  await loadSlim(tsParticles)
  container = await tsParticles.load({
    id: canvasId,
    options: {
      fullScreen: { enable: false },
      background: { color: { value: 'transparent' } },
      detectRetina: true,
      fpsLimit: 60,
      particles: {
        color: {
          value: ['#1769d2', '#2f80ed', '#62a5ee', '#a8cff7'],
        },
        links: {
          enable: true,
          color: '#75ace8',
          distance: 92,
          opacity: 0.58,
          width: 1.15,
        },
        move: {
          enable: true,
          speed: 1.1,
          direction: 'none',
          random: true,
          straight: false,
          outModes: { default: 'bounce' },
        },
        number: {
          value: 52,
          density: { enable: true, width: 320, height: 240 },
        },
        opacity: {
          value: { min: 0.38, max: 0.92 },
          animation: {
            enable: true,
            speed: 0.75,
            sync: false,
          },
        },
        shape: { type: 'circle' },
        size: {
          value: { min: 1.8, max: 5.8 },
          animation: {
            enable: true,
            speed: 2.2,
            sync: false,
          },
        },
      },
      interactivity: {
        detectsOn: 'canvas',
        events: {
          onHover: { enable: true, mode: 'repulse' },
          resize: { enable: true },
        },
        modes: {
          repulse: { distance: 54, duration: 0.35 },
        },
      },
    },
  })
})

onBeforeUnmount(() => {
  container?.destroy()
})
</script>

<template>
  <div class="creator-build-visual" aria-hidden="true">
    <div :id="canvasId" class="creator-particles" />
    <div class="creator-scan-line" />
    <div class="creator-module creator-module-prompt">
      <CodeOutlined /><span>指令</span>
    </div>
    <div class="creator-module creator-module-skill">
      <ThunderboltOutlined /><span>技能</span>
    </div>
    <div class="creator-module creator-module-tool">
      <ToolOutlined /><span>工具</span>
    </div>
    <div class="creator-build-core">
      <span class="creator-core-pulse" />
      <RobotOutlined />
      <b>智能体核心</b>
    </div>
  </div>
</template>

<style scoped>
.creator-build-visual {
  position: relative;
  width: min(300px, calc(100% - 44px));
  height: 224px;
  overflow: hidden;
  border: 1px solid #cfe0f5;
  border-radius: 28px;
  background:
    radial-gradient(circle at 50% 48%, #dcecff 0, #edf5ff 34%, #f8fbff 72%);
  box-shadow:
    0 24px 56px #265a991f,
    inset 0 1px 0 #fff;
}

.creator-particles {
  position: absolute;
  inset: 0;
}

.creator-build-core {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 112px;
  height: 82px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 7px;
  color: #1f63bd;
  border: 1px solid #9ec4f1;
  border-radius: 24px;
  background: #ffffffeb;
  box-shadow:
    0 18px 44px #1d5fa42b,
    0 0 0 10px #cfe3fb75;
  transform: translate(-50%, -50%);
  animation: creator-core-breathe 2.4s ease-in-out infinite;
}

.creator-build-core > .anticon {
  position: relative;
  z-index: 2;
  font-size: 27px;
}

.creator-build-core b {
  position: relative;
  z-index: 2;
  font-size: 11px;
  letter-spacing: .04em;
}

.creator-core-pulse {
  position: absolute;
  inset: 10px;
  border: 1px solid #8ab8ed;
  border-radius: 18px;
  opacity: 0;
  animation: creator-core-pulse 2.4s ease-out infinite;
}

.creator-module {
  position: absolute;
  z-index: 3;
  min-width: 76px;
  height: 34px;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  color: #285f9e;
  font-size: 11px;
  font-weight: 650;
  border: 1px solid #b7d2ef;
  border-radius: 999px;
  background: #ffffffed;
  box-shadow: 0 8px 22px #2c67a822;
}

.creator-module-prompt {
  top: 28px;
  left: 22px;
  animation: creator-module-prompt 4.8s ease-in-out infinite;
}

.creator-module-skill {
  top: 30px;
  right: 20px;
  animation: creator-module-skill 4.8s .8s ease-in-out infinite;
}

.creator-module-tool {
  right: 28px;
  bottom: 24px;
  animation: creator-module-tool 4.8s 1.6s ease-in-out infinite;
}

.creator-scan-line {
  position: absolute;
  z-index: 2;
  top: -20%;
  bottom: -20%;
  width: 64px;
  opacity: .6;
  background: linear-gradient(90deg, transparent, #ffffffd9, transparent);
  filter: blur(2px);
  transform: skewX(-14deg);
  animation: creator-scan 3.6s ease-in-out infinite;
}

@keyframes creator-core-breathe {
  0%,
  100% {
    transform: translate(-50%, -50%) scale(1);
  }
  50% {
    transform: translate(-50%, -50%) scale(1.055);
    box-shadow:
      0 22px 52px #1d5fa43b,
      0 0 0 15px #cfe3fb55;
  }
}

@keyframes creator-core-pulse {
  0% {
    opacity: .8;
    transform: scale(.82);
  }
  75%,
  100% {
    opacity: 0;
    transform: scale(1.55);
  }
}

@keyframes creator-module-prompt {
  0%,
  100% { transform: translate(0, 0); }
  50% { transform: translate(36px, 32px); }
}

@keyframes creator-module-skill {
  0%,
  100% { transform: translate(0, 0); }
  50% { transform: translate(-36px, 30px); }
}

@keyframes creator-module-tool {
  0%,
  100% { transform: translate(0, 0); }
  50% { transform: translate(-34px, -28px); }
}

@keyframes creator-scan {
  0% { left: -30%; }
  60%,
  100% { left: 115%; }
}

@media (prefers-reduced-motion: reduce) {
  .creator-build-core,
  .creator-core-pulse,
  .creator-module,
  .creator-scan-line {
    animation: none;
  }
}
</style>
