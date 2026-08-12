import React from 'react';
import ReactDOM from 'react-dom/client';
import '@fontsource/montserrat/900.css';
import '@fontsource/noto-sans-sc/chinese-simplified-400.css';
import '@fontsource/noto-sans-sc/chinese-simplified-500.css';
import '@fontsource/noto-sans-sc/chinese-simplified-700.css';
import '@fontsource/noto-sans-sc/chinese-simplified-900.css';
import '@fontsource/space-mono/400.css';
import '@fontsource/space-mono/400-italic.css';
import '@fontsource/space-mono/700.css';
import { BrowserRouter } from 'react-router-dom';
import gsap from 'gsap';
import { useGSAP } from '@gsap/react';
import './styles/tokens.css';
import './styles/motion.css';
import './styles/global.css';
import { App } from './App';

gsap.registerPlugin(useGSAP);

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>,
);
