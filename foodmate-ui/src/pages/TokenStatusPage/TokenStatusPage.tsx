import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button } from '../../components/ui/button';
import { AuthBrand, AuthShell } from '../Auth/AuthVisual';
import styles from '../LoginPage/LoginPage.module.css';

type TokenState = 'invalid' | 'expired' | 'used';

const tokenStates: Record<TokenState, { title: string; description: string; action: string }> = {
  invalid: {
    title: '链接无效',
    description: '该重置链接无效或已损坏。请重新申请密码重置。',
    action: '重新发送重置邮件',
  },
  expired: {
    title: '链接已过期',
    description: '该重置链接已超过 24 小时有效期。请重新申请密码重置。',
    action: '重新发送重置邮件',
  },
  used: {
    title: '链接已使用',
    description: '该重置链接已被使用过。如果不是你本人操作，请立即联系客服。',
    action: '重新发送重置邮件',
  },
};

const tokenIcons: Record<TokenState, string> = {
  invalid: '/assets/figma/auth/token-invalid-alert-triangle.svg',
  expired: '/assets/figma/auth/token-expired-clock.svg',
  used: '/assets/figma/auth/token-used-info.svg',
};

const tokenBrandIcons: Record<TokenState, string> = {
  invalid: '/assets/figma/auth/token-invalid-fork-knife.svg',
  expired: '/assets/figma/auth/token-expired-fork-knife.svg',
  used: '/assets/figma/auth/token-used-fork-knife.svg',
};

export function TokenStatusPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const requested = params.get('state') as TokenState | null;
  const state: TokenState = requested && requested in tokenStates ? requested : 'invalid';
  const copy = tokenStates[state];

  return (
    <AuthShell variant="token">
      <section className={styles.tokenCard} aria-labelledby="token-title">
        <AuthBrand title="" subtitle="" mark="utensils" iconSrc={tokenBrandIcons[state]} />
        <div className={styles.tokenContent}>
          <div className={`${styles.tokenIcon} ${styles[`tokenIcon-${state}`]}`} aria-hidden="true">
            <img src={tokenIcons[state]} alt="" />
          </div>
          <div className={styles.tokenMessage}>
            <h1 id="token-title">{copy.title}</h1>
            <p>{copy.description}</p>
          </div>
        </div>
        <div className={styles.tokenActions}>
          <Button className={styles.authPrimary} type="button" onClick={() => navigate('/forgot-password')}>
            {copy.action}
          </Button>
          {state === 'used' ? (
            <Button className={styles.tokenSupport} variant="outline" type="button" onClick={() => undefined}>
              联系客服
            </Button>
          ) : null}
          <div className={styles.tokenBackRow}>
            <Button className={styles.authBackLink} variant="ghost" type="button" onClick={() => navigate('/login')}>
              返回登录
            </Button>
          </div>
        </div>
      </section>
    </AuthShell>
  );
}
