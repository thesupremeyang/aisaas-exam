/**
 * 广场"复制到我的空间"流程（方案 G）
 *
 * <p>业务流程：
 * <ol>
 *   <li>弹 Modal.confirm 提示用户克隆的注意事项</li>
 *   <li>确认后调用 POST /api/v2-portal/clone/agent</li>
 *   <li>成功后触发 onSuccess 回调（由调用方决定跳转/提示）</li>
 * </ol>
 *
 * @author agent-clone-feature
 */
import { h } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { cloneAgentByPortal, type AgentCloneResponse } from '@/api/v2PortalClone'

export interface CloneFlowOptions {
  sourceAgentId: string | number
  sourceName?: string
  /** 克隆成功后的回调 */
  onSuccess: (data: AgentCloneResponse) => void
  /** 克隆失败/取消时的回调（可选） */
  onFail?: (err?: Error) => void
}

/**
 * 渲染确认弹窗的提示内容（使用 h 函数创建 VNode 避免 HTML 被转义）
 */
function renderConfirmContent() {
  return h('div', { style: 'line-height:1.8;color:#595959;' }, [
    h('p', null, '将把该智能体复制到您所在租户下作为本地副本。'),
    h(
      'ul',
      { style: 'margin:0;padding-left:20px;' },
      [
        h('li', null, [
          '系统提示词、技能包（含代码文件和内置工具关联）将',
          h('strong', { style: 'color:#52c41a;' }, '完整复制'),
          '到您的空间',
        ]),
        h('li', null, [
          '关联的模型、知识库、自定义工具等',
          h('strong', { style: 'color:#d4380d;' }, '不会复制'),
          '，需您重新配置',
        ]),
        h('li', null, [
          '克隆后默认',
          h('strong', { style: 'color:#d4380d;' }, '禁用'),
          '，进入编辑页启用后即可使用',
        ]),
        h('li', null, '本副本与原智能体相互独立，后续修改互不影响'),
      ]
    ),
  ])
}

/**
 * 执行克隆流程（弹确认框 → 调接口 → 回调）
 *
 * @returns Promise<boolean> true=成功, false=用户取消或失败
 */
export async function runCloneAgentFlow(opts: CloneFlowOptions): Promise<boolean> {
  const { sourceAgentId, sourceName, onSuccess, onFail } = opts
  const title = sourceName ? `复制「${sourceName}」到我的空间？` : '复制到我的空间？'

  return new Promise<boolean>((resolve) => {
    Modal.confirm({
      title,
      content: renderConfirmContent(),
      okText: '确认复制',
      cancelText: '取消',
      width: 520,
      onOk: async () => {
        const hideKey = `__clone_${Date.now()}`
        message.loading({ content: '复制中...', key: hideKey, duration: 0 })
        try {
          const res = await cloneAgentByPortal(sourceAgentId)
          message.destroy(hideKey)
          const data = res?.data?.data
          if (!data?.id) {
            throw new Error('响应数据为空')
          }
          message.success(`已复制到我的空间：${data.name}，请先配置模型后再启用使用`)
          onSuccess(data)
          resolve(true)
        } catch (e: any) {
          message.destroy(hideKey)
          const errMsg = e?.response?.data?.msg || e?.message || '复制失败'
          Modal.error({ title: '复制失败', content: errMsg })
          onFail?.(e instanceof Error ? e : new Error(errMsg))
          resolve(false)
        }
      },
      onCancel: () => {
        onFail?.()
        resolve(false)
      },
    })
  })
}
