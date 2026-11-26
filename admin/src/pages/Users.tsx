import { useEffect, useState } from 'react'
import { api } from '../api'
import type { User, UserRole } from '../types'
import { Avatar, Button, Card, Empty, ErrorBox, Icon, Modal, PageHeader, Select, TextInput } from '../ui'

export default function UsersPage() {
  const [users, setUsers] = useState<User[]>([])
  const [error, setError] = useState<string | null>(null)
  const [fullName, setFullName] = useState('')
  const [login, setLogin] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<UserRole>('INSPECTOR')
  const [showForm, setShowForm] = useState(false)

  const load = () => api.users().then(setUsers).catch((e: Error) => setError(e.message))
  useEffect(() => { load() }, [])

  const create = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    try {
      await api.createUser({ fullName, login, password, role })
      setFullName(''); setLogin(''); setPassword('')
      setShowForm(false)
      load()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  const toggleActive = async (u: User) => {
    setError(null)
    try {
      await api.updateUser(u.id, { isActive: !u.isActive })
      load()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  return (
    <>
      <PageHeader title="Персонал" subtitle="Инспекторы и администраторы системы">
        <Button onClick={() => setShowForm(true)}><Icon name="plus" />Добавить сотрудника</Button>
      </PageHeader>

      <ErrorBox error={error} />

      <Card flush>
        {users.length === 0 ? (
          <Empty text="Сотрудников пока нет" icon="users" />
        ) : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Сотрудник</th><th>Роль</th><th>Статус</th><th></th></tr></thead>
              <tbody>
                {users.map((u) => (
                  <tr key={u.id}>
                    <td>
                      <div className="cell-user">
                        <Avatar name={u.fullName} />
                        <div>
                          <div className="cell-title">{u.fullName}</div>
                          <div className="cell-sub">@{u.login}</div>
                        </div>
                      </div>
                    </td>
                    <td><span className="badge role plain">{u.role === 'ADMIN' ? 'Админ' : 'Инспектор'}</span></td>
                    <td>
                      {u.isActive
                        ? <span className="badge COMPLETED">Активен</span>
                        : <span className="badge CANCELLED">Деактивирован</span>}
                    </td>
                    <td className="cell-actions">
                      <Button className="secondary small" onClick={() => toggleActive(u)}>
                        {u.isActive ? 'Деактивировать' : 'Активировать'}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {showForm && (
        <Modal title="Новый сотрудник" onClose={() => setShowForm(false)}>
          <form className="form" onSubmit={create}>
            <label>ФИО
              <TextInput value={fullName} onChange={(e) => setFullName(e.target.value)} required autoFocus />
            </label>
            <div className="form-grid">
              <label>Логин
                <TextInput value={login} onChange={(e) => setLogin(e.target.value)} required />
              </label>
              <label>Роль
                <Select value={role} onChange={(e) => setRole(e.target.value as UserRole)}>
                  <option value="INSPECTOR">Инспектор</option>
                  <option value="ADMIN">Админ</option>
                </Select>
              </label>
            </div>
            <label>Пароль (минимум 6 символов)
              <TextInput type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={6} />
            </label>
            <div className="actions end">
              <Button type="button" className="secondary" onClick={() => setShowForm(false)}>Отмена</Button>
              <Button type="submit">Создать</Button>
            </div>
          </form>
        </Modal>
      )}
    </>
  )
}
