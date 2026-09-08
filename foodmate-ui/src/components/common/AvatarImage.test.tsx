import { fireEvent, render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { AvatarImage } from './AvatarImage';

describe('AvatarImage', () => {
  it('replaces legacy Figma person assets before rendering', () => {
    const { container } = render(
      <AvatarImage avatarUrl="/assets/figma/profile/main-avatar.png" gender="女" alt="头像" />,
    );

    expect(container.querySelector('img')).toHaveAttribute('src', '/assets/avatars/default-female.svg');
    expect(container.querySelector('img')).toHaveAttribute('data-avatar-source', 'default-female');
    expect(container.querySelector('img')).toHaveAttribute('data-avatar-registered', 'true');
  });

  it('rejects encoded Figma asset URLs and keeps the gender-specific default', () => {
    const { container } = render(
      <AvatarImage avatarUrl="https://www.figma.com/api/mcp/asset%2Favatar-person.png" gender="male" alt="头像" />,
    );

    expect(container.querySelector('img')).toHaveAttribute('src', '/assets/avatars/default-male.svg');
    expect(container.querySelector('img')).toHaveAttribute('data-avatar-source', 'default-male');
  });

  it('falls back to the gender default when a real avatar fails to load', () => {
    const { container } = render(<AvatarImage avatarUrl="/api/users/me/avatar" gender="女" alt="头像" />);
    const image = container.querySelector('img');

    expect(image).toHaveAttribute('src', '/api/users/me/avatar');
    fireEvent.error(image!);
    expect(image).toHaveAttribute('src', '/assets/avatars/default-female.svg');
    expect(image).toHaveAttribute('data-avatar-source', 'default-female');
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
      <AvatarImage avatarUrl="/assets/figma/agent-chat/user-avatar.png" defaultOnly gender="男" alt="头像" />,
    );
    const image = container.querySelector('img');

    expect(image).toHaveAttribute('src', '/assets/avatars/default-male.svg');
    expect(image).toHaveAttribute('data-avatar-policy', 'default-only');
    expect(image?.getAttribute('src')).not.toContain('/assets/figma/');
  });
});
