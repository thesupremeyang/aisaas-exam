<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import { issueSsoTicket } from '@/api/exam'
import { useAccountStore } from '@/stores'

const route = useRoute()
const router = useRouter()
const accountStore = useAccountStore()
const loading = ref(false)
const campaignId = computed(() => String(route.query.campaignId || ''))

onMounted(() => {
  if (!campaignId.value) return
  sessionStorage.setItem('exam:campaignId', campaignId.value)
  sessionStorage.setItem('exam:candidate-mode', '1')
  sessionStorage.setItem(
    'exam:candidate-account',
    String(accountStore.userInfo?.id || accountStore.userInfo?.username || ''),
  )
  window.dispatchEvent(new Event('exam:context-updated'))
})

async function enterPlatform() {
  if (!campaignId.value) return message.warning('未获取到考试批次，请返回考试门户后重试')
  loading.value = true
  try {
    const returnUrl = '/#/exam/portal'
    const response = await issueSsoTicket(campaignId.value, returnUrl)
    const ticket = response.data.data?.ticket
    if (!ticket) throw new Error('未获取到 SSO 凭证')
    await router.push({ path: '/agent', query: { examTicket: ticket } })
  } catch {
    message.error('进入智能体平台失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="gateway-page">
    <p class="crumb">考试门户 / 制作智能体</p>
    <section class="gateway-card">
      <span class="icon">AI</span>
      <p class="eyebrow">智能体制作平台</p>
      <h1>先完成作品制作，再返回提交</h1>
      <p>你将通过统一登录进入智能体平台，在“我的智能体”中创建或完善作品。完成后返回考试门户选择并提交该智能体。</p>
      <div class="gateway-row"><div><b>安全登录跳转</b><small>使用一次性 SSO Ticket，不会在地址栏暴露登录凭证或模型密钥。</small></div><span>5 分钟有效</span></div>
      <div class="actions"><a-button @click="router.push({ name: 'ExamPortal' })">暂不进入</a-button><a-button type="primary" :loading="loading" @click="enterPlatform">进入智能体平台</a-button></div>
    </section>
  </main>
</template>

<style scoped lang="scss">
.gateway-page{max-width:780px;margin:auto;padding:52px 20px}.crumb{font-size:12px;color:#8090a8;margin-bottom:20px}.gateway-card{text-align:center;background:#fff;border:1px solid #dbe6f6;border-radius:16px;padding:48px;box-shadow:0 16px 40px #174f9d12}.icon{display:inline-grid;place-items:center;width:54px;height:54px;border-radius:15px;background:linear-gradient(135deg,#1681ff,#1749ba);color:#fff;font-weight:800}.eyebrow{margin:22px 0 8px;color:#4b84d9;font-size:12px;font-weight:700;letter-spacing:.14em}.gateway-card h1{font:30px/1.35 Georgia,"Microsoft YaHei",serif;margin:0 0 12px;color:#0a1e45}.gateway-card>p{line-height:1.8;color:#6d7c94}.gateway-row{display:flex;justify-content:space-between;align-items:center;text-align:left;background:#eaf3ff;border:1px solid #d2e4ff;border-radius:10px;padding:17px;margin:27px 0}.gateway-row b,.gateway-row small{display:block}.gateway-row small{margin-top:5px;color:#6d7c94}.gateway-row>span{font-size:12px;color:#2d68b4}.actions{display:flex;justify-content:center;gap:12px}@media(max-width:640px){.gateway-card{padding:34px 22px}.gateway-row{display:block}.gateway-row>span{display:block;margin-top:9px}}
</style>
