export const DEFAULT_AVATARS = {
  male: '/assets/avatars/default-male.svg',
  female: '/assets/avatars/default-female.svg',
} as const;

// Figma 工作台示例账号使用项目登记的男性默认头像，避免运行时加载真人素材。
export const FIGMA_WORKSPACE_AVATARS = {
  sidebar: DEFAULT_AVATARS.male,
  topbar: DEFAULT_AVATARS.male,
} as const;

// Knowledge Figma fixture 使用项目登记的男性默认头像。
export const FIGMA_KNOWLEDGE_AVATARS = {
  sidebar: DEFAULT_AVATARS.male,
  topbar: DEFAULT_AVATARS.male,
} as const;

// Profile Figma fixture 的示例账号为男性，所有默认头像统一使用男性资源。
export const FIGMA_PROFILE_AVATARS = {
  sidebar: DEFAULT_AVATARS.male,
  topbar: DEFAULT_AVATARS.male,
  main: DEFAULT_AVATARS.male,
} as const;

// Admin Figma fixture 的示例账号为男性，统一使用男性默认头像。
export const FIGMA_ADMIN_AVATARS = {
  sidebar: DEFAULT_AVATARS.male,
  userDetail: DEFAULT_AVATARS.male,
} as const;

// Chat Figma fixture 同时包含男性账号头像和女性消息示例头像。
export const FIGMA_CHAT_AVATARS = {
  sidebar: DEFAULT_AVATARS.male,
  topbar: DEFAULT_AVATARS.male,
  message: DEFAULT_AVATARS.female,
} as const;

// 历史 Figma 导出的人物素材只用于设计证据，运行时不允许再次作为头像来源。
// 头像参数只要来自 Figma 资源域或本地 Figma 资源目录，就统一回退到登记的默认 SVG。
const legacyFigmaAvatarPattern = /(?:\/assets\/figma\/|figma\.com\/api\/mcp\/asset\/)/i;
const uploadedAvatarPathPattern = /^\/api\/users\/me\/avatar(?:[/?#]|$)/i;
const localPreviewPattern = /^blob:/i;

function isLegacyFigmaAvatarUrl(value: string): boolean {
  let decoded = value;
  // 最多解码三层，覆盖路由参数和缓存序列化造成的重复编码。
  for (let depth = 0; depth < 3; depth += 1) {
    if (legacyFigmaAvatarPattern.test(decoded)) return true;
    try {
      const next = decodeURIComponent(decoded);
      if (next === decoded) return false;
      decoded = next;
    } catch {
      // 非法编码的地址按普通资源处理，图片加载失败后再回退到默认头像。
      return false;
    }
  }
  return legacyFigmaAvatarPattern.test(decoded);
}

/** 只接受后端头像接口和浏览器本地预览作为用户主动上传头像。 */
function isTrustedUploadedAvatarUrl(value: string): boolean {
  return uploadedAvatarPathPattern.test(value) || localPreviewPattern.test(value);
}

function isRegisteredDefaultAvatar(value: string): boolean {
  return value === DEFAULT_AVATARS.male || value === DEFAULT_AVATARS.female;
}

export function getDefaultAvatarForGender(gender?: string): string | undefined {
  const normalized = gender?.trim().toLowerCase();
  if (normalized === '女' || normalized === 'female' || normalized === 'f') return DEFAULT_AVATARS.female;
  if (normalized === '男' || normalized === 'male' || normalized === 'm') return DEFAULT_AVATARS.male;
  return undefined;
}

export function resolveAvatarUrl(avatarUrl?: string, gender?: string): string {
  const genderDefault = getDefaultAvatarForGender(gender) ?? DEFAULT_AVATARS.male;
  const candidate = avatarUrl?.trim();
  if (!candidate) return genderDefault;
  // 历史缓存可能保留另一性别的默认 SVG，读取时必须按当前性别重新归一化。
  if (isRegisteredDefaultAvatar(candidate)) return genderDefault;
  if (!isLegacyFigmaAvatarUrl(candidate) && isTrustedUploadedAvatarUrl(candidate)) return candidate;
  // 未登记的历史人物素材、外部图片和旧缓存都不能作为默认头像继续展示。
  return genderDefault;
}
