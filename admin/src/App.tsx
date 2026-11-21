import { useState } from 'react'
import DashboardPage from './pages/Dashboard'
import UsersPage from './pages/Users'
import LocationsPage from './pages/Locations'
import TemplatesPage from './pages/Templates'
import VisitsPage from './pages/Visits'

type Tab = 'dashboard' | 'visits' | 'templates' | 'locations' | 'users'

const TABS: { id: Tab; label: string }[] = [
  { id: 'dashboard', label: 'Дашборд' },
  { id: 'visits', label: 'Визиты' },
  { id: 'templates', label: 'Шаблоны' },
  { id: 'locations', label: 'Объекты' },
  { id: 'users', label: 'Персонал' },
]

// Навигация — простые вкладки без react-router (минимальность намеренная).
// TODO(делегирование): при росте админки можно перейти на react-router-dom.
export default function App() {
  const [tab, setTab] = useState<Tab>('dashboard')

  return (
    <div className="app">
      <header className="header">
        <h1>VisitsRecording</h1>
        <nav className="tabs">
          {TABS.map((t) => (
            <button
              key={t.id}
              className={tab === t.id ? 'tab active' : 'tab'}
              onClick={() => setTab(t.id)}
            >
              {t.label}
            </button>
          ))}
        </nav>
      </header>
      <main className="main">
        {tab === 'dashboard' && <DashboardPage />}
        {tab === 'visits' && <VisitsPage />}
        {tab === 'templates' && <TemplatesPage />}
        {tab === 'locations' && <LocationsPage />}
        {tab === 'users' && <UsersPage />}
      </main>
    </div>
  )
}
