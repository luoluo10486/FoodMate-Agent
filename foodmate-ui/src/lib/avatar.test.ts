import { describe, expect, it } from 'vitest';
import {
  DEFAULT_AVATARS,
  FIXTURE_ADMIN_AVATARS,
  FIXTURE_CHAT_AVATARS,
  FIXTURE_KNOWLEDGE_AVATARS,
  FIXTURE_PROFILE_AVATARS,
  FIXTURE_WORKSPACE_AVATARS,
  REGISTERED_DEFAULT_AVATARS,
  getDefaultAvatarForGender,
  getAvatarSourceKind,
  isRegisteredDefaultAvatar,
  resolveAvatarUrl,
} from './avatar';

describe('avatar defaults', () => {
  it('locks the default whitelist to the two supplied gender SVGs', () => {
    expect(REGISTERED_DEFAULT_AVATARS).toEqual([DEFAULT_AVATARS.male, DEFAULT_AVATARS.female]);
    expect(REGISTERED_DEFAULT_AVATARS).toHaveLength(2);
    expect(REGISTERED_DEFAULT_AVATARS.every(isRegisteredDefaultAvatar)).toBe(true);
  });

  it('maps male and female gender values to the supplied assets', () => {
    expect(getDefaultAvatarForGender('男')).toBe(DEFAULT_AVATARS.male);
    expect(getDefaultAvatarForGender('female')).toBe(DEFAULT_AVATARS.female);
  });

  it('exposes only the two registered SVG assets as default source kinds', () => {
    expect(getAvatarSourceKind(DEFAULT_AVATARS.male)).toBe('default-male');
    expect(getAvatarSourceKind(DEFAULT_AVATARS.female)).toBe('default-female');
    expect(getAvatarSourceKind('/api/users/me/avatar')).toBe('uploaded');
  });

  it('does not guess an avatar for an unset gender and preserves trusted uploaded avatars', () => {
    expect(getDefaultAvatarForGender('-')).toBeUndefined();
    expect(resolveAvatarUrl('/api/users/me/avatar', '女')).toBe('/api/users/me/avatar');
    expect(resolveAvatarUrl('/api/users/me/avatar?download=1', '男')).toBe('/api/users/me/avatar?download=1');
    expect(resolveAvatarUrl('blob:http://localhost/avatar-preview', '女')).toBe('blob:http://localhost/avatar-preview');
    expect(resolveAvatarUrl('', '女')).toBe(DEFAULT_AVATARS.female);
    expect(resolveAvatarUrl('', '-')).toBe(DEFAULT_AVATARS.male);
  });

  it('normalizes a stale registered default to the current gender', () => {
    expect(resolveAvatarUrl(DEFAULT_AVATARS.female, '男')).toBe(DEFAULT_AVATARS.male);
    expect(resolveAvatarUrl(DEFAULT_AVATARS.male, '女')).toBe(DEFAULT_AVATARS.female);
    expect(resolveAvatarUrl(DEFAULT_AVATARS.female, '-')).toBe(DEFAULT_AVATARS.male);
  });

  it('replaces untrusted legacy image URLs with the gender-specific default', () => {
    expect(resolveAvatarUrl('/uploads/profile.png', '女')).toBe(DEFAULT_AVATARS.female);
    expect(resolveAvatarUrl('https://cdn.example.com/person.png', '男')).toBe(DEFAULT_AVATARS.male);
    expect(resolveAvatarUrl('/legacy-assets/profile/person-avatar.png', '女')).toBe(DEFAULT_AVATARS.female);
    expect(resolveAvatarUrl('/legacy-assets/chat/person-avatar.png', '男')).toBe(DEFAULT_AVATARS.male);
  });

  it('replaces external person assets before they reach the DOM', () => {
    expect(resolveAvatarUrl('https://assets.example.com/profile/person-avatar.png?cache=old', 'female')).toBe(
      DEFAULT_AVATARS.female,
    );
    expect(resolveAvatarUrl('https://assets.example.com/chat/person-avatar.png', 'male')).toBe(DEFAULT_AVATARS.male);
  });

  it('replaces URL-encoded person asset paths before they reach the DOM', () => {
    expect(resolveAvatarUrl('https://assets.example.com/profile%2Fperson-avatar.png', 'female')).toBe(
      DEFAULT_AVATARS.female,
    );
    expect(resolveAvatarUrl('/legacy-assets%252Fprofile%252Fperson-avatar.png', 'female')).toBe(DEFAULT_AVATARS.female);
  });

  it('uses the supplied SVG assets for all Figma fixture avatars', () => {
    const fixtureAvatars = [
      ...Object.values(FIXTURE_WORKSPACE_AVATARS),
      ...Object.values(FIXTURE_KNOWLEDGE_AVATARS),
      ...Object.values(FIXTURE_PROFILE_AVATARS),
      ...Object.values(FIXTURE_ADMIN_AVATARS),
      FIXTURE_CHAT_AVATARS.sidebar,
      FIXTURE_CHAT_AVATARS.topbar,
      FIXTURE_CHAT_AVATARS.message,
      FIXTURE_CHAT_AVATARS.agentStateMessage,
    ];

    expect(fixtureAvatars.every(isRegisteredDefaultAvatar)).toBe(true);
    expect(fixtureAvatars.filter((avatar) => avatar === DEFAULT_AVATARS.male).length).toBeGreaterThan(0);
    expect(FIXTURE_CHAT_AVATARS.message).toBe(DEFAULT_AVATARS.female);
    expect(FIXTURE_CHAT_AVATARS.agentStateMessage).toBe(DEFAULT_AVATARS.male);
  });
});
