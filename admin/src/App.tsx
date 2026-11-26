import { useState } from 'react'
import DashboardPage from './pages/Dashboard'
import UsersPage from './pages/Users'
import LocationsPage from './pages/Locations'
import TemplatesPage from './pages/Templates'
import VisitsPage from './pages/Visits'
import { Icon, Avatar } from './ui'
import type { IconName } from './ui'

type Tab = 'dashboard' | 'visits' | 'templates' | 'locations' | 'users'

const TABS: { id: Tab; label: string; icon: IconName }[] = [
  { id: 'dashboard', label: 'Дашборд', icon: 'dashboard' },
  { id: 'visits', label: 'Визиты', icon: 'visits' },
  { id: 'templates', label: 'Шаблоны', icon: 'templates' },
  { id: 'locations', label: 'Объекты', icon: 'locations' },
  { id: 'users', label: 'Персонал', icon: 'users' },
]

// Навигация — простые вкладки без react-router (минимальность намеренная).
// TODO(делегирование): при росте админки можно перейти на react-router-dom.
export default function App() {
  const [tab, setTab] = useState<Tab>('dashboard')

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark"><Icon name="logo" /></span>
          <div>
            <div className="brand-name">VisitsRecording</div>
            <div className="brand-sub">Плазма Динамика</div>
          </div>
        </div>

        <nav className="nav">
          <div className="nav-label">Меню</div>
          {TABS.map((t) => (
            <button
              key={t.id}
              className={tab === t.id ? 'nav-item active' : 'nav-item'}
              onClick={() => setTab(t.id)}
            >
              <Icon name={t.icon} />
              {t.label}
            </button>
          ))}
        </nav>

        {/* TODO(аутентификация): подставлять реального пользователя после появления /api/auth/login */}
        <div className="sidebar-foot">
          <Avatar name="Admin" />
          <div>
            <div className="who">admin</div>
            <div className="role">Администратор</div>
          </div>
        </div>
      </aside>

      <main className="content">
        <div className="content-inner">
          {tab === 'dashboard' && <DashboardPage />}
          {tab === 'visits' && <VisitsPage />}
          {tab === 'templates' && <TemplatesPage />}
          {tab === 'locations' && <LocationsPage />}
          {tab === 'users' && <UsersPage />}
        </div>
      </main>
    </div>
  )
}
