import { useEffect, useState } from 'react'
import { api } from '../api'
import type { FieldType, Template, TemplateField } from '../types'
import { Button, Card, Empty, ErrorBox, Icon, PageHeader, Select, TextInput } from '../ui'

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
      <PageHeader title="Шаблоны" subtitle="Чек-листы, по которым инспекторы проводят проверки">
        <Button onClick={() => setEditing('new')}><Icon name="plus" />Создать шаблон</Button>
      </PageHeader>
      <ErrorBox error={error} />
      {templates.length === 0 ? (
        <Card><Empty text="Шаблонов пока нет" icon="templates" /></Card>
      ) : (
        <div className="tpl-grid">
          {templates.map((t) => (
            <div className="tpl-card" key={t.id}>
              <div className="tpl-top">
                <h3>{t.name}</h3>
                <span className="badge role plain">v{t.version}</span>
              </div>
              <div className="tpl-desc">{t.description || 'Без описания'}</div>
              <div className="tpl-meta">
                <span className="badge plain">{t.fields.length} пунктов</span>
                {t.fields.some((f) => f.requirePhotoOnIssue) && <span className="badge plain">Фото при проблеме</span>}
              </div>
              <div className="actions">
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
              </div>
            </div>
          ))}
        </div>
      )}
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
    <>
      <PageHeader
        title={template ? `Редактирование: ${template.name}` : 'Новый шаблон'}
        subtitle={template ? `Текущая версия v${template.version}. Сохранение создаст новую версию — старые визиты не изменятся.` : 'Опишите пункты, которые должен заполнить инспектор'}
      >
        <Button type="button" className="secondary" onClick={onCancel}><Icon name="back" />К списку</Button>
      </PageHeader>

      <form className="form" onSubmit={save}>
        <Card title="Основное">
          <div className="form">
            <label>Название
              <TextInput value={name} onChange={(e) => setName(e.target.value)} required placeholder="Еженедельный осмотр оборудования" />
            </label>
            <label>Инструкция для инспектора
              <textarea className="input" value={description} onChange={(e) => setDescription(e.target.value)} />
            </label>
          </div>
        </Card>

        <Card title={`Пункты проверки (${fields.length})`}>
          <div className="form">
            {fields.map((f, i) => (
              <div key={i} className="field-card">
                <div className="field-num">{i + 1}</div>
                <div className="field-body">
                  <div className="form-grid">
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
                  <div className="field-foot">
                    <label className="toggle">
                      <input type="checkbox" checked={f.isRequired} onChange={(e) => setField(i, { isRequired: e.target.checked })} />
                      Обязательный
                    </label>
                    <label className="toggle">
                      <input type="checkbox" checked={f.requirePhotoOnIssue} onChange={(e) => setField(i, { requirePhotoOnIssue: e.target.checked })} />
                      Фото при проблеме
                    </label>
                    <Button
                      type="button"
                      className="danger small"
                      disabled={fields.length === 1}
                      onClick={() => setFields((prev) => prev.filter((_, idx) => idx !== i))}
                    >
                      Удалить
                    </Button>
                  </div>
                </div>
              </div>
            ))}
            <div className="actions">
              <Button type="button" className="secondary" onClick={() => setFields((prev) => [...prev, emptyField(prev.length + 1)])}>
                <Icon name="plus" />Добавить пункт
              </Button>
            </div>
          </div>
        </Card>

        <ErrorBox error={error} />
        <div className="actions">
          <Button type="submit">{template ? 'Сохранить как новую версию' : 'Создать шаблон'}</Button>
          <Button type="button" className="secondary" onClick={onCancel}>Отмена</Button>
        </div>
      </form>
    </>
  )
}
