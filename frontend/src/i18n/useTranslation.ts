import { usePreferences } from '../lib/preferences'

function useTranslation() {
  const { language, t } = usePreferences()
  return { language, t }
}
export default useTranslation
