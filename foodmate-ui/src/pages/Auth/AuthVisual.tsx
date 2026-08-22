import { Eye, EyeOff, Leaf, LockKeyhole, Mail, UserRound } from 'lucide-react';
import type { ChangeEvent, ReactNode } from 'react';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import styles from '../LoginPage/LoginPage.module.css';

export type AuthVariant = 'login' | 'register' | 'forgot' | 'reset' | 'token';

export function AuthShell({ variant, children }: { variant: AuthVariant; children: ReactNode }) {
  return (
    <main className={`${styles.authPage} ${styles[`authPage-${variant}`]}`}>
      <div className={styles.authDiagonal} aria-hidden="true" />
      {children}
    </main>
  );
}

export function AuthCard({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <section className={`${styles.authCard} ${className}`}>{children}</section>;
}

export function AuthBrand({
  title,
  subtitle,
  mark = 'leaf',
}: {
  title: string;
  subtitle: string;
  mark?: 'leaf' | 'utensils';
}) {
  return (
    <header className={styles.authHeader}>
      <div className={styles.authBrand} data-node-id="680:220">
        <span className={styles.authMark} aria-hidden="true">
          {mark === 'utensils' ? <img src="/assets/figma/auth/foodmate-fork-knife.svg" alt="" /> : <Leaf />}
        </span>
        <span className={styles.authWordmark}>
          <span>Food</span>
          <span>Mate</span>
        </span>
      </div>
      <div className={styles.authHeading}>
        <h1>{title}</h1>
        <p>{subtitle}</p>
      </div>
    </header>
  );
}

type AuthFieldProps = {
  label: string;
  name: string;
  value: string;
  placeholder?: string;
  type?: 'text' | 'email';
  autoComplete?: string;
  leadingIcon?: 'user' | 'mail';
  required?: boolean;
  onChange: (event: ChangeEvent<HTMLInputElement>) => void;
};

export function AuthField({
  label,
  name,
  value,
  placeholder,
  type = 'text',
  autoComplete,
  leadingIcon,
  required = true,
  onChange,
}: AuthFieldProps) {
  const icon =
    leadingIcon === 'mail' ? <Mail aria-hidden="true" /> : leadingIcon === 'user' ? <UserRound /> : undefined;

  return (
    <label className={styles.authField}>
      <span className={styles.authFieldLabel}>{label}</span>
      <Input
        className={styles.authInput}
        name={name}
        type={type}
        autoComplete={autoComplete}
        placeholder={placeholder}
        leadingIcon={icon}
        value={value}
        required={required}
        onChange={onChange}
      />
    </label>
  );
}

type PasswordFieldProps = {
  label: string;
  name: string;
  value: string;
  placeholder?: string;
  autoComplete?: string;
  show: boolean;
  onToggle: () => void;
  onChange: (event: ChangeEvent<HTMLInputElement>) => void;
};

export function PasswordField({
  label,
  name,
  value,
  placeholder,
  autoComplete,
  show,
  onToggle,
  onChange,
}: PasswordFieldProps) {
  return (
    <label className={styles.authField}>
      <span className={styles.authFieldLabel}>{label}</span>
      <Input
        className={styles.authInput}
        name={name}
        type={show ? 'text' : 'password'}
        autoComplete={autoComplete}
        placeholder={placeholder}
        leadingIcon={<LockKeyhole aria-hidden="true" />}
        trailingAction={
          <Button
            variant="ghost"
            size="icon"
            className={styles.authPasswordToggle}
            type="button"
            aria-label={show ? `隐藏${label}` : `显示${label}`}
            onClick={onToggle}
          >
            {show ? <EyeOff aria-hidden="true" /> : <Eye aria-hidden="true" />}
          </Button>
        }
        value={value}
        required
        onChange={onChange}
      />
    </label>
  );
}

export function AuthSubmit({ children, disabled = false }: { children: ReactNode; disabled?: boolean }) {
  return (
    <Button className={styles.authPrimary} type="submit" disabled={disabled}>
      {children}
    </Button>
  );
}

export function AuthDivider() {
  return (
    <div className={styles.authDivider} aria-hidden="true">
      <span>或者</span>
    </div>
  );
}

export function AuthBackButton({ onClick }: { onClick: () => void }) {
  return (
    <Button className={styles.authBackLink} variant="ghost" type="button" onClick={onClick}>
      返回登录
    </Button>
  );
}
