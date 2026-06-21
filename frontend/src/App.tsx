import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';

const HomePage = lazy(() => import('./pages/HomePage/HomePage').then((module) => ({ default: module.HomePage })));
const ChatPage = lazy(() => import('./pages/ChatPage/ChatPage').then((module) => ({ default: module.ChatPage })));
const AnalysisPage = lazy(() => import('./pages/AnalysisPage/AnalysisPage').then((module) => ({ default: module.AnalysisPage })));
const PlanningPage = lazy(() => import('./pages/PlanningPage/PlanningPage').then((module) => ({ default: module.PlanningPage })));

export function App() {
  return (
    <Suspense fallback={<div style={{ padding: 32 }}>FoodMate 正在准备工作台...</div>}>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/chat/:sessionId?" element={<ChatPage />} />
        <Route path="/analysis" element={<AnalysisPage />} />
        <Route path="/planning" element={<PlanningPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}
