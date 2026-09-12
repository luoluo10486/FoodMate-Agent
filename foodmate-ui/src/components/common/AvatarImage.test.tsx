import { render } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AvatarImage } from './AvatarImage';

afterEach(() => {
  vi.unstubAllEnvs();
});

describe('AvatarImage', () => {
  it('replaces legacy person assets before rendering', () => {
    const { container } = render(
      <AvatarImage avatarUrl="/legacy-assets/profile/person-avatar.png" gender="女" alt="头像" />,
    );

    expect(container.querySelector('img')).toHaveAttribute('src', '/assets/avatars/default-female.svg');
    expect(container.querySelector('img')).toHaveAttribute('data-avatar-source', 'default-female');
    expect(container.querySelector('img')).toHaveAttribute('data-avatar-contract', 'registered-default-svg');
    expect(container.querySelector('img')).toHaveAttribute('data-avatar-asset', '/assets/avatars/default-female.svg');
    expect(container.querySelector('img')).toHaveAttribute('data-avatar-kind', 'person-default');
    expect(container.querySelector('img')).toHaveAttribute('data-avatar-registered', 'true');
  });

  it('rejects encoded person asset URLs and keeps the gender-specific default', () => {
    const { container } = render(
      <AvatarImage avatarUrl="https://assets.example.com/profile%2Fperson-avatar.png" gender="male" alt="头像" />,
    );

    expect(container.querySelector('img')).toHaveAttribute('src', '/assets/avatars/default-male.svg');
    expect(container.querySelector('img')).toHaveAttribute('data-avatar-source', 'default-male');
  });

  it('does not treat authentication field icons as person avatars', () => {
    const { container } = render(
      <AvatarImage avatarUrl="/assets/figma/auth/foodmate-login-user.svg" gender="男" alt="头像" />,
    );

    expect(container.querySelector('img')).toHaveAttribute('src', '/assets/avatars/default-male.svg');
    expect(container.querySelector('img')).toHaveAttribute('data-avatar-contract', 'registered-default-svg');
  });

  it('falls back to the gender default for a persisted avatar address', () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    const { container } = render(
      <AvatarImage avatarUrl="/api/users/me/avatar" allowUploaded defaultOnly={false} gender="女" alt="头像" />,
    );
    const image = container.querySelector('img');

    expect(image).toHaveAttribute('src', '/assets/avatars/default-female.svg');
    expect(image).toHaveAttribute('data-avatar-source', 'default-female');
    expect(image).toHaveAttribute('data-avatar-registered', 'true');
  });

  it('keeps the registered default until a real temporary preview opts in', () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    const { container, rerender } = render(<AvatarImage avatarUrl="/api/users/me/avatar" gender="女" alt="头像" />);
    const image = container.querySelector('img');

    expect(image).toHaveAttribute('src', '/assets/avatars/default-female.svg');
    expect(image).toHaveAttribute('data-avatar-policy', 'default-only');

    rerender(
      <AvatarImage
        avatarUrl="blob:http://localhost/avatar-preview"
        allowUploaded
        defaultOnly={false}
        gender="女"
        alt="头像"
      />,
    );
    expect(container.querySelector('img')).toHaveAttribute('src', 'blob:http://localhost/avatar-preview');
    expect(container.querySelector('img')).toHaveAttribute('data-avatar-policy', 'uploaded-allowed');
  });

  it('only renders a temporary browser preview when the real upload flow opts in', () => {
    vi.stubEnv('VITE_AGENT_MODE', 'real');
    const { container } = render(
      <AvatarImage
        avatarUrl="blob:http://localhost/avatar-preview"
        allowUploaded
        defaultOnly={false}
        gender="女"
        alt="头像"
      />,
    );
    const image = container.querySelector('img');

    expect(image).toHaveAttribute('src', 'blob:http://localhost/avatar-preview');
    expect(image).toHaveAttribute('data-avatar-source', 'uploaded');
    expect(image).toHaveAttribute('data-avatar-kind', 'temporary-upload-preview');
    expect(image).toHaveAttribute('data-avatar-contract', 'trusted-upload');
    expect(image).toHaveAttribute('data-avatar-registered', 'false');
  });

  it('forces registered defaults in Fixture mode even when defaultOnly is omitted', () => {
    vi.stubEnv('VITE_AGENT_MODE', 'mock');
    const { container } = render(<AvatarImage avatarUrl="/api/users/me/avatar" gender="女" alt="头像" />);
    const image = container.querySelector('img');

    expect(image).toHaveAttribute('src', '/assets/avatars/default-female.svg');
    expect(image).toHaveAttribute('data-avatar-policy', 'default-only');
    expect(image).toHaveAttribute('data-avatar-contract', 'registered-default-svg');
  });

  it('replaces an unknown person URL before it reaches the DOM', () => {
    const { container } = render(<AvatarImage avatarUrl="https://cdn.example.com/person.png" gender="男" alt="头像" />);

    expect(container.querySelector('img')).toHaveAttribute('src', '/assets/avatars/default-male.svg');
    expect(container.querySelector('img')).toHaveAttribute('data-avatar-source', 'default-male');
  });

  it('keeps Fixture avatars on the registered SVG assets', () => {
    const { container } = render(
      <AvatarImage avatarUrl="https://cdn.example.com/legacy-person.png" defaultOnly gender="女" alt="头像" />,
    );
    const image = container.querySelector('img');

    expect(image).toHaveAttribute('src', '/assets/avatars/default-female.svg');
    expect(image).toHaveAttribute('data-avatar-policy', 'default-only');
    expect(image).toHaveAttribute('data-avatar-source', 'default-female');
    expect(image).toHaveAttribute('data-avatar-contract', 'registered-default-svg');
    expect(image).toHaveAttribute('data-avatar-registered', 'true');
  });

  it('uses the gender-matched default when a Fixture override has the wrong gender', () => {
    const { container } = render(
      <AvatarImage avatarUrl="/assets/avatars/default-female.svg" defaultOnly gender="男" alt="头像" />,
    );

    expect(container.querySelector('img')).toHaveAttribute('src', '/assets/avatars/default-male.svg');
  });

  it('always uses a registered default for a design Chat fixture', () => {
    const { container } = render(
      <AvatarImage avatarUrl="/legacy-assets/chat/person-avatar.png" defaultOnly gender="男" alt="头像" />,
    );
    const image = container.querySelector('img');

    expect(image).toHaveAttribute('src', '/assets/avatars/default-male.svg');
    expect(image).toHaveAttribute('data-avatar-policy', 'default-only');
    expect(image).toHaveAttribute('data-avatar-asset', '/assets/avatars/default-male.svg');
    expect(image?.getAttribute('src')).not.toContain('/legacy-assets/');
  });
});
