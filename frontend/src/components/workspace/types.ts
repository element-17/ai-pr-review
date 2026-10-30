import type { TaskDetail } from '../../lib/types'

export interface WorkspaceProps {
  task: TaskDetail
  guestToken: string
  taskPath: (
    suffix?: string,
    params?: Record<string, string | number | undefined>,
  ) => string
  onUpdate: () => void
}
