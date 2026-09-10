<script lang="ts">
import { defineComponent, ref, computed, h } from 'vue'
import { useRoute } from 'vue-router'
import { chatKeyToken } from '@/api/auth.ts'
import { getAgentIdByChatKey } from '@/api/agentChatKey.ts'
import { removeExternalChatToken, setExternalChatToken } from '@/utils/auth'
import type { AccountVO } from '@/types'
import Chat from '@/views/Chat/index.vue'

const APP_LOGIN_URL = `${((import.meta.env.BASE_URL as string) || '/').replace(/\/?$/, '/')}#/login`

export default defineComponent({
  name: 'ChatWrapper',
  emits: ['error'],
  async setup(props, { emit }) {
    const chatAgentId = ref<string>()
    const externalUserInfo = ref<AccountVO | null>(null)
    const route = useRoute()
    const chatKey = computed(() => (route.params.chatKey as string) || '')

    try {
      // 无论当前标签页是否已有登录态，都按链接换取该智能体所属租户的 ChatKey 令牌。
      // 令牌只放在 sessionStorage，避免覆盖复核员/管理员的正常登录信息。
      removeExternalChatToken()
      const response = await chatKeyToken(chatKey.value)
      const data = response.data.data
      if (!data) {
        window.location.href = APP_LOGIN_URL;
        return
      }
      setExternalChatToken(data.accessToken)
      const userDetail = data.userDetail
      externalUserInfo.value = {
        id: userDetail.id,
        nickname: userDetail.name,
        email: userDetail.email,
        username: userDetail.username,
        enabled: true
      }

      const res = await getAgentIdByChatKey(chatKey.value)
      chatAgentId.value = res.data.data
    } catch (error) {
      // 使用 h 函数返回虚拟节点
      console.error('初始化失败:', error)
      removeExternalChatToken()
      emit('error', errorMessage(error))
    }

    // 使用 h 函数返回虚拟节点
    return () => h(Chat, {
      chatAgentId: chatAgentId.value,
      showAccount: false,
      externalUserInfo: externalUserInfo.value
    })
  }
})

function errorMessage(error: unknown): string {
  if (typeof error === 'string') return error
  if (error instanceof Error) return error.message
  const response = error as { response?: { data?: { msg?: string } } } | null
  return response?.response?.data?.msg || '无法加载演示智能体'
}
</script>
