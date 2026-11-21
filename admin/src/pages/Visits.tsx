import { useEffect, useState } from 'react'
import { api } from '../api'
import type { Location, Template, User, Visit, VisitDetail, VisitStatus } from '../types'
import { Button, Card, Empty, ErrorBox, Select, TextInput } from '../ui'

const STATUS_LABELS: Record<VisitStatus, string> = {
  PLANNED: 'Запланирован',
  IN_PROGRESS: 'В работе',
  COMPLETED: 'Завершён',
  CANCELLED: 'Отменён',
  OVERDUE: 'Просрочен',
}

const fmt = (iso: string) => new Date(iso).toLocaleString('ru-RU')

// Визиты: список с фильтрами + назначение + карточка результата.
// TODO(делегирование): заменить таблицу на календарь (например, FullCalendar).
export default function VisitsPage() {
  const [visits, setVisits] = useState<Visit[]>([])
  const [templates, setTemplates] = useState<Template[]>([])
  const [locations, setLocations] = useState<Location[]>([])
  const [inspectors, setInspectors] = useState<User[]>([])
  const [error, setError] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState('')
  const [detail, setDetail] = useState<VisitDetail | null>(null)

  // Форма назначения
  const [templateId, setTemplateId] = useState('')
  const [locationId, setLocationId] = useState('')
  const [inspectorId, setInspectorId] = useState('')
  const [scheduledAt, setScheduledAt] = useState('')

  const load = () => {
    const params: Record<string, string> = {}
    if (statusFilter) params.status = statusFilter
    api.visits(params).then(setVisits).catch((e: Error) => setError(e.message))
  }

  useEffect(() => {
    load()
    api.templates().then(setTemplates).catch(() => {})
    api.locations().then(setLocations).catch(() => {})
    api.users().then((users) => setInspectors(users.filter((u) => u.role === 'INSPECTOR' && u.isActive))).catch(() => {})
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { load() }, [statusFilter]) // eslint-disable-line react-hooks/exhaustive-deps

  const create = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    try {
      await api.createVisit({
        templateId,
        locationId,
        inspectorId,
        scheduledAt: new Date(scheduledAt).toISOString(),
      })
      setTemplateId(''); setLocationId(''); setInspectorId(''); setScheduledAt('')
      load()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  const openDetail = async (v: Visit) => {
    setError(null)
    try {
      setDetail(await api.visitDetail(v.id))
    } catch (err) {
      setError((err as Error).message)
    }
  }

  const cancel = async (v: Visit) => {
    setError(null)
    try {
      await api.cancelVisit(v.id)
      setDetail(null)
      load()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  if (detail) {
    return <VisitDetailView detail={detail} onClose={() => setDetail(null)} onCancel={() => cancel(detail.visit)} />
  }

  return (
    <>
      <Card title="Назначить визит">
        <form className="form" onSubmit={create}>
          <div className="row">
            <label>Шаблон
              <Select value={templateId} onChange={(e) => setTemplateId(e.target.value)} required>
                <option value="">— выбрать —</option>
                {templates.map((t) => <option key={t.id} value={t.id}>{t.name} (v{t.version})</option>)}
              </Select>
            </label>
            <label>Объект
              <Select value={locationId} onChange={(e) => setLocationId(e.target.value)} required>
                <option value="">— выбрать —</option>
                {locations.map((l) => <option key={l.id} value={l.id}>{l.name}</option>)}
              </Select>
            </label>
          </div>
          <div className="row">
            <label>Инспектор
              <Select value={inspectorId} onChange={(e) => setInspectorId(e.target.value)} required>
                <option value="">— выбрать —</option>
                {inspectors.map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}
              </Select>
            </label>
            <label>Дата и время
              <TextInput type="datetime-local" value={scheduledAt} onChange={(e) => setScheduledAt(e.target.value)} required />
            </label>
          </div>
          <Button type="submit">Назначить</Button>
        </form>
      </Card>

      <ErrorBox error={error} />

      <Card title="Визиты">
        <div className="actions" style={{ marginBottom: 12 }}>
          <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} style={{ maxWidth: 240 }}>
            <option value="">Все статусы</option>
            {Object.entries(STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </Select>
        </div>
        {visits.length === 0 ? (
          <Empty text="Визитов нет" />
        ) : (
          <table>
            <thead><tr><th>Название</th><th>План</th><th>Статус</th><th></th></tr></thead>
            <tbody>
              {visits.map((v) => (
                <tr key={v.id} className="clickable" onClick={() => openDetail(v)}>
                  <td>{v.name}</td>
                  <td>{fmt(v.scheduledAt)}</td>
                  <td><span className={`badge ${v.status}`}>{STATUS_LABELS[v.status]}</span></td>
                  <td>
                    {v.status === 'PLANNED' && (
                      <Button className="danger small" onClick={(e) => { e.stopPropagation(); cancel(v) }}>
                        Отменить
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </>
  )
}

function VisitDetailView({
  detail, onClose, onCancel,
}: {
  detail: VisitDetail
  onClose: () => void
  onCancel: () => void
}) {
  const { visit, answers, media } = detail
  return (
    <Card title={`Визит: ${visit.name}`}>
      <p>
        <span className={`badge ${visit.status}`}>{STATUS_LABELS[visit.status]}</span>{' '}
        <span className="muted">план: {fmt(visit.scheduledAt)}</span>
        {visit.completedAt && <span className="muted"> · закрыт: {fmt(visit.completedAt)}</span>}
        {visit.factLat != null && (
          <span className="muted"> · геометка: {visit.factLat.toFixed(5)}, {visit.factLon?.toFixed(5)}</span>
        )}
      </p>

      <h3 style={{ fontSize: 14 }}>Ответы инспектора</h3>
      {answers.length === 0 ? (
        <Empty text="Ответов пока нет (визит не заполнен)" />
      ) : (
        <table>
          <thead><tr><th>Пункт</th><th>Ответ</th><th>Проблема</th><th>Комментарий</th></tr></thead>
          <tbody>
            {answers.map((a) => (
              <tr key={a.id}>
                <td>{a.fieldTitle}</td>
                <td>{a.value}</td>
                <td>{a.hasIssue ? <span className="badge issue">проблема</span> : ''}</td>
                <td>{a.comment ?? ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h3 style={{ fontSize: 14 }}>Фото ({media.length})</h3>
      {/* TODO(медиа): бек вернёт presigned URL к MinIO — показывать превью.
          Пока выводим ключи хранилища. */}
      {media.length === 0 ? (
        <Empty text="Фото не прикреплены" />
      ) : (
        <ul className="muted">
          {media.map((m) => <li key={m.id}>{m.s3Key} ({m.fileType})</li>)}
        </ul>
      )}

      <div className="actions">
        {visit.status === 'PLANNED' && <Button className="danger" onClick={onCancel}>Отменить визит</Button>}
        <Button className="secondary" onClick={onClose}>К списку</Button>
      </div>
    </Card>
  )
}
