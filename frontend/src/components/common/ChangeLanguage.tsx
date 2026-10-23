import useTranslation from '../../i18n/useTranslation'
import type { Language } from '../../i18n/types'
import styles from './ChangeLanguage.module.css'
function ChangeLanguage({
  changeLanguage,
  disabled = false,
}: {
  changeLanguage: (targetLanguage: Language) => void
  disabled?: boolean
}) {
  const { language, t } = useTranslation()
  return (
    <>
      <button
        className={styles.button}
        type="button"
        disabled={disabled}
        onClick={() => changeLanguage(language === 'zh-CN' ? 'en-US' : 'zh-CN')}
      >
        {t('changeLanguage')}
      </button>
    </>
  )
}
export default ChangeLanguage
