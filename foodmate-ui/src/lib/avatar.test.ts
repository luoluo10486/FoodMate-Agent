import { describe, expect, it } from 'vitest';
import {
  DEFAULT_AVATARS,
  FIXTURE_ADMIN_AVATARS,
  FIXTURE_CHAT_AVATARS,
  FIXTURE_KNOWLEDGE_AVATARS,
  FIXTURE_PROFILE_AVATARS,
  FIXTURE_WORKSPACE_AVATARS,
  getDefaultAvatarForGender,
  resolveAvatarUrl,
} from './avatar';

describe('avatar defaults', () => {
  it('maps male and female gender values to the supplied assets', () => {
    expect(getDefaultAvatarForGender('男')).toBe(DEFAULT_AVATARS.male);
    expect(getDefaultAvatarForGender('female')).toBe(DEFAULT_AVATARS.female);
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
    expect(resolveAvatarUrl('/.qa/figma-pixel-acceptance/legacy-avatars/profile/main-avatar.png', '女')).toBe(
      DEFAULT_AVATARS.female,
    );
  });

  it('replaces legacy Figma person assets with the registered gender defaults', () => {
    const legacySources = [
      '/assets/figma/profile/main-avatar.png',
      '/assets/figma/agent-chat/user-avatar.png',
      '/assets/figma/admin/user-detail-avatar.png',
      '/assets/figma/workspace/home-sidebar-avatar.png',
      '/assets/figma/workspace/home-topbar-avatar.png',
      '/assets/figma/workspace/legacy-person-image.png',
    ];

    expect(resolveAvatarUrl(legacySources[0], '女')).toBe(DEFAULT_AVATARS.female);
    expect(resolveAvatarUrl(legacySources[1], '男')).toBe(DEFAULT_AVATARS.male);
    expect(resolveAvatarUrl(legacySources[2], '-')).toBe(DEFAULT_AVATARS.male);
    expect(legacySources.every((source) => resolveAvatarUrl(source) === DEFAULT_AVATARS.male)).toBe(true);
  });

  it('replaces absolute Figma MCP person assets before they reach the DOM', () => {
    expect(resolveAvatarUrl('https://www.figma.com/api/mcp/asset/abc123/profile-avatar.png?cache=old', 'female')).toBe(
      DEFAULT_AVATARS.female,
    );
    expect(resolveAvatarUrl('https://www.figma.com/api/mcp/asset/abc123/image.png', 'male')).toBe(DEFAULT_AVATARS.male);
  });

  it('replaces URL-encoded Figma asset paths before they reach the DOM', () => {
    expect(resolveAvatarUrl('https://www.figma.com/api/mcp/asset%2Favatar-person.png', 'female')).toBe(
      DEFAULT_AVATARS.female,
    );
    expect(resolveAvatarUrl('/assets%252Ffigma%252Fprofile%252Favatar-person.png', 'female')).toBe(
      DEFAULT_AVATARS.female,
    );
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
    ];

    expect(fixtureAvatars.every((avatar) => Object.values(DEFAULT_AVATARS).includes(avatar))).toBe(true);
    expect(fixtureAvatars.filter((avatar) => avatar === DEFAULT_AVATARS.male).length).toBeGreaterThan(0);
    expect(FIXTURE_CHAT_AVATARS.message).toBe(DEFAULT_AVATARS.female);
  });
});
