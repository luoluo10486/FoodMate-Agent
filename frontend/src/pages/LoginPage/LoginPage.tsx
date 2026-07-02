import { Button, Checkbox, Form, Input, Message } from '@arco-design/web-react';
import { IconEmail, IconLeft, IconLock, IconUser } from '@arco-design/web-react/icon';
import gsap from 'gsap';
import { useGSAP } from '@gsap/react';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BrandLogo } from '../../components/brand/BrandLogo';
import { getLoginDefaults } from '../../services/authService';
import type { LoginFormValues } from '../../mock/auth';
import styles from './LoginPage.module.css';

type AuthMode = 'login' | 'register' | 'forgot';

type RegisterFormValues = {
  username: string;
  email: string;
  emailCode: string;
  password: string;
  confirmPassword: string;
};

type ForgotFormValues = {
  email: string;
  emailCode: string;
};

const titleByMode: Record<AuthMode, string> = {
  login: '登录 FoodMate',
  register: '注册账号',
  forgot: '找回密码',
};

function shouldPlayLoginIntro() {
  if (typeof window === 'undefined') {
    return false;
  }

  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  return !reduceMotion;
}

export function LoginPage() {
  const navigate = useNavigate();
  const pageRef = useRef<HTMLElement>(null);
  const [mode, setMode] = useState<AuthMode>('login');
  const [registerPassword, setRegisterPassword] = useState('');
  const [codeCountdown, setCodeCountdown] = useState(0);
  const [showIntro, setShowIntro] = useState(shouldPlayLoginIntro);

  useGSAP(
    () => {
      const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      gsap.set('[data-login-ambient]', { autoAlpha: 1 });

      if (reduceMotion) {
        return;
      }

      gsap.to('[data-plate-orbit="main"]', {
        rotation: 360,
        duration: 72,
        ease: 'none',
        repeat: -1,
        transformOrigin: '50% 50%',
      });
      gsap.to('[data-plate-orbit="side"]', {
        rotation: -360,
        duration: 92,
        ease: 'none',
        repeat: -1,
        transformOrigin: '50% 50%',
      });
      gsap.to('[data-brand-arc]', {
        rotation: '+=7',
        duration: 12,
        ease: 'sine.inOut',
        repeat: -1,
        yoyo: true,
        transformOrigin: '50% 50%',
      });
      gsap.to('[data-light-sweep]', {
        x: 18,
        y: -10,
        autoAlpha: 0.82,
        duration: 10,
        ease: 'sine.inOut',
        repeat: -1,
        yoyo: true,
      });
    },
    { scope: pageRef },
  );

  useGSAP(
    () => {
      const card = `.${styles.card}`;

      if (!showIntro) {
        return;
      }

      gsap.set(card, { autoAlpha: 0, y: 34, scale: 0.94 });
      gsap.set('[data-intro="shard"]', { autoAlpha: 0, scaleX: 0, transformOrigin: '50% 50%' });
      gsap.set('[data-intro="letter"]', { autoAlpha: 0, yPercent: 110, rotationX: -86, transformOrigin: '50% 80%' });
      gsap.set('[data-intro="mark"]', { autoAlpha: 0, scale: 0.34, rotation: -180, transformOrigin: '50% 50%' });
      gsap.set('[data-intro="ring"]', { autoAlpha: 0, scale: 0.42, rotation: -120, transformOrigin: '50% 50%' });
      gsap.set('[data-intro="beam"]', { scaleX: 0, transformOrigin: '50% 50%' });

      const timeline = gsap.timeline({
        defaults: { ease: 'power3.out' },
        onComplete: () => {
          setShowIntro(false);
          gsap.set(card, { autoAlpha: 1, y: 0, scale: 1, clearProps: 'transform,opacity,visibility' });
        },
      });

      timeline
        .to('[data-intro="stage"]', { autoAlpha: 1, duration: 0.08 })
        .to('[data-intro="ring"]', { autoAlpha: 1, scale: 1, rotation: 290, duration: 0.72, ease: 'expo.out' })
        .to(
          '[data-intro="mark"]',
          { autoAlpha: 1, scale: 1.08, rotation: 26, duration: 0.62, ease: 'back.out(2.1)' },
          '<0.08',
        )
        .to('[data-intro="mark"]', { scale: 1, rotation: 0, duration: 0.32, ease: 'power2.out' })
        .to(
          '[data-intro="shard"]',
          { autoAlpha: 1, scaleX: 1, duration: 0.38, stagger: { amount: 0.28, from: 'center' } },
          '<0.04',
        )
        .to(
          '[data-intro="letter"]',
          { autoAlpha: 1, yPercent: 0, rotationX: 0, duration: 0.48, stagger: 0.045, ease: 'back.out(1.5)' },
          '<0.08',
        )
        .to('[data-intro="beam"]', { scaleX: 1, duration: 0.44, ease: 'power4.out' }, '<0.1')
        .to('[data-intro="brand"]', { y: -18, scale: 0.92, duration: 0.42, ease: 'power3.inOut' }, '+=0.24')
        .to('[data-intro="stage"]', { autoAlpha: 0, scale: 1.05, duration: 0.48, ease: 'power2.inOut' }, '<0.06')
        .to(card, { autoAlpha: 1, y: 0, scale: 1, duration: 0.62, ease: 'back.out(1.35)' }, '<0.18');
    },
    { scope: pageRef, dependencies: [showIntro], revertOnUpdate: true },
  );

  useEffect(() => {
    if (codeCountdown <= 0) {
      return undefined;
    }

    const timer = window.setTimeout(() => {
      setCodeCountdown((value) => Math.max(value - 1, 0));
    }, 1000);

    return () => window.clearTimeout(timer);
  }, [codeCountdown]);

  const switchMode = (nextMode: AuthMode) => {
    setMode(nextMode);
    setRegisterPassword('');
    setCodeCountdown(0);
  };

  const sendEmailCode = () => {
    Message.success('验证码已发送（mock）');
    setCodeCountdown(60);
  };

  const handleLogin = (_values: LoginFormValues) => {
    navigate('/');
  };

  const handleRegister = (_values: RegisterFormValues) => {
    Message.success('注册流程已提交（mock）');
    switchMode('login');
  };

  const handleForgot = (_values: ForgotFormValues) => {
    Message.success('密码重置已提交（mock）');
    switchMode('login');
  };

  return (
    <main className={`${styles.page} ${showIntro ? styles.pageIntro : ''}`} ref={pageRef}>
      <div className={styles.ambient} data-login-ambient aria-hidden="true">
        <div className={`${styles.plateAura} ${styles.plateAuraMain}`} data-plate-orbit="main" />
        <div className={`${styles.plateAura} ${styles.plateAuraSide}`} data-plate-orbit="side" />
        <div className={`${styles.lightSweep} ${styles.lightSweepWarm}`} data-light-sweep />
        <div className={`${styles.lightSweep} ${styles.lightSweepCool}`} data-light-sweep />
        <div className={`${styles.brandArc} ${styles.brandArcGreen}`} data-brand-arc />
        <div className={`${styles.brandArc} ${styles.brandArcOrange}`} data-brand-arc />
      </div>

      {showIntro ? (
        <div className={styles.introStage} data-intro="stage" aria-hidden="true">
          <div className={styles.introRing} data-intro="ring" />
          <div className={styles.introBrand} data-intro="brand">
            <div className={styles.introMark} data-intro="mark">
              <BrandLogo size="hero" showWordmark={false} />
            </div>
            <div className={styles.introWord} aria-hidden="true">
              {'FoodMate'.split('').map((letter, index) => (
                <span
                  className={index >= 4 ? styles.mateLetter : styles.foodLetter}
                  data-intro="letter"
                  key={`${letter}-${index}`}
                >
                  {letter}
                </span>
              ))}
            </div>
            <div className={styles.introBeam} data-intro="beam" />
          </div>
          <div className={styles.introShards}>
            {Array.from({ length: 12 }, (_, index) => (
              <span data-intro="shard" key={index} />
            ))}
          </div>
        </div>
      ) : null}

      <section className={styles.card} aria-label={titleByMode[mode]}>
        {mode !== 'login' ? (
          <Button className={styles.backButton} icon={<IconLeft />} type="text" onClick={() => switchMode('login')}>
            返回登录
          </Button>
        ) : null}

        <div className={styles.brand}>
          <BrandLogo size="small" showTagline={false} />
          <p>{titleByMode[mode]}</p>
        </div>

        {mode === 'login' ? (
          <Form className={styles.form} initialValues={getLoginDefaults()} layout="vertical" onSubmit={handleLogin}>
            <Form.Item
              label="用户名或邮箱"
              field="username"
              rules={[{ required: true, message: '请输入用户名或邮箱' }]}
            >
              <Input prefix={<IconUser />} placeholder="请输入用户名或邮箱" />
            </Form.Item>
            <Form.Item label="密码" field="password" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password prefix={<IconLock />} placeholder="请输入密码" />
            </Form.Item>

            <div className={styles.options}>
              <Form.Item field="rememberMe" triggerPropName="checked" noStyle>
                <Checkbox>保持登录</Checkbox>
              </Form.Item>
              <Button className={styles.linkButton} type="text" onClick={() => switchMode('forgot')}>
                忘记密码
              </Button>
            </div>

            <Button className={styles.primaryAction} htmlType="submit" long type="primary">
              登录
            </Button>
          </Form>
        ) : null}

        {mode === 'register' ? (
          <Form className={styles.form} layout="vertical" onSubmit={handleRegister}>
            <Form.Item label="用户名" field="username" rules={[{ required: true, message: '请输入用户名' }]}>
              <Input prefix={<IconUser />} placeholder="请输入用户名" />
            </Form.Item>
            <Form.Item label="邮箱" field="email" rules={[{ required: true, message: '请输入邮箱' }]}>
              <Input prefix={<IconEmail />} placeholder="请输入邮箱" />
            </Form.Item>
            <Form.Item label="邮箱验证码" field="emailCode" rules={[{ required: true, message: '请输入邮箱验证码' }]}>
              <div className={styles.codeRow}>
                <Input placeholder="请输入验证码" />
                <Button disabled={codeCountdown > 0} htmlType="button" onClick={sendEmailCode}>
                  {codeCountdown > 0 ? `${codeCountdown}s` : '发送验证码'}
                </Button>
              </div>
            </Form.Item>
            <Form.Item label="密码" field="password" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password prefix={<IconLock />} placeholder="请输入密码" onChange={setRegisterPassword} />
            </Form.Item>
            <Form.Item
              label="确认密码"
              field="confirmPassword"
              rules={[
                { required: true, message: '请再次输入密码' },
                {
                  validator: (value, callback) => {
                    if (value && registerPassword && value !== registerPassword) {
                      callback('两次密码不一致');
                    } else {
                      callback();
                    }
                  },
                },
              ]}
            >
              <Input.Password prefix={<IconLock />} placeholder="请再次输入密码" />
            </Form.Item>

            <Button className={styles.primaryAction} htmlType="submit" long type="primary">
              注册
            </Button>
          </Form>
        ) : null}

        {mode === 'forgot' ? (
          <Form className={styles.form} layout="vertical" onSubmit={handleForgot}>
            <Form.Item label="邮箱" field="email" rules={[{ required: true, message: '请输入邮箱' }]}>
              <Input prefix={<IconEmail />} placeholder="请输入邮箱" />
            </Form.Item>
            <Form.Item label="邮箱验证码" field="emailCode" rules={[{ required: true, message: '请输入邮箱验证码' }]}>
              <div className={styles.codeRow}>
                <Input placeholder="请输入验证码" />
                <Button disabled={codeCountdown > 0} htmlType="button" onClick={sendEmailCode}>
                  {codeCountdown > 0 ? `${codeCountdown}s` : '发送验证码'}
                </Button>
              </div>
            </Form.Item>

            <Button className={styles.primaryAction} htmlType="submit" long type="primary">
              下一步
            </Button>
          </Form>
        ) : null}

        {mode === 'login' ? (
          <div className={styles.actions}>
            <Button long onClick={() => switchMode('register')}>
              注册账号
            </Button>
            <Button long onClick={() => navigate('/')}>
              游客登录
            </Button>
          </div>
        ) : null}
        <span className={styles.note}>当前为前端 mock 流程</span>
      </section>
    </main>
  );
}
