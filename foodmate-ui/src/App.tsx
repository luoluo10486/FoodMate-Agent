import { lazy, Suspense, useEffect } from 'react';
import { Navigate, Route, Routes, useLocation, useSearchParams } from 'react-router-dom';
import { NoticeHost } from './components/ui/notice-host';
import { AuthProvider, RequireAdmin, RequireAuth } from './auth/AuthProvider';
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
    <AuthProvider>
      <Suspense fallback={<div style={{ padding: 32 }}>FoodMate 正在准备工作台...</div>}>
        <VisualQaMode />
        <Routes>
          <Route
            path="/"
            element={
              <RequireAuth>
                <HomePage />
              </RequireAuth>
            }
          />
          <Route
            path="/chat/:session_id?"
            element={
              <RequireAuth>
                <ChatPage />
              </RequireAuth>
            }
          />
          <Route
            path="/analysis"
            element={
              <RequireAuth>
                <AnalysisRoute />
              </RequireAuth>
            }
          />
          <Route
            path="/planning"
            element={
              <RequireAuth>
                <PlanningPage />
              </RequireAuth>
            }
          />
          <Route
            path="/knowledge"
            element={
              <RequireAuth>
                <KnowledgePage />
              </RequireAuth>
            }
          />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/token-status" element={<TokenStatusPage />} />
          <Route
            path="/profile"
            element={
              <RequireAuth>
                <ProfilePage />
              </RequireAuth>
            }
          />
          <Route
            path="/profile/memories"
            element={
              <RequireAuth>
                <ProfilePage />
              </RequireAuth>
            }
          />
          <Route
            path="/profile/security"
            element={
              <RequireAuth>
                <ProfilePage />
              </RequireAuth>
            }
          />
          <Route
            path="/profile/data"
            element={
              <RequireAuth>
                <ProfilePage />
              </RequireAuth>
            }
          />
          <Route
            path="/admin/*"
            element={
              <RequireAdmin>
                <AdminPage />
              </RequireAdmin>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        <NoticeHost />
      </Suspense>
    </AuthProvider>
  );
}
