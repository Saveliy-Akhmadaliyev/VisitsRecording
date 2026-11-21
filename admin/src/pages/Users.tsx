import { useEffect, useState } from 'react'
import { api } from '../api'
import type { User, UserRole } from '../types'
import { Button, Card, ErrorBox, Select, TextInput } from '../ui'

export default function UsersPage() {
  const [users, setUsers] = useState<User[]>([])
  const [error, setError] = useState<string | null>(null)
  const [fullName, setFullName] = useState('')
  const [login, setLogin] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<UserRole>('INSPECTOR')

  const load = () => api.users().then(setUsers).catch((e: Error) => setError(e.message))
  useEffect(() => { load() }, [])

  const create = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    try {
      await api.createUser({ fullName, login, password, role })
      setFullName(''); setLogin(''); setPassword('')
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
      <Card title="Создать сотрудника">
        <form className="form" onSubmit={create}>
          <label>ФИО
            <TextInput value={fullName} onChange={(e) => setFullName(e.target.value)} required />
          </label>
          <label>Логин
            <TextInput value={login} onChange={(e) => setLogin(e.target.value)} required />
          </label>
          <label>Пароль (минимум 6 символов)
            <TextInput type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={6} />
          </label>
          <label>Роль
            <Select value={role} onChange={(e) => setRole(e.target.value as UserRole)}>
              <option value="INSPECTOR">Инспектор</option>
              <option value="ADMIN">Админ</option>
            </Select>
          </label>
          <Button type="submit">Создать</Button>
        </form>
      </Card>

      <ErrorBox error={error} />

      <Card title="Персонал">
        <table>
          <thead><tr><th>ФИО</th><th>Логин</th><th>Роль</th><th>Статус</th><th></th></tr></thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td>{u.fullName}</td>
                <td>{u.login}</td>
                <td>{u.role === 'ADMIN' ? 'Админ' : 'Инспектор'}</td>
                <td>{u.isActive ? <span className="badge COMPLETED">активен</span> : <span className="badge CANCELLED">деактивирован</span>}</td>
                <td>
                  <Button className="secondary small" onClick={() => toggleActive(u)}>
                    {u.isActive ? 'Деактивировать' : 'Активировать'}
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </>
  )
}
