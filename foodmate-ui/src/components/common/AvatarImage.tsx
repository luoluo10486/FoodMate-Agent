import { useState, type ImgHTMLAttributes } from 'react';
import { DEFAULT_AVATARS, resolveAvatarUrl } from '../../lib/avatar';

type AvatarImageProps = Omit<ImgHTMLAttributes<HTMLImageElement>, 'src'> & {
  avatarUrl?: string;
  gender?: string;
};

/**
 * 统一渲染用户头像，避免历史 Figma 人物素材或失效远程地址进入页面。
 * 有效的真实用户头像仍优先展示，加载失败后回退到项目登记的默认 SVG。
 */
export function AvatarImage({ avatarUrl, gender, onError, ...props }: AvatarImageProps) {
  const avatarKey = `${avatarUrl ?? ''}\u0000${gender ?? ''}`;
  const [failure, setFailure] = useState<{ key: string; failed: boolean }>({ key: avatarKey, failed: false });
  // 按输入签名派生失败状态，地址或性别变化后无需通过 Effect 触发二次渲染。
  const failed = failure.key === avatarKey && failure.failed;

  const source = resolveAvatarUrl(failed ? undefined : avatarUrl, gender);
  const sourceKind =
    source === DEFAULT_AVATARS.female
      ? 'default-female'
      : source === DEFAULT_AVATARS.male
        ? 'default-male'
        : 'uploaded';

  return (
    <img
      {...props}
      src={source}
      data-avatar-source={sourceKind}
      onError={(event) => {
        setFailure({ key: avatarKey, failed: true });
        onError?.(event);
      }}
    />
  );
}
