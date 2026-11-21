import { useEffect, useState } from 'react'
import { api } from '../api'
import type { Location } from '../types'
import { Button, Card, ErrorBox, TextInput } from '../ui'

export default function LocationsPage() {
  const [locations, setLocations] = useState<Location[]>([])
  const [error, setError] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [address, setAddress] = useState('')

  const load = () => api.locations().then(setLocations).catch((e: Error) => setError(e.message))
  useEffect(() => { load() }, [])

  const create = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    try {
      await api.createLocation({ name, address })
      setName(''); setAddress('')
      load()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  const remove = async (l: Location) => {
    setError(null)
    try {
      await api.deleteLocation(l.id)
      load()
    } catch (err) {
      setError((err as Error).message) // 409 «по объекту есть визиты» придёт сюда
    }
  }

  return (
    <>
      <Card title="Добавить объект">
        <form className="form" onSubmit={create}>
          <label>Название
            <TextInput value={name} onChange={(e) => setName(e.target.value)} required placeholder="Цех №4" />
          </label>
          <label>Адрес
            <TextInput value={address} onChange={(e) => setAddress(e.target.value)} required placeholder="ул. Заводская, 1" />
          </label>
          <Button type="submit">Добавить</Button>
        </form>
      </Card>

      <ErrorBox error={error} />

      <Card title="Объекты">
        <table>
          <thead><tr><th>Название</th><th>Адрес</th><th></th></tr></thead>
          <tbody>
            {locations.map((l) => (
              <tr key={l.id}>
                <td>{l.name}</td>
                <td>{l.address}</td>
                <td><Button className="danger small" onClick={() => remove(l)}>Удалить</Button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </>
  )
}
