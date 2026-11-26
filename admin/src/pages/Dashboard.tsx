import { useEffect, useState } from 'react'
import { api } from '../api'
import type { DashboardSummary, InspectorStat, ProblemLocation } from '../types'
import { Avatar, Card, Empty, ErrorBox, Icon, PageHeader } from '../ui'
import type { IconName } from '../ui'

export default function DashboardPage() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null)
  const [inspectors, setInspectors] = useState<InspectorStat[]>([])
  const [problems, setProblems] = useState<ProblemLocation[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([api.summary('month'), api.inspectorStats(), api.problemLocations()])
      .then(([s, i, p]) => {
        setSummary(s)
        setInspectors(i)
        setProblems(p)
      })
      .catch((e: Error) => setError(e.message))
  }, [])

  const stats: { label: string; value?: number; icon: IconName; tone: string }[] = [
    { label: 'Визитов за месяц', value: summary?.totalVisits, icon: 'visits', tone: 'accent' },
    { label: 'Выполнено вовремя', value: summary?.completedOnTime, icon: 'check', tone: 'ok' },
    { label: 'Просрочено', value: summary?.overdue, icon: 'clock', tone: 'danger' },
    { label: 'Выявлено проблем', value: summary?.findings, icon: 'alert', tone: 'warn' },
  ]
  const maxFindings = Math.max(1, ...problems.map((p) => p.findings))

  return (
    <>
      <PageHeader title="Дашборд" subtitle="Сводка по выездам за последний месяц" />
      <ErrorBox error={error} />

      <div className="stat-cards">
        {stats.map((s) => (
          <div className="stat" key={s.label}>
            <div className={`stat-icon tone-${s.tone}`}><Icon name={s.icon} /></div>
            <div>
              <div className="value">{s.value ?? '—'}</div>
              <div className="label">{s.label}</div>
            </div>
          </div>
        ))}
      </div>

      <div className="row">
        <Card title="Рейтинг инспекторов" flush>
          {inspectors.length === 0 ? (
            <Empty text="Нет закрытых визитов" icon="users" />
          ) : (
            <div className="table-wrap">
              <table>
                <thead><tr><th>Инспектор</th><th className="num">Закрыто</th><th className="num">Откл. от плана</th></tr></thead>
                <tbody>
                  {inspectors.map((s, i) => (
                    <tr key={s.inspectorId}>
                      <td>
                        <div className="cell-user">
                          <Avatar name={s.fullName} className={`rank${i === 0 ? ' r1' : ''}`} />
                          <span className="cell-title">{s.fullName}</span>
                        </div>
                      </td>
                      <td className="num">{s.completedVisits}</td>
                      <td className="num">
                        {s.avgCompletionMinutes == null
                          ? '—'
                          : `${s.avgCompletionMinutes > 0 ? '+' : ''}${s.avgCompletionMinutes} мин`}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>

        <Card title="Объекты с проблемами (топ-10)" flush>
          {problems.length === 0 ? (
            <Empty text="Проблем не зафиксировано" icon="check" />
          ) : (
            <div className="bar-list">
              {problems.map((p) => (
                <div className="bar-item" key={p.locationId}>
                  <div className="bar-top"><span>{p.name}</span><b>{p.findings}</b></div>
                  <div className="bar-track"><div className="bar-fill" style={{ width: `${(p.findings / maxFindings) * 100}%` }} /></div>
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>
    </>
  )
}
