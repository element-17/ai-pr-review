import type { MessageKey } from '../../i18n/types'

export type TourKind = 'home' | 'history' | 'review'
export type TourPage = 'home' | 'tasks' | 'settings' | 'review'

export interface TourStep {
  page: TourPage
  target: string
  title: MessageKey
  description: MessageKey
}

const sessionCompletions = new Set<string>()

/** 按引导版本和身份记录，一位用户在所有页面共用一份完成记忆。 */
export function tourStorageKey(audience: string) {
  return `pr-review.onboarding.v1:${audience}`
}

/** 兼容旧版本按页面保存的记忆，升级后会直接视为已完成。 */
export function legacyTourStorageKey(audience: string, kind: TourKind) {
  return `pr-review.onboarding.v1:${audience}:${kind}`
}

export function hasCompletedTour(key: string, legacyKeys: string[] = []) {
  if (sessionCompletions.has(key)) return true
  try {
    return localStorage.getItem(key) === 'done' || legacyKeys.some(legacyKey => localStorage.getItem(legacyKey) === 'done')
  } catch {
    return false
  }
}

export function completeTour(key: string, legacyKey?: string) {
  sessionCompletions.add(key)
  if (legacyKey) sessionCompletions.add(legacyKey)
  try {
    localStorage.setItem(key, 'done')
    // 让旧版本的记忆检查和现有自动化用例也能平滑迁移。
    if (legacyKey) localStorage.setItem(legacyKey, 'done')
  } catch {
    // 存储被禁用时仍在当前应用会话中记住，保持引导和页面可用。
  }
}

export function getTourSteps(guest: boolean): TourStep[] {
  const home: TourStep[] = [
    { page: 'home', target: 'pr-url', title: 'tourPrTitle', description: guest ? 'tourGuestPrDescription' : 'tourPrDescription' },
    { page: 'home', target: 'report-language', title: 'tourLanguageTitle', description: 'tourLanguageDescription' },
    { page: 'home', target: 'review-submit', title: 'tourPreviewTitle', description: 'tourPreviewDescription' },
    { page: 'home', target: 'open-task', title: 'tourOpenTitle', description: guest ? 'tourGuestOpenDescription' : 'tourOpenDescription' },
  ]
  if (guest) return home
  return [
    ...home,
    { page: 'home', target: 'nav-tasks', title: 'tourHistoryTitle', description: 'tourCrossTasksDescription' },
    { page: 'tasks', target: 'task-filter', title: 'tourFilterTitle', description: 'tourFilterDescription' },
    { page: 'tasks', target: 'nav-settings', title: 'tourSettingsTitle', description: 'tourCrossSettingsDescription' },
    { page: 'settings', target: 'settings-navigation', title: 'tourSettingsTitle', description: 'tourSettingsDescription' },
    { page: 'settings', target: 'nav-home', title: 'tourNewReviewTitle', description: 'tourCrossHomeDescription' },
    { page: 'home', target: 'pr-url', title: 'tourFinishTitle', description: 'tourFinishDescription' },
  ]
}
