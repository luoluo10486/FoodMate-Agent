export const DEFAULT_AVATARS = Object.freeze({
  male: '/assets/avatars/default-male.svg',
  female: '/assets/avatars/default-female.svg',
} as const);

/** 运行时默认头像白名单，所有 Fixture 人物头像必须从这里选择。 */
export const REGISTERED_DEFAULT_AVATARS = Object.freeze([DEFAULT_AVATARS.male, DEFAULT_AVATARS.female]);

export type AvatarSourceKind = 'default-male' | 'default-female' | 'uploaded';

// 所有 Figma Fixture 的示例账号共用同一份登记头像，避免侧栏、顶栏和消息头像发生漂移。
export const FIXTURE_ACCOUNT_AVATAR = DEFAULT_AVATARS.male;

// Figma 工作台示例账号使用项目登记的男性默认头像，避免运行时加载真人素材。
export const FIXTURE_WORKSPACE_AVATARS = {
  sidebar: FIXTURE_ACCOUNT_AVATAR,
  topbar: FIXTURE_ACCOUNT_AVATAR,
} as const;

// Knowledge Figma fixture 使用项目登记的男性默认头像。
export const FIXTURE_KNOWLEDGE_AVATARS = {
  sidebar: FIXTURE_ACCOUNT_AVATAR,
  topbar: FIXTURE_ACCOUNT_AVATAR,
} as const;

// Profile Figma fixture 的示例账号为男性，所有默认头像统一使用男性资源。
export const FIXTURE_PROFILE_AVATARS = {
  sidebar: FIXTURE_ACCOUNT_AVATAR,
  topbar: FIXTURE_ACCOUNT_AVATAR,
  main: FIXTURE_ACCOUNT_AVATAR,
} as const;

// Admin Figma fixture 的示例账号为男性，统一使用男性默认头像。
export const FIXTURE_ADMIN_AVATARS = {
  sidebar: FIXTURE_ACCOUNT_AVATAR,
  userDetail: FIXTURE_ACCOUNT_AVATAR,
} as const;

// Chat 默认 Figma fixture 的账号使用男性示例，工作区和用户消息统一使用同一头像。
export const FIXTURE_CHAT_AVATARS = {
  sidebar: FIXTURE_ACCOUNT_AVATAR,
  topbar: FIXTURE_ACCOUNT_AVATAR,
  message: FIXTURE_ACCOUNT_AVATAR,
  // 六个 Agent 状态画板的普通用户消息继续使用男性示例头像。
  agentStateMessage: FIXTURE_ACCOUNT_AVATAR,
} as const;

export const FIXTURE_CHAT_AVATAR_GENDERS = {
  defaultMessage: '男',
  agentStateMessage: '男',
  safetyDegradedMessage: '女',
} as const;

// 历史 Figma 导出的人物素材只用于设计证据，运行时不允许再次作为头像来源。
// 头像参数只要来自 Figma 资源域或本地 Figma 资源目录，就统一回退到登记的默认 SVG。
const legacyFigmaAvatarPattern = /(?:\/assets\/figma\/|figma\.com\/api\/mcp\/asset\/)/i;
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

/** 只接受用户刚选择图片后生成的浏览器临时预览，不直接展示持久化头像地址。 */
function isTemporaryUploadedAvatarUrl(value: string): boolean {
  return localPreviewPattern.test(value);
}

export function isRegisteredDefaultAvatar(value: string): boolean {
  return REGISTERED_DEFAULT_AVATARS.includes(value as (typeof REGISTERED_DEFAULT_AVATARS)[number]);
}

/** 运行时只允许登记的默认 SVG 或主动上传生成的临时预览进入头像组件。 */
export function isAllowedAvatarRuntimeSource(value: string): boolean {
  return isRegisteredDefaultAvatar(value) || isTemporaryUploadedAvatarUrl(value);
}

export function getAvatarSourceKind(value: string): AvatarSourceKind {
  if (value === DEFAULT_AVATARS.female) return 'default-female';
  if (value === DEFAULT_AVATARS.male) return 'default-male';
  if (isTemporaryUploadedAvatarUrl(value)) return 'uploaded';
  return 'default-male';
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
  if (!isLegacyFigmaAvatarUrl(candidate) && isTemporaryUploadedAvatarUrl(candidate)) return candidate;
  // 未登记的历史人物素材、持久化头像地址、外部图片和旧缓存都不能作为默认头像继续展示。
  return genderDefault;
}

/**
 * 归一化持久化账号头像。
 *
 * blob URL 只代表当前页面主动选择图片后的临时预览，不能写入或复用为登录缓存头像。
 */
export function resolvePersistedAvatarUrl(avatarUrl?: string, gender?: string): string {
  const candidate = avatarUrl?.trim();
  return candidate && isTemporaryUploadedAvatarUrl(candidate)
    ? resolveAvatarUrl(undefined, gender)
    : resolveAvatarUrl(candidate, gender);
}
