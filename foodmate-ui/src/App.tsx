import { lazy, Suspense, useEffect } from 'react';
import { Navigate, Route, Routes, useLocation, useSearchParams } from 'react-router-dom';
import { NoticeHost } from './components/ui/notice-host';
import { isVisualQaEnabled } from './lib/visualQa';

const HomePage = lazy(() => import('./pages/HomePage/HomePage').then((module) => ({ default: module.HomePage })));
const ChatPage = lazy(() => import('./pages/ChatPage/ChatPage').then((module) => ({ default: module.ChatPage })));
const DietRecordsPage = lazy(() =>
  import('./pages/DietRecordsPage/DietRecordsPage').then((module) => ({ default: module.DietRecordsPage })),
);
const AnalysisPage = lazy(() =>
  import('./pages/AnalysisPage/AnalysisPage').then((module) => ({ default: module.AnalysisPage })),
);
const PlanningPage = lazy(() =>
  import('./pages/PlanningPage/PlanningPage').then((module) => ({ default: module.PlanningPage })),
);
const KnowledgePage = lazy(() =>
  import('./pages/KnowledgePage/KnowledgePage').then((module) => ({ default: module.KnowledgePage })),
);
const LoginPage = lazy(() => import('./pages/LoginPage/LoginPage').then((module) => ({ default: module.LoginPage })));
const RegisterPage = lazy(() =>
  import('./pages/RegisterPage/RegisterPage').then((module) => ({ default: module.RegisterPage })),
);
const ForgotPasswordPage = lazy(() =>
  import('./pages/ForgotPasswordPage/ForgotPasswordPage').then((module) => ({ default: module.ForgotPasswordPage })),
);
const ResetPasswordPage = lazy(() =>
  import('./pages/ResetPasswordPage/ResetPasswordPage').then((module) => ({ default: module.ResetPasswordPage })),
);
const TokenStatusPage = lazy(() =>
  import('./pages/TokenStatusPage/TokenStatusPage').then((module) => ({ default: module.TokenStatusPage })),
);
const ProfilePage = lazy(() =>
  import('./pages/ProfilePage/ProfilePage').then((module) => ({ default: module.ProfilePage })),
);
const AdminPage = lazy(() => import('./pages/AdminPage/AdminPage').then((module) => ({ default: module.AdminPage })));

function AnalysisRoute() {
  const [searchParams] = useSearchParams();
  return searchParams.get('view') === 'records' ? <DietRecordsPage /> : <AnalysisPage />;
}

function VisualQaMode() {
  const location = useLocation();
  const enabled = isVisualQaEnabled(location.search);

  useEffect(() => {
    if (enabled) {
      document.documentElement.dataset.visualQa = 'true';
    } else {
      delete document.documentElement.dataset.visualQa;
    }

    return () => {
      delete document.documentElement.dataset.visualQa;
    };
  }, [enabled]);

  return null;
}

export function App() {
  return (
    <Suspense fallback={<div style={{ padding: 32 }}>FoodMate 正在准备工作台...</div>}>
      <VisualQaMode />
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/chat/:session_id?" element={<ChatPage />} />
        <Route path="/analysis" element={<AnalysisRoute />} />
        <Route path="/planning" element={<PlanningPage />} />
        <Route path="/knowledge" element={<KnowledgePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/token-status" element={<TokenStatusPage />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/profile/memories" element={<ProfilePage />} />
        <Route path="/profile/security" element={<ProfilePage />} />
        <Route path="/profile/data" element={<ProfilePage />} />
        <Route path="/admin/*" element={<AdminPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      <NoticeHost />
    </Suspense>
  );
}
