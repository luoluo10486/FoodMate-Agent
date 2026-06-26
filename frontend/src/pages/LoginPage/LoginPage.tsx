import { Alert, Button, Checkbox, Form, Input, Tag } from '@arco-design/web-react';
import { IconLock, IconSafe, IconUser } from '@arco-design/web-react/icon';
import { Link, useNavigate } from 'react-router-dom';
import { BrandLogo } from '../../components/brand/BrandLogo';
import { mockAuthScenarios, mockAuthStatus, mockLoginDefaults, publicAuthCapabilities } from '../../mock/auth';
import type { AuthStatus, LoginFormValues } from '../../mock/auth';
import styles from './LoginPage.module.css';

const statusColor: Record<AuthStatus, 'gray' | 'green' | 'orange' | 'red' | 'blue'> = {
  anonymous: 'gray',
  authenticated: 'green',
  expired: 'orange',
  disabled: 'red',
  forbidden: 'red'
};

const authPrinciples = [
  '未登录前不展示任何用户姓名、邮箱、历史记录或营养目标',
  '登录成功后再通过 /foodmate/auth/me 获取当前用户资料',
  '前端 mock 不生成真实 token，不调用真实后端接口'
];

export function LoginPage() {
  const navigate = useNavigate();
  const currentScenario = mockAuthScenarios.find((item) => item.status === mockAuthStatus) ?? mockAuthScenarios[0];

  const handleSubmit = (_values: LoginFormValues) => {
    navigate('/');
  };

  return (
    <main className={styles.page}>
      <section className={styles.intro}>
        <div className={styles.brandBar}>
          <BrandLogo size="small" showTagline />
          <Tag color="gray">{currentScenario.title}</Tag>
        </div>

        <div className={styles.heroCopy}>
          <span>FoodMate Account</span>
          <h1>先登录，再进入你的饮食工作台</h1>
          <p>这是未登录公共入口。姓名、邮箱、历史饮食记录、营养目标和计划内容都会在认证成功后再加载。</p>
        </div>

        <div className={styles.publicProof}>
          <span>未登录页面原则</span>
          <strong>只展示公共信息，不展示任何个人资料</strong>
        </div>

        <div className={styles.capabilityGrid}>
          {publicAuthCapabilities.map((item) => (
            <article className={styles.capability} key={item.title}>
              <strong>{item.title}</strong>
              <span>{item.description}</span>
            </article>
          ))}
        </div>
      </section>

      <section className={styles.authShell}>
        <Form className={styles.form} initialValues={mockLoginDefaults} layout="vertical" onSubmit={handleSubmit}>
          <div className={styles.formHead}>
            <Tag color={statusColor[currentScenario.status]}>{currentScenario.title}</Tag>
            <h2>进入 FoodMate</h2>
            <p>{currentScenario.description}</p>
          </div>

          <Alert
            className={styles.notice}
            type="info"
            title="当前是 mock 登录"
            content="提交后只进入前端演示工作台；真实登录、刷新、退出和路由守卫等待后端鉴权方案审核后接入。"
          />

          <Form.Item label="用户名或邮箱" field="username" rules={[{ required: true, message: '请输入用户名或邮箱' }]}>
            <Input prefix={<IconUser />} placeholder="请输入用户名或邮箱" />
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

          <Button className={styles.primaryAction} htmlType="submit" long type="primary">
            登录并进入工作台
          </Button>

          <div className={styles.secondaryActions}>
            <Button long>注册新账号</Button>
            <Button long onClick={() => navigate('/')}>
              稍后体验
            </Button>
          </div>

          <div className={styles.securityStrip}>
            <IconSafe />
            <div>
              <span>推荐鉴权方案</span>
              <strong>Access Token + HttpOnly Refresh Cookie</strong>
              <small>Access Token 15-30 分钟 · Refresh Token 7-30 天，可撤销</small>
            </div>
          </div>
        </Form>

        <aside className={styles.guardPanel}>
          <div>
            <span>认证后才加载</span>
            <strong>用户身份由后端返回</strong>
          </div>
          <ul>
            {authPrinciples.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
          <div className={styles.scenarios}>
            {mockAuthScenarios.map((scenario) => (
              <Tag color={statusColor[scenario.status]} key={scenario.status}>
                {scenario.code}
              </Tag>
            ))}
          </div>
        </aside>
      </section>
    </main>
  );
}
