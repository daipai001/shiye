export interface AdminTeamAuditLog {
  id: string
  teamId: string
  teamName?: string | null
  operatorId: string
  operatorEmail?: string | null
  action: string
  targetType?: string | null
  targetId?: string | null
  detail?: unknown
  ip?: string | null
  userAgent?: string | null
  createdAt: string
}
