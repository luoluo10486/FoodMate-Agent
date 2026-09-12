import { useState, type ImgHTMLAttributes } from 'react';
import {
  DEFAULT_AVATARS,
  getDefaultAvatarForGender,
  getAvatarSourceKind,
  isAllowedAvatarRuntimeSource,
  isRegisteredDefaultAvatar,
  resolveAvatarUrl,
} from '../../lib/avatar';

type AvatarImageProps = Omit<ImgHTMLAttributes<HTMLImageElement>, 'src'> & {
  avatarUrl?: string;
  gender?: string;
  /** Fixture 头像只允许使用项目登记的男女默认 SVG。 */
  defaultOnly?: boolean;
  /** 仅真实模式的主动上传流程可以显式展示当前选择图片的 blob 预览，默认关闭。 */
  allowUploaded?: boolean;
};

/**
 * 统一渲染用户头像。
 * 默认只使用与性别匹配的登记 SVG；真实模式只有主动选择图片后的 blob 预览可以显式开启。
 */
export function AvatarImage({
  avatarUrl,
  gender,
  defaultOnly = false,
  allowUploaded = false,
  onError,
  ...props
}: AvatarImageProps) {
  // 默认入口禁止人物上传图，只有真实上传流程显式声明后才允许加载当前选择的本地预览。
  const fixtureMode = import.meta.env.VITE_AGENT_MODE !== 'real';
  const uploadedAllowed = allowUploaded && !defaultOnly && !fixtureMode;
  const effectiveDefaultOnly = !uploadedAllowed;
  const avatarKey = `${avatarUrl ?? ''}\u0000${gender ?? ''}\u0000${effectiveDefaultOnly ? 'default-only' : 'uploaded-allowed'}`;
  const [failure, setFailure] = useState<{ key: string; failed: boolean }>({ key: avatarKey, failed: false });
  // 按输入签名派生失败状态，地址或性别变化后无需通过 Effect 触发二次渲染。
  const failed = failure.key === avatarKey && failure.failed;

  const genderDefault = getDefaultAvatarForGender(gender) ?? DEFAULT_AVATARS.male;
  const resolvedSource = resolveAvatarUrl(effectiveDefaultOnly || failed ? undefined : avatarUrl, gender);
  // 解析层之后再次兜底，防止新增调用方绕过统一解析后把未登记人物素材送进 DOM。
  const safeSource = isAllowedAvatarRuntimeSource(resolvedSource) ? resolvedSource : genderDefault;
  // Fixture/默认头像不接受调用方覆盖，避免男性账号展示女性头像或反之。
  const displaySource = effectiveDefaultOnly || failed ? genderDefault : safeSource;
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
