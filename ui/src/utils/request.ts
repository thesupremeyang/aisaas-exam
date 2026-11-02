import axios, { type AxiosRequestConfig } from "axios";
import { tansRequestParams, checkRequestRepeat } from "@/utils/tools";
import {
  getRequestToken,
  getRefreshToken,
  setToken,
  setRefreshToken,
  removeToken,
  removeRefreshToken
} from "./auth";
import setting from "@/config/setting";
import NProgress from "nprogress";
import "nprogress/nprogress.css";
import { message as AMessage } from 'ant-design-vue'
import { refreshToken as refreshTokenApi } from "@/api/auth";

type TokenRefreshCallback = (newToken: string) => void;

declare module "axios" {
  export interface AxiosRequestConfig {
    /** 轮询/后台请求：不在拦截器里弹任何提示 */
    silent?: boolean;
    /**
     * 传输层失败自动重试次数。
     * 默认 GET / silent 请求重试 2 次；写操作默认不自动重试（避免重复提交）。
     * 传 false 关闭；传数字覆盖次数。
     */
    retry?: boolean | number;
  }
}

NProgress.configure({ easing: "ease", speed: 150, showSpinner: false });

const instance = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API,
});

const APP_LOGIN_URL = `${((import.meta.env.BASE_URL as string) || '/').replace(/\/?$/, '/')}#/login`;

// 用于标记是否正在刷新令牌，防止并发请求重复刷新
let isRefreshing = false;
// 存储等待刷新令牌完成后重试的请求
let requestsQueue:TokenRefreshCallback[] = [];

function isGetMethod(config?: AxiosRequestConfig): boolean {
  return (config?.method || "get").toLowerCase() === "get";
}

const TRANSPORT_RETRY_DELAYS_MS = [600, 1800] as const;

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** 传输层失败是否值得自动重试（弱网抖动下提升成功率，且避免重复写） */
function shouldRetryTransport(config: AxiosRequestConfig | undefined): boolean {
  if (!config) return false;
  if (config.retry === false) return false;
  if (typeof config.retry === "number") return config.retry > 0;
  // 默认只自动重试幂等读 / 显式 silent 轮询
  return isGetMethod(config) || config.silent === true;
}

function nextRetryDelay(attempt: number): number {
  return TRANSPORT_RETRY_DELAYS_MS[attempt] ?? 1800;
}

function maxTransportRetries(config?: AxiosRequestConfig): number {
  if (!config) return 0;
  if (config.retry === false) return 0;
  if (typeof config.retry === "number") return config.retry;
  return shouldRetryTransport(config) ? TRANSPORT_RETRY_DELAYS_MS.length : 0;
}

/** 传输层失败（超时/断网/连接被重置）——不弹红框，只静默失败 */
function isTransportError(error: any): boolean {
  return !error?.response;
}

/**
 * 网络抖动属于常态，不要用红色 error 吓用户。
 * 轮询类（GET / silent）完全静默；用户主动写操作最多给一次温和提示。
 */
function isOnLoginPage(): boolean {
  return typeof window !== 'undefined' && window.location.hash.includes('login');
}

function notifyTransportFailure(config?: AxiosRequestConfig) {
  if (!config || config.silent || isGetMethod(config)) return;
  // 登录页保持安静，避免弱网时连环弹窗
  if (isOnLoginPage()) return;
  AMessage.warning("网络不稳定，请稍后重试").then(() => {});
}

function shouldNotifyBusinessError(config?: AxiosRequestConfig): boolean {
  if (!config || config.silent) return false;
  return !isGetMethod(config);
}

// 请求拦截器
instance.interceptors.request.use(
  (config) => {
    NProgress.start();

    // v2 门户模式：所有 API 请求使用 tenant=1 上下文
    if ((window as any).__PORTAL_V2__) {
      config.headers['X-Portal-V2'] = 'true';
    }
    // 携带凭证
    const needToken = !((config.headers || {}).token === false);
    const token = getRequestToken();
    if (needToken && token) {
      config.headers[setting.tokenHeader] = 'Bearer ' + token;
    }

    // 参数转换
    if (config.params) {
      // 请求参数转换
      let url = config.url + "?" + tansRequestParams(config.params);
      url = url.slice(0, -1);
      config.params = {};
      config.url = url;
    }

    // 检测是否重复操作
    if (
      (config.headers || {}).unrepeat &&
      checkRequestRepeat(<string>config.url, {
        ...config.data,
        ...config.params,
      })
    ) {
      AMessage.warning('请勿重复操作').then(() => {})
      NProgress.done();
      return Promise.reject(new Error("请勿重复操作"));
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 响应拦截器
instance.interceptors.response.use(
  (response) => {
    NProgress.done();
    if (
      response.request.responseType === "blob" ||
      response.request.responseType === "arraybuffer"
    ) {
      return response;
    }

    const code = response.data.code || 500;
    const msg = response.data.msg || "未知异常";
    if(code == 200) {
      return response;
    } else if (code == 202) {
      if (!response.config?.silent) {
        AMessage.info(msg).then(() => {})
      }
      return response;
    } else if(code === 401) {
      // 未登录/过期是常态跳转，避免突兀弹窗打断登录页
      if (!isOnLoginPage()) {
        AMessage.warning('登录状态已失效，请重新登录').then(() => {})
      }
      window.location.href = APP_LOGIN_URL;
      window.location.reload();
      return Promise.reject(new Error(msg));
    } else {
      // 业务拒绝（如「尚未开启查分」/「用户名或密码错误」）不是系统崩溃：
      // GET/轮询静默；写操作用温和提示；登录页只提示账号结果，不刷屏
      if (shouldNotifyBusinessError(response.config) || isOnLoginPage()) {
        if (isOnLoginPage() && isGetMethod(response.config)) return Promise.reject(new Error(msg));
        AMessage.warning(msg).then(() => {})
      }
      return Promise.reject(new Error(msg));
    }
  },
  async (error) => {
    const originalRequest = error.config;
    // 1. 非401：传输层失败静默，避免 timeout of xxxms / Network Error 红刷屏
    if (!error.response || error.response.status !== 401) {
      NProgress.done();
      if (isTransportError(error)) {
        // 弱网抖动：对读请求自动退避重试，真正失败再交给调用方
        const alreadyRetried = Number((originalRequest as any)?._transportRetryCount || 0);
        const maxRetry = maxTransportRetries(originalRequest);
        if (originalRequest && alreadyRetried < maxRetry) {
          (originalRequest as any)._transportRetryCount = alreadyRetried + 1;
          await sleep(nextRetryDelay(alreadyRetried));
          return instance(originalRequest);
        }
        notifyTransportFailure(originalRequest);
        const friendly = new Error("网络连接不稳定，请稍后重试");
        (friendly as any).isTransportError = true;
        return Promise.reject(friendly);
      }
      // HTTP 非 2xx 但有响应体：也别甩英文 axios 原文
      const status = error.response?.status;
      const bodyMsg = error.response?.data?.msg;
      const text = bodyMsg || (status ? `请求失败（${status}）` : "请求失败");
      if (shouldNotifyBusinessError(originalRequest)) {
        AMessage.warning(text).then(() => {})
      }
      return Promise.reject(new Error(text));
    }

    const quietLoginRedirect = (tip: string) => {
      removeToken();
      removeRefreshToken();
      requestsQueue = [];
      if (!isOnLoginPage()) {
        AMessage.warning(tip).then(() => {})
      }
      window.location.href = APP_LOGIN_URL;
      window.location.reload();
    };

    // 2. 如果是刷新令牌接口本身返回401，直接清理令牌并跳转登录
    if (originalRequest.headers[setting.refreshTokenRequest] || originalRequest.url.includes('/api/auth/refresh-token')) {
      quietLoginRedirect('登录状态已失效，请重新登录');
      return Promise.reject(new Error('刷新令牌失败'));
    }

    // 3. 防止重复重试
    if (originalRequest.headers._retry) {
      quietLoginRedirect('登录状态已失效，请重新登录');
      return Promise.reject(new Error('令牌重试失败'));
    }

    originalRequest.headers._retry = true;

    // 4. 处理令牌刷新逻辑
    if (!isRefreshing) {
      isRefreshing = true;
      const refreshToken = getRefreshToken();

      if (!refreshToken) {
        isRefreshing = false;
        quietLoginRedirect('登录状态已失效，请重新登录');
        return Promise.reject(new Error('无刷新令牌'));
      }

      try {
        // 标记刷新令牌请求，便于识别
        const refreshResponse = await refreshTokenApi(refreshToken);

        const newToken = refreshResponse.data.data.accessToken;
        const newTokenTTL = refreshResponse.data.data.accessTokenTTL;
        const newRefreshToken = refreshResponse.data.data.refreshToken;
        const newRefreshTokenTTL = refreshResponse.data.data.refreshTokenTTL;

        setToken({
          value: newToken,
          ttl: newTokenTTL
        });
        setRefreshToken({
          value: newRefreshToken,
          ttl: newRefreshTokenTTL
        });

        // 更新原请求的令牌
        originalRequest.headers[setting.tokenHeader] = `Bearer ${newToken}`;

        // 重试队列中所有等待的请求
        requestsQueue.forEach(cb => cb(newToken));
        requestsQueue = [];

        isRefreshing = false;
        return instance(originalRequest);
      } catch (err) {
        // 刷新令牌失败，清理所有状态
        isRefreshing = false;
        quietLoginRedirect('登录状态已失效，请重新登录');
        return Promise.reject(err);
      }
    } else {
      // 有其他请求正在刷新令牌，加入队列等待
      return new Promise((resolve) => {
        requestsQueue.push((newToken: string) => {
          originalRequest.headers[setting.tokenHeader] = `Bearer ${newToken}`;
          resolve(instance(originalRequest));
        });
      });
    }
  }
);

export default instance;
