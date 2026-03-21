import { Outlet } from 'react-router-dom'
import Sidebar from '../components/Sidebar'

const AppLayout = () => (
  <div className="app-shell">
    <Sidebar />
    <main className="main">
      <Outlet />
    </main>
  </div>
)

export default AppLayout
