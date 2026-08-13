import {
  AlertCircle,
  AlertTriangle,
  Eye,
  EyeOff,
  Info,
  Leaf,
  LoaderCircle,
  LockKeyhole,
  UserRound,
} from 'lucide-react';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { notify } from '../../lib/notice';
import { getLoginDefaults, login } from '../../services/authService';
import styles from './LoginPage.module.css';

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
        <span className={styles.logoPlaceholder} aria-hidden="true" data-node-id="660:212" />
      ) : (
        <span className={styles.loginMark} aria-hidden="true">
          <Leaf />
        </span>
      )}
      <span className={styles.wordmark} data-node-id="647:236">
        <span>Food</span>
        <span>Mate</span>
      </span>
    </div>
  );
}

export function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const defaults = getLoginDefaults();
  const requestedState = searchParams.get('state') as LoginState | null;
  const state = requestedState && loginStates.has(requestedState) ? requestedState : 'default';
  const [submitting, setSubmitting] = useState(false);
  const visualState: LoginState = state === 'default' && submitting ? 'submitting' : state;
  const [loginValues, setLoginValues] = useState<LoginValues>(defaults);
  const [showPassword, setShowPassword] = useState(false);

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
    <main className={`${styles.authPage} ${styles['authPage-login']}`}>
      <div className={styles.authDiagonal} aria-hidden="true" />
      <section className={styles.authCard} aria-label="欢迎回来">
        <div className={styles.brand}>
          <LoginBrand state={visualState} />
          <div className={styles.welcome}>
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
              <AlertCircle aria-hidden="true" />
              <strong>邮箱或密码错误，请重试</strong>
            </div>
          ) : null}
          {state === 'account-locked' ? (
            <div className={`${styles.loginAlert} ${styles.loginAlertWarning}`} role="alert">
              <AlertTriangle aria-hidden="true" />
              <div>
                <strong>账号已锁定</strong>
                <span>由于多次登录失败，你的账号已被暂时锁定。请 30 分钟后重试或联系客服。</span>
              </div>
            </div>
          ) : null}
          {state === 'account-disabled' ? (
            <div className={`${styles.loginAlert} ${styles.loginAlertError}`} role="alert">
              <AlertCircle aria-hidden="true" />
              <div>
                <strong>账号已禁用</strong>
                <span>你的账号已被管理员禁用。如有疑问，请联系客服支持。</span>
                <button type="button" onClick={() => undefined}>
                  联系客服
                </button>
              </div>
            </div>
          ) : null}
          {state === 'service-unavailable' ? (
            <div className={`${styles.loginAlert} ${styles.loginAlertInfo}`} role="alert">
              <Info aria-hidden="true" />
              <div>
                <strong>服务暂时不可用</strong>
                <span>系统维护中，请稍后再试。</span>
                <button type="button" onClick={() => window.location.reload()}>
                  刷新页面
                </button>
              </div>
            </div>
          ) : null}
          <div className={styles.loginFields}>
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
                leadingIcon={<UserRound aria-hidden="true" />}
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
                leadingIcon={<LockKeyhole aria-hidden="true" />}
                trailingAction={
                  <button
                    className={styles.passwordToggle}
                    type="button"
                    aria-label={showPassword ? '隐藏密码' : '显示密码'}
                    onClick={() => setShowPassword((value) => !value)}
                  >
                    {showPassword ? <EyeOff aria-hidden="true" /> : <Eye aria-hidden="true" />}
                  </button>
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
          >
            {visualState === 'submitting' ? (
              <>
                <LoaderCircle className={styles.loginSpinner} aria-hidden="true" />
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
          <div className={styles.divider} aria-hidden="true">
            <span>或者</span>
          </div>
          <div className={styles.signupRow}>
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
