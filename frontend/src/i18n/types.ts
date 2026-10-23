import type zh from './locales/zh-CN/common.json'

export type Language = 'zh-CN' | 'en-US'
export type MessageKey = keyof typeof zh
export type Translation = Record<MessageKey, string>
