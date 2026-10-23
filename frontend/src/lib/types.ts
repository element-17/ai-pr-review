import type { Language } from '../i18n/types'
export type { Language } from '../i18n/types'
export interface User {
  id: number
  username: string
  nickname: string | null
  avatar: string | null
  role: string
}
export interface Session {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  userInfo: User
}
export interface Page<T> {
  total: number
  records: T[]
}
export interface AnalysisConfig {
  checkCategories: number[]
  ignoreDirs: string[]
  maxFileCount: number
  maxDiffLines: number
}
export interface Settings extends AnalysisConfig {
  theme: number
  language: Language
  updateTime?: string
}
export interface Pr {
  repoFullName: string
  prNumber: number
  title: string
  author: string
  state: number
  baseBranch: string
  headBranch: string
  headSha: string
  addLines: number
  deleteLines: number
  fileCount: number
}
export interface Task {
  taskId: number
  pr: Pr
  headSha: string
  status: number
  stage: number
  progress: number
  reportLanguage: Language
  isExpired: boolean
  createTime: string
  finishTime: string | null
}
export interface Progress {
  taskId: number
  status: number
  stage: number
  progress: number
  errorCode: number | null
  errorMessage: string | null
  startTime: string | null
  finishTime: string | null
}
export interface IssueStats {
  total: number
  pending: number
  accepted: number
  resolved: number
  falsePositive: number
  bySeverity: { low: number; medium: number; high: number; critical: number }
}
export interface TaskDetail extends Task {
  modelName: string | null
  analysisConfig: AnalysisConfig
  errorCode: number | null
  errorMessage: string | null
  isGuest: boolean
  canCancel: boolean
  canRetry: boolean
  canDelete: boolean
  hasReport: boolean
  startTime: string | null
  stats: {
    files: { total: number; analyzed: number; skipped: number; failed: number }
    issues: IssueStats
  }
  priorityFiles: {
    fileId: number
    filePath: string
    priorityReason: string
    issueCount: number
  }[]
}
export interface Report {
  taskId: number
  reportLanguage: Language
  modelName: string | null
  analysisConfig: AnalysisConfig
  purpose: string | null
  changes: string | null
  impacts: string | null
  coverage: {
    totalFiles: number
    analyzedFiles: number
    skippedFiles: number
    failedFiles: number
  }
  generatedAt: string
}
export interface Issue {
  issueId: number
  title: string
  category: number
  severity: number
  confidence: number
  fileId: number | null
  filePath: string | null
  startLine: number | null
  endLine: number | null
  feedbackStatus: number
  updateTime: string
}
export interface IssueDetail extends Issue {
  file: { fileId: number; filePath: string; changeType: number } | null
  triggerCondition: string | null
  impact: string | null
  evidence: string | null
  aiSuggestion: string | null
  suggestion: string
  feedbackRemark: string | null
  feedbackUserId: number | null
  feedbackTime: string | null
  githubUrl: string | null
}
export interface IssuePage extends Page<Issue> {
  stats: IssueStats
}
export interface TaskFile {
  fileId: number
  filePath: string
  oldPath: string | null
  changeType: number
  addLines: number
  deleteLines: number
  language: string | null
  isBinary: boolean
  status: number
  skipReason: string | null
  priorityReason: string | null
  summary: string | null
}
export interface FileDetail extends TaskFile {
  patch: string | null
  githubUrl: string | null
  issues: Issue[]
}
export interface DiffRow {
  type: 'hunk' | 'context' | 'add' | 'delete' | 'meta'
  oldLine: number | null
  newLine: number | null
  content: string
  issueIds: number[]
  highestSeverity: number | null
}
export interface FileDiff extends FileDetail {
  patchAvailable: boolean
  rows: DiffRow[]
  addedLines: number[]
  hunkLines: number[]
}
export interface CreatedTask {
  taskId: number
  guestToken: string | null
  isGuest: boolean
  expireTime: string | null
}
export interface Preview {
  pr: Pr
  reusableTask: Task | null
}
export interface Comparison {
  base: Task
  target: Task
  summary: Record<
    'purpose' | 'changes' | 'impacts',
    { base: string | null; target: string | null; changed: boolean }
  >
  issues: {
    added: Issue[]
    remaining: (Issue & {
      baseIssueId: number
      targetIssueId: number
      lineShifted: boolean
      matchConfidence?: string
    })[]
    resolved: Issue[]
  }
  matching: { method: string; lineShiftWarning: boolean; note: string }
}
