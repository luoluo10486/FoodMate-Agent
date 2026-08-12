import { Eye, EyeOff, LockKeyhole, UserRound } from 'lucide-react';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { notify } from '../../lib/notice';
import { getLoginDefaults, login, register, requestPasswordReset } from '../../services/authService';
import styles from './LoginPage.module.css';

type AuthMode = 'login' | 'register' | 'forgot';

type LoginValues = {
  username: string;
  password: string;
  rememberMe: boolean;
};

type RegisterValues = {
  username: string;
  email: string;
  password: string;
  confirmPassword: string;
};

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className={styles.field}>
      <span className={styles.fieldLabel}>{label}</span>
      {children}
    </label>
  );
}

function LoginBrand() {
  return (
    <div className={styles.loginBrand} data-node-id="647:234">
      <span className={styles.logoPlaceholder} aria-hidden="true" data-node-id="660:212" />
      <span className={styles.wordmark} data-node-id="647:236">
        <span>Food</span>
        <span>Mate</span>
      </span>
    </div>
  );
}

export function LoginPage() {
  const navigate = useNavigate();
  const defaults = getLoginDefaults();
  const [mode, setMode] = useState<AuthMode>('login');
  const [submitting, setSubmitting] = useState(false);
  const [registerValues, setRegisterValues] = useState<RegisterValues>({
    username: '',
    email: '',
    password: '',
    confirmPassword: '',
  });
  const [loginValues, setLoginValues] = useState<LoginValues>(defaults);
  const [forgotEmail, setForgotEmail] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  const handleLogin = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
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

  const handleRegister = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (registerValues.password !== registerValues.confirmPassword) {
      notify('两次密码不一致', 'error');
      return;
    }
    setSubmitting(true);
    try {
      await register(registerValues);
      navigate('/');
    } catch (error) {
      notify(error instanceof Error ? error.message : '注册失败', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const handleForgot = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    try {
      await requestPasswordReset(forgotEmail);
      notify('重置邮件已发送，请检查邮箱。', 'success');
      setMode('login');
    } catch (error) {
      notify(error instanceof Error ? error.message : '密码重置请求失败', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const title = mode === 'login' ? '欢迎回来' : mode === 'register' ? '注册账号' : '找回密码';

  return (
    <main className={styles.page}>
      <div className={styles.mintDiagonal} aria-hidden="true" />
      <section className={styles.card} aria-label={title}>
        {mode !== 'login' ? (
          <Button className={styles.backButton} variant="ghost" type="button" onClick={() => setMode('login')}>
            返回登录
          </Button>
        ) : null}
        <div className={styles.brand}>
          <LoginBrand />
          <div className={styles.welcome}>
            <p>{title}</p>
            {mode === 'login' ? <span>让我们开始今天的营养管理</span> : null}
          </div>
        </div>

        {mode === 'login' ? (
          <form className={styles.form} onSubmit={handleLogin}>
            <div className={styles.loginFields}>
              <Field label="">
                <Input
                  className={styles.figmaInput}
                  name="username"
                  autoComplete="username"
                  placeholder="邮箱地址"
                  aria-label="邮箱地址"
                  leadingIcon={<UserRound aria-hidden="true" />}
                  value={loginValues.username}
                  required
                  onChange={(event) => setLoginValues((current) => ({ ...current, username: event.target.value }))}
                />
              </Field>
              <Field label="">
                <Input
                  className={styles.figmaInput}
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder="密码"
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
              </Field>
              <div className={styles.options}>
                <Button className={styles.forgotButton} variant="ghost" type="button" onClick={() => setMode('forgot')}>
                  忘记密码？
                </Button>
              </div>
            </div>
            <Button className={styles.primaryAction} type="submit" disabled={submitting} data-node-id="647:251">
              {submitting ? '登录中...' : '登录'}
            </Button>
          </form>
        ) : null}

        {mode === 'register' ? (
          <form className={styles.form} onSubmit={handleRegister}>
            <Field label="用户名">
              <Input
                name="username"
                autoComplete="username"
                placeholder="请输入用户名"
                value={registerValues.username}
                required
                onChange={(event) => setRegisterValues((current) => ({ ...current, username: event.target.value }))}
              />
            </Field>
            <Field label="邮箱">
              <Input
                name="email"
                type="email"
                autoComplete="email"
                placeholder="请输入邮箱"
                value={registerValues.email}
                required
                onChange={(event) => setRegisterValues((current) => ({ ...current, email: event.target.value }))}
              />
            </Field>
            <Field label="密码">
              <Input
                name="password"
                type="password"
                autoComplete="new-password"
                placeholder="请输入密码"
                minLength={8}
                value={registerValues.password}
                required
                onChange={(event) => setRegisterValues((current) => ({ ...current, password: event.target.value }))}
              />
            </Field>
            <Field label="确认密码">
              <Input
                name="confirmPassword"
                type="password"
                autoComplete="new-password"
                placeholder="请再次输入密码"
                value={registerValues.confirmPassword}
                required
                onChange={(event) =>
                  setRegisterValues((current) => ({ ...current, confirmPassword: event.target.value }))
                }
              />
            </Field>
            <Button className={styles.primaryAction} type="submit" disabled={submitting}>
              {submitting ? '注册中...' : '注册'}
            </Button>
          </form>
        ) : null}

        {mode === 'forgot' ? (
          <form className={styles.form} onSubmit={handleForgot}>
            <Field label="邮箱">
              <Input
                name="email"
                type="email"
                autoComplete="email"
                placeholder="请输入注册邮箱"
                value={forgotEmail}
                required
                onChange={(event) => setForgotEmail(event.target.value)}
              />
            </Field>
            <Button className={styles.primaryAction} type="submit" disabled={submitting}>
              {submitting ? '发送中...' : '发送重置邮件'}
            </Button>
          </form>
        ) : null}

        {mode === 'login' ? (
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
                onClick={() => setMode('register')}
              >
                注册
              </Button>
            </div>
          </div>
        ) : null}
        {mode !== 'login' ? (
          <span className={styles.note}>
            {import.meta.env.VITE_AGENT_MODE === 'real' ? '当前连接真实服务' : '当前为前端 mock 流程'}
          </span>
        ) : null}
      </section>
    </main>
  );
}
