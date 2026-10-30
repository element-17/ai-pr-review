import useTranslation from '../../i18n/useTranslation'
import type { Language } from '../../i18n/types'
import styles from './ChangeLanguage.module.scss'
/** 接收语言切换回调和禁用标记；返回中英文切换按钮节点。 */
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
