import { ModelProviderType } from '@/types'

export interface ProviderPresetScheme {
  id: string
  label: string
  description: string
  baseUrl: string
  type?: ModelProviderType
}

export interface ModelProviderPreset {
  id: string
  name: string
  shortName: string
  description: string
  type: ModelProviderType
  color: string
  documentationUrl?: string
  schemes: ProviderPresetScheme[]
}

const openAiCompatible = ModelProviderType.OPEN_AI

/**
 * 国内常用模型平台的官方接入预设。
 * 大部分平台实现 OpenAI 兼容协议，因此可复用现有 OPEN_AI 运行时适配器。
 */
export const modelProviderPresets: ModelProviderPreset[] = [
  {
    id: 'deepseek',
    name: 'DeepSeek',
    shortName: 'DS',
    description: 'DeepSeek 官方 API',
    type: openAiCompatible,
    color: '#2563EB',
    documentationUrl: 'https://api-docs.deepseek.com/',
    schemes: [
      {
        id: 'official',
        label: '官方 API',
        description: '适用于 DeepSeek 平台创建的 API Key',
        baseUrl: 'https://api.deepseek.com',
      },
    ],
  },
  {
    id: 'mimo',
    name: '小米 MiMo',
    shortName: 'Mi',
    description: '小米 MiMo API 开放平台',
    type: openAiCompatible,
    color: '#FF6900',
    documentationUrl: 'https://mimo.mi.com/docs/zh-CN/quick-start/summary/first-api-call',
    schemes: [
      {
        id: 'payg',
        label: '按量付费 API',
        description: '使用 sk- 开头的 API Key',
        baseUrl: 'https://api.xiaomimimo.com/v1',
      },
      {
        id: 'token-plan',
        label: 'Token Plan',
        description: '使用套餐专属 tp- Key',
        baseUrl: 'https://token-plan-cn.xiaomimimo.com/v1',
      },
    ],
  },
  {
    id: 'volcengine',
    name: '火山方舟',
    shortName: 'ARK',
    description: '豆包及火山方舟模型服务',
    type: openAiCompatible,
    color: '#4F46E5',
    documentationUrl: 'https://www.volcengine.com/docs/82379',
    schemes: [
      {
        id: 'ark',
        label: '方舟 API',
        description: '适用于方舟控制台创建的 API Key',
        baseUrl: 'https://ark.cn-beijing.volces.com/api/v3',
      },
      {
        id: 'coding-plan',
        label: 'Coding Plan / AgentPlan',
        description: '适用于方舟 Coding Plan 与 AgentPlan 专属 API Key',
        baseUrl: 'https://ark.cn-beijing.volces.com/api/coding/v3',
      },
    ],
  },
  {
    id: 'moonshot',
    name: 'Kimi',
    shortName: 'K',
    description: '月之暗面 Moonshot AI',
    type: openAiCompatible,
    color: '#171717',
    documentationUrl: 'https://platform.moonshot.cn/docs',
    schemes: [
      {
        id: 'official',
        label: '官方 API',
        description: '适用于 Moonshot 平台创建的 API Key',
        baseUrl: 'https://api.moonshot.cn/v1',
      },
      {
        id: 'kimi-code',
        label: 'Kimi Code',
        description: '适用于 Kimi Code 会员创建的专属 API Key',
        baseUrl: 'https://api.kimi.com/coding/v1',
        type: openAiCompatible,
      },
    ],
  },
  {
    id: 'dashscope',
    name: '阿里云百炼',
    shortName: 'AI',
    description: '通义千问与百炼模型服务',
    type: ModelProviderType.DASH_SCOPE,
    color: '#1677FF',
    documentationUrl: 'https://help.aliyun.com/zh/model-studio/',
    schemes: [
      {
        id: 'dashscope',
        label: '百炼原生 API',
        description: '使用 DashScope 协议，支持原生思考模式',
        baseUrl: 'https://dashscope.aliyuncs.com',
      },
      {
        id: 'compatible',
        label: '按量付费（OpenAI 兼容）',
        description: '适用于百炼按量付费 API Key',
        baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
        type: openAiCompatible,
      },
      {
        id: 'coding-plan',
        label: '百炼 Coding Plan',
        description: '使用 Coding Plan 专属 API Key',
        baseUrl: 'https://coding.dashscope.aliyuncs.com/v1',
        type: openAiCompatible,
      },
      {
        id: 'token-plan',
        label: '百炼 Token Plan 团队版',
        description: '使用 Token Plan 团队版专属 API Key',
        baseUrl: 'https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1',
        type: openAiCompatible,
      },
    ],
  },
  {
    id: 'zhipu',
    name: '智谱 AI',
    shortName: 'GLM',
    description: 'GLM 系列模型服务',
    type: openAiCompatible,
    color: '#0F766E',
    documentationUrl: 'https://docs.bigmodel.cn/cn/guide/develop/http/introduction',
    schemes: [
      {
        id: 'official',
        label: '通用 API',
        description: '适用于智谱开放平台 API Key',
        baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
      },
      {
        id: 'coding-plan',
        label: 'GLM Coding Plan',
        description: '使用 GLM Coding Plan 专属 API Key',
        baseUrl: 'https://open.bigmodel.cn/api/coding/paas/v4',
        type: openAiCompatible,
      },
    ],
  },
  {
    id: 'qianfan',
    name: '百度千帆',
    shortName: 'QF',
    description: '文心与千帆模型服务',
    type: openAiCompatible,
    color: '#2932E1',
    documentationUrl: 'https://cloud.baidu.com/doc/qianfan/s/Hmh4suq26',
    schemes: [
      {
        id: 'official',
        label: 'OpenAI 兼容 API',
        description: '使用千帆 API Key',
        baseUrl: 'https://qianfan.baidubce.com/v2',
      },
      {
        id: 'coding-plan',
        label: '千帆 Coding Plan',
        description: '使用 Coding Plan 专属 API Key',
        baseUrl: 'https://qianfan.baidubce.com/v2/coding',
        type: openAiCompatible,
      },
    ],
  },
  {
    id: 'hunyuan',
    name: '腾讯混元',
    shortName: 'HY',
    description: '腾讯云混元大模型服务',
    type: openAiCompatible,
    color: '#00A870',
    documentationUrl: 'https://cloud.tencent.com/document/product/1729/111007',
    schemes: [
      {
        id: 'official',
        label: 'OpenAI 兼容 API',
        description: '使用混元 API Key',
        baseUrl: 'https://api.hunyuan.cloud.tencent.com/v1',
      },
      {
        id: 'tokenhub-coding',
        label: '腾讯云 TokenHub Coding Plan',
        description: '使用 TokenHub Coding Plan 专属 sk-sp Key',
        baseUrl: 'https://api.lkeap.cloud.tencent.com/coding/v3',
        type: openAiCompatible,
      },
    ],
  },
  {
    id: 'spark',
    name: '讯飞星火',
    shortName: 'XF',
    description: '讯飞星火认知大模型',
    type: openAiCompatible,
    color: '#2563EB',
    documentationUrl:
      'https://www.xfyun.cn/doc/spark/HTTP%E8%B0%83%E7%94%A8%E6%96%87%E6%A1%A3.html',
    schemes: [
      {
        id: 'official',
        label: 'HTTP API',
        description: '填入控制台获取的 APIPassword',
        baseUrl: 'https://spark-api-open.xf-yun.com/v1',
      },
      {
        id: 'astron-coding-plan',
        label: '讯飞星辰 Coding Plan',
        description: '使用讯飞星辰 MaaS Coding Plan 专属 API Key',
        baseUrl: 'https://maas-coding-api.cn-huabei-1.xf-yun.com/v2',
        type: openAiCompatible,
      },
    ],
  },
  {
    id: 'minimax',
    name: 'MiniMax',
    shortName: 'MM',
    description: 'MiniMax 开放平台',
    type: openAiCompatible,
    color: '#7C3AED',
    documentationUrl: 'https://platform.minimaxi.com/docs/api-reference/text-openai-api',
    schemes: [
      {
        id: 'official',
        label: 'OpenAI 兼容 API',
        description: '使用 MiniMax API Key',
        baseUrl: 'https://api.minimaxi.com/v1',
      },
      {
        id: 'token-plan',
        label: 'MiniMax Token Plan / Code',
        description: '使用 Token Plan 专属 sk-cp Key',
        baseUrl: 'https://api.minimaxi.com/anthropic',
        type: ModelProviderType.ANTHROPIC,
      },
    ],
  },
  {
    id: 'siliconflow',
    name: '硅基流动',
    shortName: 'SF',
    description: '多模型推理与部署平台',
    type: openAiCompatible,
    color: '#E05B36',
    documentationUrl: 'https://docs.siliconflow.cn/cn/userguide/quickstart',
    schemes: [
      {
        id: 'official',
        label: 'OpenAI 兼容 API',
        description: '使用硅基流动 API Key',
        baseUrl: 'https://api.siliconflow.cn/v1',
      },
    ],
  },
  {
    id: 'stepfun',
    name: '阶跃星辰 StepFun',
    shortName: 'SF',
    description: '阶跃星辰模型与 Step Plan 编程套餐',
    type: ModelProviderType.ANTHROPIC,
    color: '#16D6D2',
    documentationUrl: 'https://platform.stepfun.com/',
    schemes: [
      {
        id: 'step-plan',
        label: 'Step Plan',
        description: '使用 Step Plan 专属 API Key',
        baseUrl: 'https://api.stepfun.com/step_plan',
      },
    ],
  },
  {
    id: 'longcat',
    name: '美团 LongCat',
    shortName: 'LC',
    description: 'LongCat 模型开放平台',
    type: ModelProviderType.ANTHROPIC,
    color: '#29E154',
    documentationUrl: 'https://longcat.chat/platform',
    schemes: [
      {
        id: 'official',
        label: '官方 API',
        description: '使用 LongCat 平台创建的 API Key',
        baseUrl: 'https://api.longcat.chat/anthropic',
      },
    ],
  },
  {
    id: 'bailing',
    name: '蚂蚁百灵',
    shortName: 'BL',
    description: '蚂蚁百灵大模型服务',
    type: ModelProviderType.ANTHROPIC,
    color: '#1677FF',
    documentationUrl: 'https://alipaytbox.yuque.com/sxs0ba/ling/get_started',
    schemes: [
      {
        id: 'official',
        label: '官方 API',
        description: '使用百灵平台创建的 API Key',
        baseUrl: 'https://api.tbox.cn/api/anthropic',
      },
    ],
  },
  {
    id: 'modelscope',
    name: '魔搭 ModelScope',
    shortName: 'MS',
    description: '阿里云模型社区与推理服务',
    type: ModelProviderType.ANTHROPIC,
    color: '#624AFF',
    documentationUrl: 'https://modelscope.cn/docs',
    schemes: [
      {
        id: 'inference',
        label: '推理服务 API',
        description: '使用 ModelScope Access Token',
        baseUrl: 'https://api-inference.modelscope.cn',
      },
    ],
  },
  {
    id: 'ollama',
    name: 'Ollama',
    shortName: 'OL',
    description: '本地或私有化部署模型',
    type: ModelProviderType.OLLAMA,
    color: '#111827',
    documentationUrl: 'https://ollama.com/',
    schemes: [
      {
        id: 'local',
        label: '本地服务',
        description: '请确认 Ollama 服务已在本机启动',
        baseUrl: 'http://localhost:11434',
      },
    ],
  },
  {
    id: 'custom',
    name: '自定义兼容接口',
    shortName: 'API',
    description: '兼容 OpenAI API 的其他平台或私有服务',
    type: openAiCompatible,
    color: '#64748B',
    schemes: [
      { id: 'custom', label: '自定义地址', description: '保存前请填写 API 基础地址', baseUrl: '' },
    ],
  },
]

export function getModelProviderPreset(id?: string) {
  return modelProviderPresets.find((item) => item.id === id)
}

const EXAM_MODEL_PROVIDER_DESCRIPTION = '考生专属考试模型，请勿在管理端修改'

function normalizeBaseUrl(baseUrl?: string | null): string {
  return (baseUrl || '').replace(/\/+$/, '').toLowerCase()
}

/**
 * 获取模型提供商在管理页的展示名称。
 * 考试场景会用批次和考生账号生成唯一的内部名称，展示时恢复为服务商预设名称。
 */
export function getModelProviderDisplayName(provider: {
  type?: string | null
  name?: string | null
  description?: string | null
  baseUrl?: string | null
}): string {
  if (provider.description !== EXAM_MODEL_PROVIDER_DESCRIPTION) {
    return provider.name || ''
  }

  const baseUrl = normalizeBaseUrl(provider.baseUrl)
  const preset = modelProviderPresets.find((item) => item.schemes.some((scheme) => {
    const schemeType = scheme.type || item.type
    return schemeType === provider.type && normalizeBaseUrl(scheme.baseUrl) === baseUrl
  }))

  return preset?.name || provider.name || ''
}
