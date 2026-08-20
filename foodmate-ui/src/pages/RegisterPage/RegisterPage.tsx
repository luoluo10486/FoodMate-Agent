import { CheckCircle2 } from 'lucide-react';
import { useState } from 'react';
import type { ChangeEvent, FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { AuthBrand, AuthCard, AuthDivider, AuthField, AuthShell, AuthSubmit, PasswordField } from '../Auth/AuthVisual';
import { Button } from '../../components/ui/button';
import { notify } from '../../lib/notice';
import { register } from '../../services/authService';
import styles from '../LoginPage/LoginPage.module.css';

type RegisterValues = {
  username: string;
  email: string;
  password: string;
  confirmPassword: string;
};

const rules = [
  { label: '至少 8 个字符', matches: (value: string) => value.length >= 8 },
  { label: '包含大写字母', matches: (value: string) => /[A-Z]/.test(value) },
  { label: '包含小写字母', matches: (value: string) => /[a-z]/.test(value) },
  { label: '包含数字', matches: (value: string) => /\d/.test(value) },
];

export function RegisterPage() {
  const navigate = useNavigate();
  const [values, setValues] = useState<RegisterValues>({ username: '', email: '', password: '', confirmPassword: '' });
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const update = (key: keyof RegisterValues) => (event: ChangeEvent<HTMLInputElement>) =>
    setValues((current) => ({ ...current, [key]: event.target.value }));

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (values.password !== values.confirmPassword) {
      notify('两次输入的密码不一致。', 'error');
      return;
    }
    setSubmitting(true);
    try {
      await register(values);
      navigate('/');
    } catch (error) {
      notify(error instanceof Error ? error.message : '注册失败', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthShell variant="register">
      <AuthCard className={styles.registerCard}>
        <AuthBrand title="创建账号" subtitle="开始你的营养管理之旅" />
        <form className={styles.authForm} onSubmit={submit}>
          <AuthField
            label="用户名"
            name="username"
            autoComplete="username"
            placeholder="麦克斯"
            leadingIcon="user"
            value={values.username}
            onChange={update('username')}
          />
          <AuthField
            label="邮箱地址"
            name="email"
            type="email"
            autoComplete="email"
            placeholder="max@foodmate.com"
            leadingIcon="mail"
            value={values.email}
            onChange={update('email')}
          />
          <PasswordField
            label="密码"
            name="password"
            autoComplete="new-password"
            placeholder="Foodmate123"
            value={values.password}
            show={showPassword}
            onToggle={() => setShowPassword((current) => !current)}
            onChange={update('password')}
          />
          <PasswordField
            label="确认密码"
            name="confirmPassword"
            autoComplete="new-password"
            placeholder="Foodmate123"
            value={values.confirmPassword}
            show={showConfirmPassword}
            onToggle={() => setShowConfirmPassword((current) => !current)}
            onChange={update('confirmPassword')}
          />

          <div className={styles.passwordRules} aria-label="密码要求">
            {rules.map((rule) => {
              const valid = rule.matches(values.password);
              return (
                <span className={valid ? styles.passwordRuleValid : styles.passwordRule} key={rule.label}>
                  <CheckCircle2 aria-hidden="true" />
                  {rule.label}
                </span>
              );
            })}
          </div>

          <AuthSubmit disabled={submitting}>{submitting ? '注册中...' : '注册'}</AuthSubmit>
          <AuthDivider />
          <div className={styles.authLinkRow}>
            <span>已有账号？</span>
            <Button className={styles.authSecondary} variant="outline" type="button" onClick={() => navigate('/login')}>
              登录
            </Button>
          </div>
        </form>
      </AuthCard>
    </AuthShell>
  );
}
