// Минимальный клиент REST API бекенда. Все пути — через Vite-прокси /api -> :8080.
// TODO(аутентификация): добавить JWT в заголовок Authorization после реализации /api/auth/login.

import type {
  DashboardSummary, InspectorStat, Location, ProblemLocation, Template,
  TemplateField, User, UserRole, Visit, VisitDetail,
} from './types'

const BASE = '/api/admin'

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const res = await fetch(BASE + path, {
    method,
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) {
    let message = `HTTP ${res.status}`
    try {
      const data = (await res.json()) as { error?: string }
      if (data.error) message = data.error
    } catch {
      // тело не JSON — оставляем HTTP-код
    }
    throw new Error(message)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

export interface LocationSave {
  name: string
  address: string
  targetLat?: number | null
  targetLon?: number | null
}

export interface TemplateSave {
  name: string
  description: string
  fields: TemplateField[]
}

export interface VisitCreate {
  templateId: string
  locationId: string
  inspectorId: string
  scheduledAt: string // ISO-8601
  name?: string | null
}

export const api = {
  // Пользователи
  users: () => request<User[]>('GET', '/users'),
  createUser: (data: { fullName: string; login: string; password: string; role: UserRole }) =>
    request<User>('POST', '/users', data),
  updateUser: (id: string, data: { fullName?: string; isActive?: boolean }) =>
    request<User>('PATCH', `/users/${id}`, data),

  // Объекты
  locations: () => request<Location[]>('GET', '/locations'),
  createLocation: (data: LocationSave) => request<Location>('POST', '/locations', data),
  updateLocation: (id: string, data: LocationSave) => request<Location>('PUT', `/locations/${id}`, data),
  deleteLocation: (id: string) => request<void>('DELETE', `/locations/${id}`),

  // Шаблоны (PUT создаёт новую версию на беке)
  templates: () => request<Template[]>('GET', '/templates'),
  template: (id: string) => request<Template>('GET', `/templates/${id}`),
  createTemplate: (data: TemplateSave) => request<Template>('POST', '/templates', data),
  updateTemplate: (id: string, data: TemplateSave) => request<Template>('PUT', `/templates/${id}`, data),
  archiveTemplate: (id: string) => request<void>('DELETE', `/templates/${id}`),

  // Визиты
  visits: (params: Record<string, string> = {}) => {
    const qs = new URLSearchParams(params).toString()
    return request<Visit[]>('GET', `/visits${qs ? `?${qs}` : ''}`)
  },
  createVisit: (data: VisitCreate) => request<Visit>('POST', '/visits', data),
  updateVisit: (id: string, data: { scheduledAt?: string; inspectorId?: string }) =>
    request<Visit>('PATCH', `/visits/${id}`, data),
  cancelVisit: (id: string) => request<void>('DELETE', `/visits/${id}`),
  visitDetail: (id: string) => request<VisitDetail>('GET', `/visits/${id}`),

  // Дашборд
  summary: (period: 'week' | 'month' | 'all' = 'month') =>
    request<DashboardSummary>('GET', `/dashboard/summary?period=${period}`),
  inspectorStats: () => request<InspectorStat[]>('GET', '/dashboard/inspectors-stats'),
  problemLocations: () => request<ProblemLocation[]>('GET', '/dashboard/problem-locations'),
}
