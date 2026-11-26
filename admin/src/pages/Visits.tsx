import { useEffect, useMemo, useState } from 'react'
import { api } from '../api'
import type { Location, Template, User, Visit, VisitDetail, VisitStatus } from '../types'
import { Avatar, Button, Card, Empty, ErrorBox, Icon, Modal, PageHeader, Select, TextInput } from '../ui'

const STATUS_LABELS: Record<VisitStatus, string> = {
  PLANNED: 'Запланирован',
  IN_PROGRESS: 'В работе',
  COMPLETED: 'Завершён',
  CANCELLED: 'Отменён',
  OVERDUE: 'Просрочен',
}

const fmt = (iso: string) => new Date(iso).toLocaleString('ru-RU', { dateStyle: 'medium', timeStyle: 'short' })

// Визиты: список с фильтрами + назначение + карточка результата.
// TODO(делегирование): заменить таблицу на календарь (например, FullCalendar).
export default function VisitsPage() {
  const [visits, setVisits] = useState<Visit[]>([])
  const [templates, setTemplates] = useState<Template[]>([])
  const [locations, setLocations] = useState<Location[]>([])
  const [users, setUsers] = useState<User[]>([])
  const [error, setError] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState('')
  const [detail, setDetail] = useState<VisitDetail | null>(null)
  const [showForm, setShowForm] = useState(false)

  // Форма назначения
  const [templateId, setTemplateId] = useState('')
  const [locationId, setLocationId] = useState('')
  const [inspectorId, setInspectorId] = useState('')
  const [scheduledAt, setScheduledAt] = useState('')

  const inspectors = useMemo(() => users.filter((u) => u.role === 'INSPECTOR' && u.isActive), [users])
  const locationName = (id: string) => locations.find((l) => l.id === id)?.name ?? '—'
  const userName = (id: string) => users.find((u) => u.id === id)?.fullName ?? '—'

  const load = () => {
    const params: Record<string, string> = {}
    if (statusFilter) params.status = statusFilter
    api.visits(params).then(setVisits).catch((e: Error) => setError(e.message))
  }

  useEffect(() => {
    load()
    api.templates().then(setTemplates).catch(() => {})
    api.locations().then(setLocations).catch(() => {})
    api.users().then(setUsers).catch(() => {})
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
      setShowForm(false)
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
    return (
      <VisitDetailView
        detail={detail}
        locationName={locationName(detail.visit.locationId)}
        inspectorName={userName(detail.visit.inspectorId)}
        onClose={() => setDetail(null)}
        onCancel={() => cancel(detail.visit)}
      />
    )
  }

  return (
    <>
      <PageHeader title="Визиты" subtitle="Назначайте выезды и смотрите результаты проверок">
        <Button onClick={() => setShowForm(true)}><Icon name="plus" />Назначить визит</Button>
      </PageHeader>

      <ErrorBox error={error} />

      <div className="chips">
        <button className={statusFilter === '' ? 'chip active' : 'chip'} onClick={() => setStatusFilter('')}>Все</button>
        {Object.entries(STATUS_LABELS).map(([value, label]) => (
          <button key={value} className={statusFilter === value ? 'chip active' : 'chip'} onClick={() => setStatusFilter(value)}>
            {label}
          </button>
        ))}
      </div>

      <Card flush>
        {visits.length === 0 ? (
          <Empty text="Визитов нет" icon="visits" />
        ) : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Визит</th><th>Инспектор</th><th>План</th><th>Статус</th><th></th></tr></thead>
              <tbody>
                {visits.map((v) => (
                  <tr key={v.id} className="clickable" onClick={() => openDetail(v)}>
                    <td>
                      <div className="cell-title">{v.name}</div>
                      <div className="cell-sub">{locationName(v.locationId)}</div>
                    </td>
                    <td>
                      <div className="cell-user">
                        <Avatar name={userName(v.inspectorId)} small />
                        {userName(v.inspectorId)}
                      </div>
                    </td>
                    <td>{fmt(v.scheduledAt)}</td>
                    <td><span className={`badge ${v.status}`}>{STATUS_LABELS[v.status]}</span></td>
                    <td className="cell-actions">
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
          </div>
        )}
      </Card>

      {showForm && (
        <Modal title="Назначить визит" onClose={() => setShowForm(false)}>
          <form className="form" onSubmit={create}>
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
            <div className="form-grid">
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
            <div className="actions end">
              <Button type="button" className="secondary" onClick={() => setShowForm(false)}>Отмена</Button>
              <Button type="submit">Назначить</Button>
            </div>
          </form>
        </Modal>
      )}
    </>
  )
}

function VisitDetailView({
  detail, locationName, inspectorName, onClose, onCancel,
}: {
  detail: VisitDetail
  locationName: string
  inspectorName: string
  onClose: () => void
  onCancel: () => void
}) {
  const { visit, answers, media } = detail
  const issues = answers.filter((a) => a.hasIssue).length

  return (
    <>
      <Button className="secondary small back" onClick={onClose}><Icon name="back" />К списку</Button>
      <PageHeader title={visit.name} subtitle={`${locationName} · ${inspectorName}`}>
        <span className={`badge ${visit.status}`}>{STATUS_LABELS[visit.status]}</span>
        {visit.status === 'PLANNED' && <Button className="danger" onClick={onCancel}>Отменить визит</Button>}
      </PageHeader>

      <Card flush>
        <dl className="meta-grid" style={{ margin: 0 }}>
          <div><dt>План</dt><dd>{fmt(visit.scheduledAt)}</dd></div>
          <div><dt>Закрыт</dt><dd>{visit.completedAt ? fmt(visit.completedAt) : '—'}</dd></div>
          <div>
            <dt>Геометка</dt>
            <dd>{visit.factLat != null ? `${visit.factLat.toFixed(5)}, ${visit.factLon?.toFixed(5)}` : '—'}</dd>
          </div>
          <div><dt>Проблем выявлено</dt><dd>{issues}</dd></div>
        </dl>
      </Card>

      <Card title="Ответы инспектора" flush>
        {answers.length === 0 ? (
          <Empty text="Ответов пока нет (визит не заполнен)" icon="templates" />
        ) : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Пункт</th><th>Ответ</th><th>Проблема</th><th>Комментарий</th></tr></thead>
              <tbody>
                {answers.map((a) => (
                  <tr key={a.id} className={a.hasIssue ? 'issue-row' : ''}>
                    <td className="cell-title">{a.fieldTitle}</td>
                    <td>{a.value}</td>
                    <td>{a.hasIssue ? <span className="badge issue">Проблема</span> : ''}</td>
                    <td>{a.comment ?? ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card title={`Фото (${media.length})`} flush>
        {/* TODO(медиа): бек вернёт presigned URL к MinIO — показывать превью вместо плейсхолдеров. */}
        {media.length === 0 ? (
          <Empty text="Фото не прикреплены" icon="image" />
        ) : (
          <div className="photo-grid">
            {media.map((m) => (
              <div className="photo-tile" key={m.id} title={m.s3Key}>
                <Icon name="image" />
                <span>{m.fileType}</span>
              </div>
            ))}
          </div>
        )}
      </Card>
    </>
  )
}
