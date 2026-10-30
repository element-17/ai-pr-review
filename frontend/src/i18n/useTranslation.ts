import { usePreferences } from '../lib/preferences'

/** 无参数；返回当前界面语言 language 和翻译函数 t。 */
function useTranslation() {
  const { language, t } = usePreferences()
  return { language, t }
}
export default useTranslation
