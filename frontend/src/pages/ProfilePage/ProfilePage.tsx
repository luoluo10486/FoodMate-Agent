import { useEffect, useRef, useState } from 'react';
import type { ChangeEvent } from 'react';
import { Button, Card, Form, Input, InputNumber, Message, Modal, Select, Tag } from '@arco-design/web-react';
import { WorkspaceLayout } from '../../layouts/WorkspaceLayout/WorkspaceLayout';
import { getAuthUser } from '../../services/authService';
import { changePassword, deleteAvatar, updateProfile, uploadAvatar } from '../../services/accountService';
import styles from './ProfilePage.module.css';

const Option = Select.Option;

export function ProfilePage() {
  const authUser = getAuthUser();
  const [avatarRemoved, setAvatarRemoved] = useState(false);
  const [avatarError, setAvatarError] = useState('');
  const [avatarFileName, setAvatarFileName] = useState('');
  const [avatarPreviewUrl, setAvatarPreviewUrl] = useState('');
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [passwordValues, setPasswordValues] = useState({ oldPassword: '', newPassword: '', confirmPassword: '' });
  const [saved, setSaved] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(
    () => () => {
      if (avatarPreviewUrl) {
        URL.revokeObjectURL(avatarPreviewUrl);
      }
    },
    [avatarPreviewUrl],
  );

  const handleSave = async (values: Record<string, unknown>) => {
    if (import.meta.env.VITE_AGENT_MODE !== 'real') { setSaved(true); Message.success('个人资料已模拟保存'); return; }
    try { await updateProfile(values); setSaved(true); Message.success('个人资料已保存'); }
    catch (error) { Message.error(error instanceof Error ? error.message : '保存失败'); }
  };

  const handleAvatarSelect = () => {
    fileInputRef.current?.click();
  };

  const handleAvatarChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 2 * 1024 * 1024) {
      setAvatarError('图片超过 2MB 或类型不支持');
      setAvatarFileName(file.name);
      Message.error('头像上传失败：图片超过 2MB 或类型不支持');
      event.target.value = '';
      return;
    }

    if (avatarPreviewUrl) {
      URL.revokeObjectURL(avatarPreviewUrl);
    }

    setAvatarRemoved(false);
    setAvatarError('');
    setAvatarFileName(file.name);
    setAvatarPreviewUrl(URL.createObjectURL(file));
    if (import.meta.env.VITE_AGENT_MODE === 'real') uploadAvatar(file).then(() => Message.success('头像已上传')).catch((error) => Message.error(error instanceof Error ? error.message : '头像上传失败'));
    else Message.success('头像已模拟上传并生成预览');
    event.target.value = '';
  };

  const handlePasswordSave = () => {
    if (!passwordValues.oldPassword || !passwordValues.newPassword || !passwordValues.confirmPassword) {
      Message.warning('请完整填写旧密码、新密码和确认密码');
      return;
    }

    if (passwordValues.newPassword !== passwordValues.confirmPassword) {
      Message.error('两次输入的新密码不一致');
      return;
    }

    const finish = () => { setPasswordVisible(false); setPasswordValues({ oldPassword: '', newPassword: '', confirmPassword: '' }); Message.success('密码已修改，其他会话已撤销'); };
    if (import.meta.env.VITE_AGENT_MODE === 'real') changePassword(passwordValues.oldPassword, passwordValues.newPassword).then(finish).catch((error) => Message.error(error instanceof Error ? error.message : '密码修改失败'));
    else finish();
  };

  return (
    <WorkspaceLayout activeModule="profile" moduleLabel={<Tag color="green">个人资料</Tag>}>
      <div className={`${styles.page} fm-enter`}>
        <section className={styles.header}>
          <div>
            <h1>个人资料</h1>
            <p>管理账号、安全设置和饮食画像。当前为 mock 流程，不会写入真实后端。</p>
          </div>
          {saved ? <Tag color="green">已模拟保存</Tag> : null}
        </section>

        <section className={styles.body}>
          <aside className={styles.accountColumn}>
            <Card className={styles.card} bordered={false}>
              <div className={styles.avatarPanel}>
                <div className={`${styles.avatar} ${avatarRemoved ? styles.avatarMuted : ''}`}>
                  {!avatarRemoved && avatarPreviewUrl ? (
                    <img alt="avatar preview" className={styles.avatarImage} src={avatarPreviewUrl} />
                  ) : null}
                  {avatarRemoved || avatarPreviewUrl ? null : authUser.displayName.slice(0, 1)}
                  {avatarRemoved ? '访' : null}
                </div>
                <strong>{authUser.displayName}</strong>
                <span>{authUser.email}</span>
                {avatarFileName ? <Tag color="arcoblue">{avatarFileName}</Tag> : null}
              </div>
              <div className={styles.avatarActions}>
                <input
                  accept="image/jpeg,image/png,image/webp"
                  className={styles.hiddenInput}
                  onChange={handleAvatarChange}
                  ref={fileInputRef}
                  type="file"
                />
                <Button type="primary" onClick={handleAvatarSelect}>
                  选择图片
                </Button>
                <Button
                  onClick={() => {
                    if (avatarPreviewUrl) {
                      URL.revokeObjectURL(avatarPreviewUrl);
                    }
                    setAvatarRemoved(true);
                    setAvatarError('');
                    setAvatarPreviewUrl('');
                    setAvatarFileName('');
                    if (import.meta.env.VITE_AGENT_MODE === 'real') deleteAvatar().then(() => Message.success('头像已删除')).catch(() => Message.error('头像删除失败'));
                    else Message.info('头像已模拟删除');
                  }}
                >
                  删除头像
                </Button>
                <Button
                  status="danger"
                  onClick={() => {
                    setAvatarError('图片超过 2MB 或类型不支持');
                    Message.error('头像上传失败：图片超过 2MB 或类型不支持');
                  }}
                >
                  模拟失败
                </Button>
              </div>
              {avatarError ? <Tag color="red">{avatarError}</Tag> : null}
              <p className={styles.uploadHint}>
                支持 JPG / PNG / WebP，2MB 内。当前只做本地预览和状态反馈，真实接入后文件进入 MinIO。
              </p>
            </Card>

            <Card className={styles.card} bordered={false}>
              <strong>账号状态</strong>
              <div className={styles.metaList}>
                <article>
                  <span>用户名</span>
                  <b>{authUser.username}</b>
                </article>
                <article>
                  <span>角色</span>
                  <Tag color="arcoblue">{authUser.role}</Tag>
                </article>
                <article>
                  <span>状态</span>
                  <Tag color="green">{authUser.status}</Tag>
                </article>
                <article>
                  <span>最近登录</span>
                  <b>{authUser.lastLoginAt}</b>
                </article>
              </div>
            </Card>
          </aside>

          <main className={styles.formColumn}>
            <Card className={styles.card} bordered={false}>
              <div className={styles.cardHead}>
                <strong>资料与饮食画像</strong>
                <Tag color="orange">role/status 只能由管理员修改</Tag>
              </div>
              <Form
                className={styles.form}
                layout="vertical"
                initialValues={{
                  displayName: authUser.displayName,
                  email: authUser.email,
                  heightCm: authUser.profile.heightCm,
                  weightKg: authUser.profile.weightKg,
                  activityLevel: authUser.profile.activityLevel,
                  dietGoal: authUser.profile.dietGoal,
                  calorieTarget: authUser.profile.calorieTarget,
                  proteinTarget: authUser.profile.proteinTarget,
                  dislikes: authUser.profile.dislikes.join('、'),
                  allergens: authUser.profile.allergens.join('、'),
                  units: `${authUser.profile.preferredUnits.weight} / ${authUser.profile.preferredUnits.energy}`,
                }}
                onSubmit={handleSave}
              >
                <div className={styles.formGrid}>
                  <Form.Item label="昵称 / 展示名" field="displayName">
                    <Input />
                  </Form.Item>
                  <Form.Item label="邮箱" field="email">
                    <Input />
                  </Form.Item>
                  <Form.Item label="身高 cm" field="heightCm">
                    <InputNumber min={80} max={240} />
                  </Form.Item>
                  <Form.Item label="体重 kg" field="weightKg">
                    <InputNumber min={20} max={250} />
                  </Form.Item>
                  <Form.Item label="活动水平" field="activityLevel">
                    <Select>
                      <Option value="低活动">低活动</Option>
                      <Option value="中等活动">中等活动</Option>
                      <Option value="高活动">高活动</Option>
                    </Select>
                  </Form.Item>
                  <Form.Item label="饮食目标" field="dietGoal">
                    <Input />
                  </Form.Item>
                  <Form.Item label="每日热量目标 kcal" field="calorieTarget">
                    <InputNumber min={800} max={6000} />
                  </Form.Item>
                  <Form.Item label="蛋白质目标 g" field="proteinTarget">
                    <InputNumber min={20} max={300} />
                  </Form.Item>
                  <Form.Item label="忌口" field="dislikes">
                    <Input />
                  </Form.Item>
                  <Form.Item label="过敏原" field="allergens">
                    <Input />
                  </Form.Item>
                  <Form.Item label="单位偏好" field="units">
                    <Input />
                  </Form.Item>
                </div>
                <div className={styles.actions}>
                  <Button onClick={() => setPasswordVisible(true)}>修改密码</Button>
                  <Button type="primary" htmlType="submit">
                    保存资料
                  </Button>
                </div>
              </Form>
            </Card>
          </main>
        </section>
      </div>
      <Modal
        title="修改密码"
        visible={passwordVisible}
        okText="模拟保存"
        cancelText="取消"
        onCancel={() => {
          setPasswordVisible(false);
          setPasswordValues({ oldPassword: '', newPassword: '', confirmPassword: '' });
        }}
        onOk={handlePasswordSave}
      >
        <div className={styles.passwordForm}>
          <Input.Password
            placeholder="旧密码"
            value={passwordValues.oldPassword}
            onChange={(value) => setPasswordValues((current) => ({ ...current, oldPassword: value }))}
          />
          <Input.Password
            placeholder="新密码"
            value={passwordValues.newPassword}
            onChange={(value) => setPasswordValues((current) => ({ ...current, newPassword: value }))}
          />
          <Input.Password
            placeholder="确认新密码"
            value={passwordValues.confirmPassword}
            onChange={(value) => setPasswordValues((current) => ({ ...current, confirmPassword: value }))}
          />
        </div>
      </Modal>
    </WorkspaceLayout>
  );
}
