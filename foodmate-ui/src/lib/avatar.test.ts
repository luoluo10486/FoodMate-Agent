import { describe, expect, it } from 'vitest';
import { DEFAULT_AVATARS, getDefaultAvatarForGender, resolveAvatarUrl } from './avatar';

describe('avatar defaults', () => {
  it('maps male and female gender values to the supplied assets', () => {
    expect(getDefaultAvatarForGender('男')).toBe(DEFAULT_AVATARS.male);
    expect(getDefaultAvatarForGender('female')).toBe(DEFAULT_AVATARS.female);
  });

  it('does not guess an avatar for an unset gender and preserves uploaded avatars', () => {
    expect(getDefaultAvatarForGender('-')).toBeUndefined();
    expect(resolveAvatarUrl('/uploads/profile.png', '女')).toBe('/uploads/profile.png');
    expect(resolveAvatarUrl('', '女')).toBe(DEFAULT_AVATARS.female);
  });
});
