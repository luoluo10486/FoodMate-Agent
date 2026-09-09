import { useState, type ImgHTMLAttributes } from 'react';
import {
  DEFAULT_AVATARS,
  getDefaultAvatarForGender,
  getAvatarSourceKind,
  isRegisteredDefaultAvatar,
  resolveAvatarUrl,
} from '../../lib/avatar';

type AvatarImageProps = Omit<ImgHTMLAttributes<HTMLImageElement>, 'src'> & {
  avatarUrl?: string;
  gender?: string;
  /** Fixture 头像只允许使用项目登记的男女默认 SVG。 */
  defaultOnly?: boolean;
};

/**
 * 统一渲染用户头像。
 * Fixture 模式只使用与性别匹配的登记 SVG，真实模式只展示后端头像接口返回的上传头像或本地预览。
 */
export function AvatarImage({ avatarUrl, gender, defaultOnly = false, onError, ...props }: AvatarImageProps) {
  // Fixture 模式全局禁止人物上传图，防止遗漏 defaultOnly 的调用点重新加载历史真人素材。
  const fixtureMode = import.meta.env.VITE_AGENT_MODE !== 'real';
  const effectiveDefaultOnly = defaultOnly || fixtureMode;
  const avatarKey = `${avatarUrl ?? ''}\u0000${gender ?? ''}\u0000${effectiveDefaultOnly ? 'default-only' : 'uploaded-allowed'}`;
  const [failure, setFailure] = useState<{ key: string; failed: boolean }>({ key: avatarKey, failed: false });
  // 按输入签名派生失败状态，地址或性别变化后无需通过 Effect 触发二次渲染。
  const failed = failure.key === avatarKey && failure.failed;

  const genderDefault = getDefaultAvatarForGender(gender) ?? DEFAULT_AVATARS.male;
  const source = resolveAvatarUrl(effectiveDefaultOnly || failed ? undefined : avatarUrl, gender);
  // Fixture/默认头像不接受调用方覆盖，避免男性账号展示女性头像或反之。
  const displaySource = effectiveDefaultOnly || failed ? genderDefault : source;
  const sourceKind = getAvatarSourceKind(displaySource);
  const isRegisteredDefault = isRegisteredDefaultAvatar(displaySource);

  return (
    <img
      {...props}
      src={displaySource}
      data-avatar-source={sourceKind}
      data-avatar-policy={effectiveDefaultOnly ? 'default-only' : 'uploaded-allowed'}
      data-avatar-asset={displaySource}
      data-avatar-contract={isRegisteredDefault ? 'registered-default-svg' : 'trusted-upload'}
      data-avatar-registered={isRegisteredDefault ? 'true' : 'false'}
      onError={(event) => {
        setFailure({ key: avatarKey, failed: true });
        onError?.(event);
      }}
    />
  );
}
