import { useEffect, useState } from 'react'
import { api } from '../api'
import type { DashboardSummary, InspectorStat, ProblemLocation } from '../types'
import { Card, Empty, ErrorBox } from '../ui'

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

  if (error) return <ErrorBox error={error} />

  return (
    <>
      <div className="stat-cards">
        <div className="stat"><div className="value">{summary?.totalVisits ?? '—'}</div><div className="label">Визитов за месяц</div></div>
        <div className="stat"><div className="value">{summary?.completedOnTime ?? '—'}</div><div className="label">Выполнено вовремя</div></div>
        <div className="stat"><div className="value">{summary?.overdue ?? '—'}</div><div className="label">Просрочено</div></div>
        <div className="stat"><div className="value">{summary?.findings ?? '—'}</div><div className="label">Выявлено проблем</div></div>
      </div>

      <div className="row">
        <Card title="Рейтинг инспекторов">
          {inspectors.length === 0 ? (
            <Empty text="Нет закрытых визитов" />
          ) : (
            <table>
              <thead><tr><th>Инспектор</th><th>Закрыто</th><th>Отклонение от плана</th></tr></thead>
              <tbody>
                {inspectors.map((s) => (
                  <tr key={s.inspectorId}>
                    <td>{s.fullName}</td>
                    <td>{s.completedVisits}</td>
                    <td>
                      {s.avgCompletionMinutes == null
                        ? '—'
                        : `${s.avgCompletionMinutes > 0 ? '+' : ''}${s.avgCompletionMinutes} мин`}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Card>

        <Card title="Объекты с проблемами (топ-10)">
          {problems.length === 0 ? (
            <Empty text="Проблем не зафиксировано" />
          ) : (
            <table>
              <thead><tr><th>Объект</th><th>Проблем</th></tr></thead>
              <tbody>
                {problems.map((p) => (
                  <tr key={p.locationId}><td>{p.name}</td><td>{p.findings}</td></tr>
                ))}
              </tbody>
            </table>
          )}
        </Card>
      </div>
    </>
  )
}
