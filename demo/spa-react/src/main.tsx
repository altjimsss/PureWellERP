import React from 'react'
import ReactDOM from 'react-dom/client'
import { HashRouter } from 'react-router-dom'
import App from './App'
import './styles/base.css'
import './styles/dashboard.css'
import './styles/finance.css'
import './styles/hrm.css'
import './styles/procurement.css'
import './styles/sales.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
  <HashRouter>
    <App />
  </HashRouter>
  </React.StrictMode>
)
