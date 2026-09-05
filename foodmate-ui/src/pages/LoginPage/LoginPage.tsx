import { useState } from 'react';
import { useRef } from 'react';
import type { FormEvent } from 'react';
import { useGSAP } from '@gsap/react';
import gsap from 'gsap';
import { CustomEase } from 'gsap/CustomEase';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { notify } from '../../lib/notice';
import { isVisualQaEnabled } from '../../lib/visualQa';
import { getLoginDefaults, login } from '../../services/authService';
import styles from './LoginPage.module.css';

gsap.registerPlugin(CustomEase);

const FIGMA_LOGIN_CUBIC_EASE = CustomEase.create('foodmate-login-figma-cubic', 'M0,0 C0.16,1 0.3,1 1,1');
const FIGMA_LOGIN_SCALE_EASE = CustomEase.create('foodmate-login-figma-scale', 'M0,0 C0,0 0.2,1 1,1');
const FIGMA_LOGIN_SPRING_EASE = (progress: number) =>
  1 - Math.exp(-progress * 7.4426) * (Math.cos(progress * 10.5254) + 0.7071 * Math.sin(progress * 10.5254));

type LoginValues = {
  username: string;
  password: string;
  rememberMe: boolean;
};

type LoginState =
  | 'default'
  | 'submitting'
  | 'field-error'
  | 'credential-error'
  | 'account-locked'
  | 'account-disabled'
  | 'service-unavailable';

function loginAsset(state: LoginState, name: 'user' | 'lock' | 'eye' | 'line') {
  const suffix =
    state === 'submitting'
      ? '-submitting'
      : state === 'field-error'
        ? '-field-error'
        : state === 'credential-error'
          ? '-credential-error'
          : state === 'account-locked'
            ? '-account-locked'
            : state === 'account-disabled'
              ? '-account-disabled'
              : state === 'service-unavailable'
                ? '-service-unavailable'
                : '';
  return `/assets/figma/auth/foodmate-login${suffix}-${name}.svg`;
}

function loginLeafAsset(state: LoginState) {
  if (state === 'submitting') return '/assets/figma/auth/foodmate-login-submitting-leaf.svg';
  if (state === 'field-error') return '/assets/figma/auth/foodmate-login-field-error-leaf.svg';
  if (state === 'credential-error') return '/assets/figma/auth/foodmate-login-credential-error-leaf.svg';
  if (state === 'account-locked') return '/assets/figma/auth/foodmate-login-account-locked-leaf.svg';
  if (state === 'account-disabled') return '/assets/figma/auth/foodmate-login-account-disabled-leaf.svg';
  if (state === 'service-unavailable') return '/assets/figma/auth/foodmate-login-service-unavailable-leaf.svg';
  return '/assets/figma/auth/foodmate-leaf.svg';
}

function loginAlertAsset(state: LoginState) {
  if (state === 'credential-error') return '/assets/figma/auth/foodmate-login-credential-error-alert.svg';
  if (state === 'account-locked') return '/assets/figma/auth/foodmate-login-account-locked-alert.svg';
  if (state === 'account-disabled') return '/assets/figma/auth/foodmate-login-account-disabled-alert.svg';
  if (state === 'service-unavailable') return '/assets/figma/auth/foodmate-login-service-unavailable-info.svg';
  return undefined;
}

const loginStates = new Set<LoginState>([
  'default',
  'submitting',
  'field-error',
  'credential-error',
  'account-locked',
  'account-disabled',
  'service-unavailable',
]);

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className={styles.field}>
      <span className={styles.fieldLabel}>{label}</span>
      {children}
    </label>
  );
}

function LoginBrand({ state }: { state: LoginState }) {
  return (
    <div className={styles.loginBrand} data-node-id="647:234">
      {state === 'default' ? (
        <span className={styles.logoPlaceholder} aria-hidden="true" data-login-motion="logo" data-node-id="660:212" />
      ) : (
        <span className={styles.loginMark} aria-hidden="true" data-login-motion="logo">
          <img src={loginLeafAsset(state)} alt="" />
        </span>
      )}
      <span className={styles.wordmark} data-login-motion="wordmark" data-node-id="647:236">
        <span>Food</span>
        <span>Mate</span>
      </span>
    </div>
  );
}

export function LoginPage() {
  const navigate = useNavigate();
  const pageRef = useRef<HTMLElement>(null);
  const [searchParams] = useSearchParams();
  const visualQaEnabled = isVisualQaEnabled(searchParams.toString());
  const defaults = getLoginDefaults();
  const requestedState = searchParams.get('state') as LoginState | null;
  const state = requestedState && loginStates.has(requestedState) ? requestedState : 'default';
  const [submitting, setSubmitting] = useState(false);
  const visualState: LoginState = state === 'default' && submitting ? 'submitting' : state;
  const [loginValues, setLoginValues] = useState<LoginValues>(() => {
    if (state === 'submitting') return { ...defaults, username: 'alex@foodmate.com', password: 'password' };
    if (state === 'credential-error') return { ...defaults, username: 'wrong@foodmate.com', password: 'password' };
    if (state === 'account-locked') return { ...defaults, username: 'locked@foodmate.com', password: 'password' };
    if (state === 'account-disabled') return { ...defaults, username: 'disabled@foodmate.com', password: 'password' };
    return defaults;
  });
  const [showPassword, setShowPassword] = useState(false);

  useGSAP(
    () => {
      const page = pageRef.current;
      if (
        visualQaEnabled ||
        state !== 'default' ||
        !page ||
        window.matchMedia('(prefers-reduced-motion: reduce)').matches
      )
        return;

      // 按 Figma 节点 647:214 的 4.5 秒循环时间线同步背景、品牌和表单的出现节奏。
      const timeline = gsap.timeline({ repeat: -1, repeatDelay: 0 });
      timeline
        .set('[data-login-motion="diagonal"]', { x: -1800, y: -1000 })
        .set('[data-login-motion="logo"]', { rotate: 0, scaleX: 12, scaleY: 12, x: 90, y: 207 })
        .set('[data-login-motion="wordmark"]', { autoAlpha: 0, y: 8 })
        .set('[data-login-motion="welcome"]', { autoAlpha: 0, y: 15 })
        .set('[data-login-motion="fields"]', { autoAlpha: 0, y: 15 })
        .set('[data-login-motion="submit"]', { autoAlpha: 0, y: 12 })
        .set('[data-login-motion="divider"]', { autoAlpha: 0 })
        .set('[data-login-motion="signup"]', { autoAlpha: 0, y: 8 })
        .to('[data-login-motion="logo"]', { rotate: 120, duration: 0.5, ease: 'none' }, 0)
        .to('[data-login-motion="logo"]', { rotate: 240, duration: 0.5, ease: 'none' }, 0.5)
        .to('[data-login-motion="logo"]', { rotate: 360, duration: 0.5, ease: FIGMA_LOGIN_CUBIC_EASE }, 1)
        .to('[data-login-motion="logo"]', { rotate: 360, duration: 3, ease: 'none' }, 1.5)
        .to('[data-login-motion="logo"]', { scaleX: 12, scaleY: 12, x: 90, y: 207, duration: 2, ease: 'none' }, 0)
        .to(
          '[data-login-motion="logo"]',
          { scaleX: 1, scaleY: 1, x: 0, y: 0, duration: 0.8, ease: FIGMA_LOGIN_SCALE_EASE },
          2,
        )
        .to('[data-login-motion="logo"]', { scaleX: 1, scaleY: 1, x: 0, y: 0, duration: 1.7, ease: 'none' }, 2.8)
        .to('[data-login-motion="wordmark"]', { autoAlpha: 1, y: 0, duration: 0.4, ease: FIGMA_LOGIN_CUBIC_EASE }, 2.8)
        .to('[data-login-motion="welcome"]', { autoAlpha: 1, y: 0, duration: 0.4, ease: FIGMA_LOGIN_CUBIC_EASE }, 3.2)
        .to('[data-login-motion="fields"]', { autoAlpha: 1, y: 0, duration: 0.4, ease: FIGMA_LOGIN_CUBIC_EASE }, 3.4)
        .to('[data-login-motion="submit"]', { autoAlpha: 1, y: 0, duration: 0.4, ease: FIGMA_LOGIN_CUBIC_EASE }, 3.6)
        .to('[data-login-motion="divider"]', { autoAlpha: 1, duration: 0.3, ease: FIGMA_LOGIN_CUBIC_EASE }, 3.8)
        .to('[data-login-motion="signup"]', { autoAlpha: 1, y: 0, duration: 0.4, ease: FIGMA_LOGIN_CUBIC_EASE }, 3.9)
        .to('[data-login-motion="diagonal"]', { x: 0, y: 0, duration: 0.4, ease: FIGMA_LOGIN_SPRING_EASE }, 2.3)
        .to('[data-login-motion="diagonal"]', { x: 0, y: 0, duration: 1.8, ease: 'none' }, 2.7);
    },
    { scope: pageRef, dependencies: [state, visualQaEnabled] },
  );

  const handleLogin = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (state !== 'default') return;
    setSubmitting(true);
    try {
      await login(loginValues);
      navigate('/');
    } catch (error) {
      notify(error instanceof Error ? error.message : '登录失败', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main
      className={`${styles.authPage} ${styles['authPage-login']} ${state === 'submitting' ? styles.authPageLoginSubmitting : state === 'field-error' ? styles.authPageLoginFieldError : state === 'credential-error' ? styles.authPageLoginCredentialError : state === 'account-locked' ? styles.authPageLoginAccountLocked : state === 'account-disabled' ? styles.authPageLoginAccountDisabled : state === 'service-unavailable' ? styles.authPageLoginServiceUnavailable : ''}`}
      ref={pageRef}
    >
      <div className={styles.authDiagonal} aria-hidden="true" data-login-motion="diagonal" />
      <section className={styles.authCard} aria-label="欢迎回来">
        <div className={styles.brand}>
          <LoginBrand state={visualState} />
          <div className={styles.welcome} data-login-motion="welcome">
            <p>欢迎回来</p>
            <span>
              {visualState === 'submitting'
                ? '正在安全连接，请稍候'
                : visualState === 'field-error'
                  ? '请检查您填写的信息'
                  : visualState === 'account-locked'
                    ? '您的账号安全受到保护'
                    : visualState === 'account-disabled'
                      ? '账号状态发生变更'
                      : visualState === 'service-unavailable'
                        ? '服务器正在进行系统优化'
                        : '让我们开始今天的营养管理'}
            </span>
          </div>
        </div>

        <form className={styles.form} onSubmit={handleLogin}>
          {state === 'credential-error' ? (
            <div className={`${styles.loginAlert} ${styles.loginAlertError}`} role="alert">
              <img src={loginAlertAsset(state)} alt="" aria-hidden="true" />
              <strong>邮箱或密码错误，请重试</strong>
            </div>
          ) : null}
          {state === 'account-locked' ? (
            <div className={`${styles.loginAlert} ${styles.loginAlertWarning}`} role="alert">
              <img src={loginAlertAsset(state)} alt="" aria-hidden="true" />
              <div>
                <strong>账号已锁定</strong>
                <span>由于多次登录失败，你的账号已被暂时锁定。请 30 分钟后重试或联系客服。</span>
              </div>
            </div>
          ) : null}
          {state === 'account-disabled' ? (
            <div className={`${styles.loginAlert} ${styles.loginAlertError}`} role="alert">
              <img src={loginAlertAsset(state)} alt="" aria-hidden="true" />
              <div>
                <strong>账号已禁用</strong>
                <span>你的账号已被管理员禁用。如有疑问，请联系客服支持。</span>
                <Button className={styles.loginAlertAction} variant="ghost" type="button" onClick={() => undefined}>
                  联系客服
                </Button>
              </div>
            </div>
          ) : null}
          {state === 'service-unavailable' ? (
            <div className={`${styles.loginAlert} ${styles.loginAlertInfo}`} role="alert">
              <img src={loginAlertAsset(state)} alt="" aria-hidden="true" />
              <div>
                <strong>服务暂时不可用</strong>
                <span>系统维护中，请稍后再试。</span>
                <Button
                  className={styles.loginAlertAction}
                  variant="ghost"
                  type="button"
                  onClick={() => window.location.reload()}
                >
                  刷新页面
                </Button>
              </div>
            </div>
          ) : null}
          <div className={styles.loginFields} data-login-motion="fields">
            <Field label="">
              <Input
                className={`${styles.figmaInput} ${state === 'field-error' ? styles.figmaInputError : ''}`}
                name="username"
                autoComplete="username"
                placeholder={
                  state === 'field-error'
                    ? 'invalid-email'
                    : state === 'credential-error'
                      ? 'wrong@foodmate.com'
                      : state === 'account-locked'
                        ? 'locked@foodmate.com'
                        : state === 'account-disabled'
                          ? 'disabled@foodmate.com'
                          : '邮箱地址'
                }
                aria-label="邮箱地址"
                leadingIcon={<img src={loginAsset(visualState, 'user')} alt="" />}
                value={loginValues.username}
                required
                onChange={(event) => setLoginValues((current) => ({ ...current, username: event.target.value }))}
              />
              {state === 'field-error' ? <span className={styles.loginFieldError}>请输入有效的邮箱地址</span> : null}
            </Field>
            <Field label="">
              <Input
                className={`${styles.figmaInput} ${state === 'field-error' ? styles.figmaInputError : ''}`}
                name="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                placeholder={
                  state === 'field-error'
                    ? '密码'
                    : state === 'credential-error' || state === 'account-locked' || state === 'account-disabled'
                      ? '••••••••'
                      : '密码'
                }
                aria-label="密码"
                leadingIcon={<img src={loginAsset(visualState, 'lock')} alt="" />}
                trailingAction={
                  <Button
                    className={styles.passwordToggle}
                    variant="ghost"
                    size="icon"
                    type="button"
                    aria-label={showPassword ? '隐藏密码' : '显示密码'}
                    onClick={() => setShowPassword((value) => !value)}
                  >
                    <img src={loginAsset(visualState, 'eye')} alt="" />
                  </Button>
                }
                value={loginValues.password}
                required
                onChange={(event) => setLoginValues((current) => ({ ...current, password: event.target.value }))}
              />
              {state === 'field-error' ? <span className={styles.loginFieldError}>密码不能为空</span> : null}
            </Field>
            <div className={styles.options}>
              <Button
                className={styles.forgotButton}
                variant="ghost"
                type="button"
                onClick={() => navigate('/forgot-password')}
              >
                忘记密码？
              </Button>
            </div>
          </div>
          <Button
            className={`${styles.primaryAction} ${['submitting', 'account-locked', 'account-disabled', 'service-unavailable'].includes(visualState) ? styles.primaryActionDisabled : ''}`}
            type="submit"
            disabled={
              submitting ||
              ['submitting', 'account-locked', 'account-disabled', 'service-unavailable'].includes(visualState)
            }
            data-node-id="647:251"
            data-login-motion="submit"
          >
            {visualState === 'submitting' ? (
              <>
                <img
                  className={styles.loginSpinner}
                  src="/assets/figma/auth/foodmate-login-loader.svg"
                  alt=""
                  aria-hidden="true"
                />
                登录中...
              </>
            ) : visualState === 'account-locked' ? (
              '登录已禁用'
            ) : visualState === 'account-disabled' ? (
              '账号不可用'
            ) : visualState === 'service-unavailable' ? (
              '系统维护中'
            ) : (
              '登录'
            )}
          </Button>
        </form>

        <div className={styles.actions}>
          <div className={styles.divider} aria-hidden="true" data-login-motion="divider">
            <img src={loginAsset(visualState, 'line')} alt="" />
            <span>或者</span>
            <img src={loginAsset(visualState, 'line')} alt="" />
          </div>
          <div className={styles.signupRow} data-login-motion="signup">
            <span className={styles.signupPrompt}>没有账号？</span>
            <Button
              className={styles.signupButton}
              variant="outline"
              type="button"
              onClick={() => navigate('/register')}
            >
              注册
            </Button>
          </div>
        </div>
      </section>
    </main>
  );
}
