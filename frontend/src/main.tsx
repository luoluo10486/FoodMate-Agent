import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import gsap from 'gsap';
import { useGSAP } from '@gsap/react';
import '@arco-design/web-react/dist/css/arco.css';
import './styles/tokens.css';
import './styles/arco-theme.css';
import './styles/motion.css';
import './styles/global.css';
import { App } from './App';

gsap.registerPlugin(useGSAP);

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);
