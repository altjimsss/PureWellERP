import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './layouts/AppLayout'
import ServerPage from './components/ServerPage'
import SupabaseTodosPage from './components/SupabaseTodosPage'

const App = () => (
  <Routes>
    <Route element={<AppLayout />}>
      <Route index element={<Navigate to="/dashboard" replace />} />
      <Route path="/dashboard" element={<ServerPage url="/" />} />
      <Route path="/finance" element={<ServerPage url="/modules/finance" />} />
      <Route path="/hrm" element={<ServerPage url="/modules/hrm" />} />
      <Route path="/procurement" element={<ServerPage url="/modules/procurement" />} />
      <Route path="/sales" element={<ServerPage url="/modules/sales" />} />
      <Route path="/supabase-todos" element={<SupabaseTodosPage />} />
    </Route>
  </Routes>
)

export default App
