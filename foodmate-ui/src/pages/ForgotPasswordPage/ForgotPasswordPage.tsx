import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../../components/ui/button';
import { notify } from '../../lib/notice';
import { apiFieldError, isAbortError } from '../../services/apiClient';
import { requestPasswordReset } from '../../services/authService';
import { AuthBrand, AuthCard, AuthField, AuthShell, AuthSubmit } from '../Auth/AuthVisual';
import styles from '../LoginPage/LoginPage.module.css';

export function ForgotPasswordPage() {
  const navigate = useNavigate();
  const requestControllerRef = useRef<AbortController>();
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);
  const [emailError, setEmailError] = useState<string>();

  useEffect(() => {
    return () => requestControllerRef.current?.abort();
  }, []);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setEmailError(undefined);
    setSubmitting(true);
    requestControllerRef.current?.abort();
    const controller = new AbortController();
    requestControllerRef.current = controller;
    try {
      await requestPasswordReset(email, controller.signal);
      if (controller.signal.aborted) return;
      setSent(true);
    } catch (error) {
      if (controller.signal.aborted || isAbortError(error)) return;
      const fieldError = apiFieldError(error, 'email');
      if (fieldError) setEmailError(fieldError);
      else notify(error instanceof Error ? error.message : '密码重置请求失败', 'error');
    } finally {
      if (requestControllerRef.current === controller) {
        requestControllerRef.current = undefined;
        if (!controller.signal.aborted) setSubmitting(false);
      }
    }
  };

  return (
    <AuthShell variant="forgot">
      <div className={styles.forgotContainer}>
        <AuthCard className={styles.forgotCard}>
          <AuthBrand
            title="找回密码"
            subtitle="输入你的注册邮箱，我们将发送重置链接"
            iconSrc="/assets/figma/auth/foodmate-forgot-leaf.svg"
          />
          <form className={styles.authForm} onSubmit={submit}>
            <AuthField
              label="邮箱地址"
              name="email"
              type="email"
              autoComplete="email"
              placeholder="example@foodmate.com"
              leadingIcon="mail"
              leadingIconSrc="/assets/figma/auth/foodmate-forgot-mail.svg"
              value={email}
              error={emailError}
              onChange={(event) => {
                setEmail(event.target.value);
                setEmailError(undefined);
              }}
            />
            <div className={styles.authActionStack} data-node-id="680:293">
              <AuthSubmit disabled={submitting}>{submitting ? '发送中...' : '发送重置邮件'}</AuthSubmit>
              <Button className={styles.authBackLink} variant="ghost" type="button" onClick={() => navigate('/login')}>
                返回登录
              </Button>
            </div>
          </form>
        </AuthCard>
        <AuthCard className={`${styles.forgotCard} ${styles.successCard}`}>
          <div className={styles.successContent} data-node-id="680:298">
            <div className={styles.successIcon} aria-hidden="true" data-node-id="680:299">
              <img src="/assets/figma/auth/foodmate-forgot-check-circle.svg" alt="" />
            </div>
            <h1>邮件已发送</h1>
            <p>如果该邮箱已注册，你将收到重置密码的邮件。请检查你的收件箱。</p>
          </div>
          <div className={styles.successActions} data-node-id="680:303">
            <Button className={styles.successActionButton} type="button" onClick={() => navigate('/login')}>
              返回登录
            </Button>
            {sent ? (
              <span className={styles.successLive} role="status">
                重置邮件请求已完成
              </span>
            ) : null}
          </div>
        </AuthCard>
      </div>
    </AuthShell>
  );
}
