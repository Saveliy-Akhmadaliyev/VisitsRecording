// Типы зеркалят DTO бекенда (backend/.../api/dto/Dtos.kt).
// При изменении API менять синхронно там и здесь.

export type UserRole = 'ADMIN' | 'INSPECTOR'
export type FieldType = 'TEXT' | 'RATING_1_5' | 'YES_NO' | 'SELECT'
// OVERDUE — вычисляемый статус: бекенд возвращает его в поле status, но не хранит.
export type VisitStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | 'OVERDUE'

export interface User {
  id: string
  fullName: string
  login: string
  role: UserRole
  isActive: boolean
}

export interface Location {
  id: string
  name: string
  address: string
  targetLat?: number | null
  targetLon?: number | null
}

export interface TemplateField {
  id?: string
  orderIndex: number
  title: string
  fieldType: FieldType
  options?: string[] | null
  isRequired: boolean
  requirePhotoOnIssue: boolean
}

export interface Template {
  id: string
  originId: string
  name: string
  description: string
  version: number
  isArchived: boolean
  fields: TemplateField[]
}

export interface Visit {
  id: string
  name: string
  templateId: string
  locationId: string
  adminId: string
  inspectorId: string
  scheduledAt: string
  completedAt?: string | null
  factLat?: number | null
  factLon?: number | null
  status: VisitStatus
  updatedAt: string
}

export interface VisitAnswer {
  id: string
  fieldId: string
  fieldTitle: string
  value: string
  hasIssue: boolean
  comment?: string | null
}

export interface MediaFile {
  id: string
  answerId?: string | null
  s3Key: string
  fileType: string
  createdAt: string
}

export interface VisitDetail {
  visit: Visit
  answers: VisitAnswer[]
  media: MediaFile[]
}

export interface DashboardSummary {
  totalVisits: number
  completedOnTime: number
  overdue: number
  findings: number
}

export interface InspectorStat {
  inspectorId: string
  fullName: string
  completedVisits: number
  avgCompletionMinutes?: number | null
}

export interface ProblemLocation {
  locationId: string
  name: string
  findings: number
}
