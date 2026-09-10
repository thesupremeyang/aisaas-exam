/**
 * 外置对话链接工具。
 * 使用 Vite 的 BASE_URL 生成链接，兼容本地版的 /web 上下文路径。
 */

function appBasePath(): string {
  const base = import.meta.env.BASE_URL || '/';
  return base.endsWith('/') ? base : `${base}/`;
}

export function buildCommunicationUrl(chatKey: string): string {
  return `${window.location.origin}${appBasePath()}#/communication/${encodeURIComponent(chatKey)}`;
}

/**
 * 将历史上缺少上下文路径的同源外置对话链接迁移到当前应用路径。
 */
export function normalizeCommunicationUrl(value?: string | null): string {
  if (!value) return '';

  try {
    const url = new URL(value, window.location.href);
    if (
      url.origin === window.location.origin &&
      (url.pathname === '/' || url.pathname === '') &&
      url.hash.startsWith('#/communication/')
    ) {
      return `${url.origin}${appBasePath()}${url.hash}`;
    }
  } catch {
    // 保留原始值，由浏览器继续处理链接。
  }

  return value;
}
