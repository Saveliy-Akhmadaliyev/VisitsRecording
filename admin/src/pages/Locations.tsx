import { useEffect, useState } from 'react'
import { api } from '../api'
import type { Location } from '../types'
import { Button, Card, Empty, ErrorBox, Icon, Modal, PageHeader, TextInput } from '../ui'

export default function LocationsPage() {
  const [locations, setLocations] = useState<Location[]>([])
  const [error, setError] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [address, setAddress] = useState('')
  const [showForm, setShowForm] = useState(false)

  const load = () => api.locations().then(setLocations).catch((e: Error) => setError(e.message))
  useEffect(() => { load() }, [])

  const create = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    try {
      await api.createLocation({ name, address })
      setName(''); setAddress('')
      setShowForm(false)
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
      <PageHeader title="Объекты" subtitle="Места, где проводятся проверки">
        <Button onClick={() => setShowForm(true)}><Icon name="plus" />Добавить объект</Button>
      </PageHeader>

      <ErrorBox error={error} />

      <Card flush>
        {locations.length === 0 ? (
          <Empty text="Объектов пока нет" icon="locations" />
        ) : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Название</th><th>Адрес</th><th></th></tr></thead>
              <tbody>
                {locations.map((l) => (
                  <tr key={l.id}>
                    <td className="cell-title">{l.name}</td>
                    <td>{l.address}</td>
                    <td className="cell-actions">
                      <Button className="danger small" onClick={() => remove(l)}>Удалить</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {showForm && (
        <Modal title="Новый объект" onClose={() => setShowForm(false)}>
          <form className="form" onSubmit={create}>
            <label>Название
              <TextInput value={name} onChange={(e) => setName(e.target.value)} required placeholder="Цех №4" autoFocus />
            </label>
            <label>Адрес
              <TextInput value={address} onChange={(e) => setAddress(e.target.value)} required placeholder="ул. Заводская, 1" />
            </label>
            <div className="actions end">
              <Button type="button" className="secondary" onClick={() => setShowForm(false)}>Отмена</Button>
              <Button type="submit">Добавить</Button>
            </div>
          </form>
        </Modal>
      )}
    </>
  )
}
