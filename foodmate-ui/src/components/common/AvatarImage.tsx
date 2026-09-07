import { useState, type ImgHTMLAttributes } from 'react';
import { DEFAULT_AVATARS, resolveAvatarUrl } from '../../lib/avatar';

type AvatarImageProps = Omit<ImgHTMLAttributes<HTMLImageElement>, 'src'> & {
  avatarUrl?: string;
  gender?: string;
  /** Fixture 头像只允许使用项目登记的男女默认 SVG。 */
  defaultOnly?: boolean;
};

/**
 * 统一渲染用户头像。
 * Fixture 模式只使用登记的默认 SVG，真实模式只展示后端头像接口返回的上传头像或本地预览。
 */
export function AvatarImage({ avatarUrl, gender, defaultOnly = false, onError, ...props }: AvatarImageProps) {
  const avatarKey = `${avatarUrl ?? ''}\u0000${gender ?? ''}\u0000${defaultOnly ? 'default-only' : 'uploaded-allowed'}`;
  const [failure, setFailure] = useState<{ key: string; failed: boolean }>({ key: avatarKey, failed: false });
  // 按输入签名派生失败状态，地址或性别变化后无需通过 Effect 触发二次渲染。
  const failed = failure.key === avatarKey && failure.failed;

  const source = resolveAvatarUrl(defaultOnly || failed ? undefined : avatarUrl, gender);
  const canonicalSource =
    avatarUrl === DEFAULT_AVATARS.female || avatarUrl === DEFAULT_AVATARS.male ? avatarUrl : source;
  const displaySource = defaultOnly && !failed ? canonicalSource : source;
  const sourceKind =
    displaySource === DEFAULT_AVATARS.female
      ? 'default-female'
      : displaySource === DEFAULT_AVATARS.male
        ? 'default-male'
        : 'uploaded';

  return (
    <img
      {...props}
      src={displaySource}
      data-avatar-source={sourceKind}
      data-avatar-policy={defaultOnly ? 'default-only' : 'uploaded-allowed'}
      onError={(event) => {
        setFailure({ key: avatarKey, failed: true });
        onError?.(event);
      }}
    />
  );
}
