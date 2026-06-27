import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';

const HomePage = lazy(() => import('./pages/HomePage/HomePage').then((module) => ({ default: module.HomePage })));
const ChatPage = lazy(() => import('./pages/ChatPage/ChatPage').then((module) => ({ default: module.ChatPage })));
const AnalysisPage = lazy(() => import('./pages/AnalysisPage/AnalysisPage').then((module) => ({ default: module.AnalysisPage })));
const PlanningPage = lazy(() => import('./pages/PlanningPage/PlanningPage').then((module) => ({ default: module.PlanningPage })));
const KnowledgePage = lazy(() => import('./pages/KnowledgePage/KnowledgePage').then((module) => ({ default: module.KnowledgePage })));
const LoginPage = lazy(() => import('./pages/LoginPage/LoginPage').then((module) => ({ default: module.LoginPage })));
const ProfilePage = lazy(() => import('./pages/ProfilePage/ProfilePage').then((module) => ({ default: module.ProfilePage })));
const AdminPage = lazy(() => import('./pages/AdminPage/AdminPage').then((module) => ({ default: module.AdminPage })));

export function App() {
  return (
    <Suspense fallback={<div style={{ padding: 32 }}>FoodMate 正在准备工作台...</div>}>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/chat/:sessionId?" element={<ChatPage />} />
        <Route path="/analysis" element={<AnalysisPage />} />
        <Route path="/planning" element={<PlanningPage />} />
        <Route path="/knowledge" element={<KnowledgePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/admin" element={<AdminPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}
