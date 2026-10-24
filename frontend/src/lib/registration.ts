import type { MessageKey } from '../i18n/types'

// Current RegisterDTO; see the current-contract table in docs/接口文档.md §3.1.
export const registrationLimits = {
  username: { min: 3, max: 50 },
  password: { min: 6, max: 32 },
  nickname: { max: 50 },
  email: { max: 100 },
  phone: { max: 11 },
} as const

export const registrationFields = ['username', 'password', 'nickname', 'email', 'phone'] as const
export type RegistrationField = typeof registrationFields[number]
export type RegistrationErrors = Partial<Record<RegistrationField, MessageKey>>

/** 接收注册表单元素；返回整理后的字段 values 和以翻译键表示的字段错误 errors。 */
export function validateRegistration(form: HTMLFormElement) {
  const data = new FormData(form)
  const values = {
    username: String(data.get('username') ?? '').trim(),
    // Passwords are checked for blankness but sent exactly as entered.
    password: String(data.get('password') ?? ''),
    nickname: String(data.get('nickname') ?? '').trim(),
    email: String(data.get('email') ?? '').trim(),
    phone: String(data.get('phone') ?? '').trim(),
  }
  const errors: RegistrationErrors = {}
  if (!values.username) errors.username = 'usernameRequired'
  else if (values.username.length < registrationLimits.username.min || values.username.length > registrationLimits.username.max) errors.username = 'usernameLength'
  else if (!/^[a-zA-Z0-9_]+$/.test(values.username)) errors.username = 'usernameCharacters'

  if (!values.password.trim()) errors.password = 'passwordRequired'
  else if (values.password.length < registrationLimits.password.min || values.password.length > registrationLimits.password.max) errors.password = 'registerPasswordLength'

  if (values.nickname.length > registrationLimits.nickname.max) errors.nickname = 'nicknameLength'
  if (values.email.length > registrationLimits.email.max) errors.email = 'emailLength'
  else if (values.email) {
    const emailInput = form.elements.namedItem('email')
    if (emailInput instanceof HTMLInputElement && emailInput.validity.typeMismatch) errors.email = 'emailInvalid'
  }
  if (values.phone && !/^1[3-9][0-9]{9}$/.test(values.phone)) errors.phone = 'phoneInvalid'
  return { values, errors }
}
