import { useEffect, useState } from 'react'
import { api } from '../api'
import type { FieldType, Template, TemplateField } from '../types'
import { Button, Card, Empty, ErrorBox, Select, TextInput } from '../ui'

const FIELD_TYPES: { value: FieldType; label: string }[] = [
  { value: 'TEXT', label: 'Текст' },
  { value: 'RATING_1_5', label: 'Оценка 1–5' },
  { value: 'YES_NO', label: 'Да / Нет' },
  { value: 'SELECT', label: 'Выбор из списка' },
]

const emptyField = (orderIndex: number): TemplateField => ({
  orderIndex,
  title: '',
  fieldType: 'TEXT',
  options: null,
  isRequired: false,
  requirePhotoOnIssue: false,
})

// Конструктор шаблонов: список + редактор (создание/обновление).
// PUT на беке создаёт НОВУЮ версию шаблона — старые визиты не ломаются.
export default function TemplatesPage() {
  const [templates, setTemplates] = useState<Template[]>([])
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<Template | 'new' | null>(null)

  const load = () => api.templates().then(setTemplates).catch((e: Error) => setError(e.message))
  useEffect(() => { load() }, [])

  if (editing !== null) {
    return (
      <TemplateEditor
        template={editing === 'new' ? null : editing}
        onDone={() => { setEditing(null); load() }}
        onCancel={() => setEditing(null)}
      />
    )
  }

  return (
    <>
      <ErrorBox error={error} />
      <Card title="Шаблоны проверок">
        <div className="actions" style={{ marginBottom: 12 }}>
          <Button onClick={() => setEditing('new')}>Создать шаблон</Button>
        </div>
        {templates.length === 0 ? (
          <Empty text="Шаблонов пока нет" />
        ) : (
          <table>
            <thead><tr><th>Название</th><th>Версия</th><th>Пунктов</th><th></th></tr></thead>
            <tbody>
              {templates.map((t) => (
                <tr key={t.id}>
                  <td>{t.name}</td>
                  <td>v{t.version}</td>
                  <td>{t.fields.length}</td>
                  <td className="actions">
                    <Button className="secondary small" onClick={() => setEditing(t)}>Редактировать</Button>
                    <Button
                      className="danger small"
                      onClick={async () => {
                        setError(null)
                        try { await api.archiveTemplate(t.id); load() } catch (e) { setError((e as Error).message) }
                      }}
                    >
                      Архивировать
                    </Button>
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

function TemplateEditor({
  template, onDone, onCancel,
}: {
  template: Template | null
  onDone: () => void
  onCancel: () => void
}) {
  const [name, setName] = useState(template?.name ?? '')
  const [description, setDescription] = useState(template?.description ?? '')
  const [fields, setFields] = useState<TemplateField[]>(
    template?.fields?.map((f) => ({ ...f, options: f.options ?? null })) ?? [emptyField(1)],
  )
  const [error, setError] = useState<string | null>(null)

  const setField = (i: number, patch: Partial<TemplateField>) => {
    setFields((prev) => prev.map((f, idx) => (idx === i ? { ...f, ...patch } : f)))
  }

  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    const payload = {
      name,
      description,
      fields: fields.map((f, i) => ({
        orderIndex: i + 1,
        title: f.title,
        fieldType: f.fieldType,
        options: f.fieldType === 'SELECT' ? (f.options ?? []) : null,
        isRequired: f.isRequired,
        requirePhotoOnIssue: f.requirePhotoOnIssue,
      })),
    }
    try {
      if (template) await api.updateTemplate(template.id, payload)
      else await api.createTemplate(payload)
      onDone()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  return (
    <Card title={template ? `Редактирование: ${template.name} (v${template.version})` : 'Новый шаблон'}>
      <form className="form" onSubmit={save}>
        <label>Название
          <TextInput value={name} onChange={(e) => setName(e.target.value)} required placeholder="Еженедельный осмотр оборудования" />
        </label>
        <label>Инструкция для инспектора
          <textarea className="input" value={description} onChange={(e) => setDescription(e.target.value)} />
        </label>

        <h3 style={{ fontSize: 14 }}>Пункты проверки</h3>
        {fields.map((f, i) => (
          <div key={i} className="card" style={{ padding: 12 }}>
            <div className="row">
              <label>Текст пункта
                <TextInput value={f.title} onChange={(e) => setField(i, { title: e.target.value })} required />
              </label>
              <label>Тип ответа
                <Select value={f.fieldType} onChange={(e) => setField(i, { fieldType: e.target.value as FieldType })}>
                  {FIELD_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                </Select>
              </label>
            </div>
            {f.fieldType === 'SELECT' && (
              <label>Варианты (через запятую)
                <TextInput
                  value={(f.options ?? []).join(', ')}
                  onChange={(e) => setField(i, { options: e.target.value.split(',').map((s) => s.trim()).filter(Boolean) })}
                  placeholder="Исправно, Требует ремонта, Критично"
                />
              </label>
            )}
            <div className="row" style={{ alignItems: 'center' }}>
              <label className="inline">
                <input type="checkbox" checked={f.isRequired} onChange={(e) => setField(i, { isRequired: e.target.checked })} />
                Обязательный
              </label>
              <label className="inline">
                <input type="checkbox" checked={f.requirePhotoOnIssue} onChange={(e) => setField(i, { requirePhotoOnIssue: e.target.checked })} />
                Требовать фото при проблеме
              </label>
              <Button
                type="button"
                className="danger small"
                disabled={fields.length === 1}
                onClick={() => setFields((prev) => prev.filter((_, idx) => idx !== i))}
              >
                Удалить пункт
              </Button>
            </div>
          </div>
        ))}
        <div className="actions">
          <Button type="button" className="secondary" onClick={() => setFields((prev) => [...prev, emptyField(prev.length + 1)])}>
            + Добавить пункт
          </Button>
        </div>

        <ErrorBox error={error} />
        <div className="actions">
          <Button type="submit">{template ? 'Сохранить (создаст новую версию)' : 'Создать'}</Button>
          <Button type="button" className="secondary" onClick={onCancel}>Отмена</Button>
        </div>
      </form>
    </Card>
  )
}
