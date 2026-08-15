export const DEFAULT_AVATARS = {
  male: '/assets/avatars/default-male.svg',
  female: '/assets/avatars/default-female.svg',
} as const;

export function getDefaultAvatarForGender(gender?: string): string | undefined {
  const normalized = gender?.trim().toLowerCase();
  if (normalized === '女' || normalized === 'female' || normalized === 'f') return DEFAULT_AVATARS.female;
  if (normalized === '男' || normalized === 'male' || normalized === 'm') return DEFAULT_AVATARS.male;
  return undefined;
}

export function resolveAvatarUrl(avatarUrl?: string, gender?: string): string | undefined {
  return avatarUrl?.trim() || getDefaultAvatarForGender(gender);
}
