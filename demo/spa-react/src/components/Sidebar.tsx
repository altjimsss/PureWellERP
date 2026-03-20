import { NavLink } from 'react-router-dom'
import { useEffect, useState } from 'react'

type UserProfile = {
  fullName: string
  role: string
  position: string
  department: string
  initials: string
  profilePicUrl?: string
}

const Sidebar = () => {
  const [user, setUser] = useState<UserProfile | null>(null)

  useEffect(() => {
    fetch('/api/me', { credentials: 'include' })
      .then((res) => {
        if (res.status === 401) {
          window.location.href = '/login'
          return null
        }
        return res.ok ? res.json() : null
      })
      .then((data) => setUser(data))
      .catch(() => setUser(null))
  }, [])

  return (
    <aside className="sidebar">
      <div className="logo-wrap">
        <img src="/img/purewelllogo.png" alt="PureWell Logo" />
        <div>
          <strong>PureWell</strong>
          <span>Refilling Station</span>
        </div>
      </div>
      <nav className="nav">
        <NavLink to="/dashboard">
          <span className="label">
            <span className="nav-icon">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <rect x="3" y="3" width="8" height="8" rx="2" stroke="currentColor" strokeWidth="1.6" fill="none" />
                <rect x="13" y="3" width="8" height="8" rx="2" stroke="currentColor" strokeWidth="1.6" fill="none" />
                <rect x="3" y="13" width="8" height="8" rx="2" stroke="currentColor" strokeWidth="1.6" fill="none" />
                <rect x="13" y="13" width="8" height="8" rx="2" stroke="currentColor" strokeWidth="1.6" fill="none" />
              </svg>
            </span>
            Dashboard
          </span>
        </NavLink>
        <NavLink to="/hrm">
          <span className="label">
            <span className="nav-icon">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <circle cx="9" cy="8" r="3.5" stroke="currentColor" strokeWidth="1.6" fill="none" />
                <circle cx="17" cy="9" r="2.5" stroke="currentColor" strokeWidth="1.6" fill="none" />
                <path d="M3 20a6 6 0 0 1 12 0" stroke="currentColor" strokeWidth="1.6" fill="none" />
                <path d="M14 20a5 5 0 0 1 8 0" stroke="currentColor" strokeWidth="1.6" fill="none" />
              </svg>
            </span>
            Human Resource
          </span>
        </NavLink>
        <NavLink to="/finance">
          <span className="label">
            <span className="nav-icon">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M10.464 8.746c.227-.18.497-.311.786-.394v2.795a2.252 2.252 0 0 1-.786-.393c-.394-.313-.546-.681-.546-1.004 0-.323.152-.691.546-1.004ZM12.75 15.662v-2.824c.347.085.664.228.921.421.427.32.579.686.579.991 0 .305-.152.671-.579.991a2.534 2.534 0 0 1-.921.42Z"/>
                <path fillRule="evenodd" d="M12 2.25c-5.385 0-9.75 4.365-9.75 9.75s4.365 9.75 9.75 9.75 9.75-4.365 9.75-9.75S17.385 2.25 12 2.25ZM12.75 6a.75.75 0 0 0-1.5 0v.816a3.836 3.836 0 0 0-1.72.756c-.712.566-1.112 1.35-1.112 2.178 0 .829.4 1.612 1.113 2.178.502.4 1.102.647 1.719.756v2.978a2.536 2.536 0 0 1-.921-.421l-.879-.66a.75.75 0 0 0-.9 1.2l.879.66c.533.4 1.169.645 1.821.75V18a.75.75 0 0 0 1.5 0v-.81a4.124 4.124 0 0 0 1.821-.749c.745-.559 1.179-1.344 1.179-2.191 0-.847-.434-1.632-1.179-2.191a4.122 4.124 0 0 0-1.821-.75V8.354c.29.082.559.213.786.393l.415.33a.75.75 0 0 0 .933-1.175l-.415-.33a3.836 3.836 0 0 0-1.719-.755V6Z" clipRule="evenodd"/>
              </svg>
            </span>
            Finance & Accounting
          </span>
        </NavLink>
        <NavLink to="/procurement">
          <span className="label">
            <span className="nav-icon">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M2.25 2.25a.75.75 0 0 0 0 1.5h1.386c.17 0 .318.114.362.278l2.558 9.592a3.752 3.752 0 0 0-2.806 3.63c0 .414.336.75.75.75h15.75a.75.75 0 0 0 0-1.5H5.378A2.25 2.25 0 0 1 7.5 15h11.218a.75.75 0 0 0 .674-.421 60.358 60.358 0 0 0 2.96-7.228.75.75 0 0 0-.525-.965A60.864 60.864 0 0 0 5.68 4.509l-.232-.867A1.875 1.875 0 0 0 3.636 2.25H2.25ZM3.75 20.25a1.5 1.5 0 1 1 3 0 1.5 1.5 0 0 1-3 0ZM16.5 20.25a1.5 1.5 0 1 1 3 0 1.5 1.5 0 0 1-3 0Z"/>
              </svg>
            </span>
            Procurement & Inventory
          </span>
        </NavLink>
        <NavLink to="/sales">
          <span className="label">
            <span className="nav-icon">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M3 7h13l3 4v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" stroke="currentColor" strokeWidth="1.6" fill="none"/>
                <path d="M16 7v4h3" stroke="currentColor" strokeWidth="1.6" fill="none"/>
                <circle cx="7.5" cy="19" r="1.5" stroke="currentColor" strokeWidth="1.6" fill="none"/>
                <circle cx="17.5" cy="19" r="1.5" stroke="currentColor" strokeWidth="1.6" fill="none"/>
              </svg>
            </span>
            Sales & Delivery
          </span>
        </NavLink>
      </nav>
      {user && (
        <div className="sidebar-footer">
          <div className="user-card">
            {!user.profilePicUrl ? (
              <div className="avatar">{user.initials}</div>
            ) : (
              <img className="avatar-img" src={user.profilePicUrl} alt="Profile" />
            )}
            <div className="user-meta">
              <strong>{user.fullName}</strong>
              <span>{user.role}</span>
              <small>
                {user.position} - {user.department}
              </small>
            </div>
          </div>
          <a className="pill ghost signout-link" href="/logout">
            <span className="signout-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24">
                <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" />
                <path d="M10 17l5-5-5-5" />
                <path d="M15 12H3" />
              </svg>
            </span>
            Sign out
          </a>
        </div>
      )}
    </aside>
  )
}

export default Sidebar
