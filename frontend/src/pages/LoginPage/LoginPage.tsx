import { Alert, Button, Checkbox, Form, Input, Tag } from '@arco-design/web-react';
import { IconEmail, IconLock, IconSafe, IconUser } from '@arco-design/web-react/icon';
import { Link, useNavigate } from 'react-router-dom';
import { BrandLogo } from '../../components/brand/BrandLogo';
import { mockAuthScenarios, mockAuthStatus, mockAuthUser, mockLoginDefaults } from '../../mock/auth';
import type { AuthStatus, LoginFormValues } from '../../mock/auth';
import styles from './LoginPage.module.css';

const statusColor: Record<AuthStatus, 'gray' | 'green' | 'orange' | 'red' | 'blue'> = {
  anonymous: 'gray',
  authenticated: 'green',
  expired: 'orange',
  disabled: 'red',
  forbidden: 'red'
};

export function LoginPage() {
  const navigate = useNavigate();
  const currentScenario = mockAuthScenarios.find((item) => item.status === mockAuthStatus) ?? mockAuthScenarios[0];

  const handleSubmit = (_values: LoginFormValues) => {
    navigate('/');
  };

  return (
    <main className={styles.page}>
      <section className={styles.hero}>
        <BrandLogo size="hero" showTagline />
        <div className={styles.heroCopy}>
          <Tag color="green">Mock Auth</Tag>
          <h1>把每一次饮食任务绑定到正确的人</h1>
          <p>登录后，会话、饮食记录、备餐计划、知识库权限和营养目标都会按当前用户隔离。当前页面只模拟体验，不保存真实 token，不调用真实后端。</p>
        </div>
        <div className={styles.securityStrip}>
          <span>推荐方案</span>
          <strong>{mockAuthUser.security.tokenStrategy}</strong>
          <small>Access Token {mockAuthUser.security.accessTokenTtl} · Refresh Token {mockAuthUser.security.refreshTokenMode}</small>
        </div>
      </section>

      <section className={styles.loginStack}>
        <Form className={styles.form} initialValues={mockLoginDefaults} layout="vertical" onSubmit={handleSubmit}>
          <div className={styles.formHead}>
            <Tag color={statusColor[currentScenario.status]}>{currentScenario.title}</Tag>
            <h2>进入 FoodMate 工作台</h2>
            <span>{currentScenario.description}</span>
          </div>

          <Alert className={styles.notice} type="info" title="前端 mock 状态" content="这里不会生成真实凭证；后端认证接口审核通过后再接入登录、刷新、退出和路由守卫。" />

          <Form.Item label="用户名或邮箱" field="username" rules={[{ required: true, message: '请输入用户名或邮箱' }]}>
            <Input prefix={<IconUser />} placeholder="liang 或 liang@example.com" />
          </Form.Item>
          <Form.Item label="密码" field="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<IconLock />} placeholder="请输入密码" />
          </Form.Item>

          <div className={styles.options}>
            <Form.Item field="rememberMe" triggerPropName="checked" noStyle>
              <Checkbox>保持登录 7 天</Checkbox>
            </Form.Item>
            <Link to="/login">忘记密码</Link>
          </div>

          <Button htmlType="submit" long type="primary">
            登录并进入工作台
          </Button>

          <div className={styles.secondaryActions}>
            <Button long>注册新账号</Button>
            <Button long onClick={() => navigate('/')}>
              稍后体验
            </Button>
          </div>
        </Form>

        <aside className={styles.profileCard}>
          <div className={styles.profileHead}>
            <div className={styles.avatar}>梁</div>
            <div>
              <span>当前 mock 用户</span>
              <strong>{mockAuthUser.displayName}</strong>
            </div>
            <Tag color={mockAuthUser.status === 'active' ? 'green' : 'red'}>{mockAuthUser.status}</Tag>
          </div>

          <dl className={styles.profileGrid}>
            <dt>账号</dt>
            <dd>{mockAuthUser.username}</dd>
            <dt>邮箱</dt>
            <dd>{mockAuthUser.email}</dd>
            <dt>角色</dt>
            <dd>{mockAuthUser.role}</dd>
            <dt>上次登录</dt>
            <dd>{mockAuthUser.lastLoginAt}</dd>
          </dl>

          <div className={styles.nutrition}>
            <IconSafe />
            <div>
              <strong>{mockAuthUser.profile.proteinTargetRange[0]}-{mockAuthUser.profile.proteinTargetRange[1]}g/天</strong>
              <span>{mockAuthUser.profile.weightKg}kg × {mockAuthUser.profile.proteinMultiplierRange.join('-')} · {mockAuthUser.profile.preference}</span>
            </div>
          </div>
        </aside>
      </section>

      <section className={styles.permissionPanel}>
        <div className={styles.panelHead}>
          <div>
            <span>权限范围</span>
            <strong>后端接入后由 token 解析 userId/role，前端不传可信用户 ID</strong>
          </div>
          <Tag color="blue">
            <IconEmail />
            待审核契约
          </Tag>
        </div>
        <div className={styles.permissionGrid}>
          {mockAuthUser.permissions.map((permission) => (
            <article className={styles.permission} key={permission.key}>
              <strong>{permission.label}</strong>
              <span>{permission.scope}</span>
            </article>
          ))}
        </div>
        <div className={styles.scenarios}>
          {mockAuthScenarios.map((scenario) => (
            <Tag color={statusColor[scenario.status]} key={scenario.status}>
              {scenario.code}
            </Tag>
          ))}
        </div>
      </section>
    </main>
  );
}
