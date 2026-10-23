import styles from './Loading.module.css'
import useTranslation from '../../i18n/useTranslation'
import loadingIcon from '../../assets/loading.svg'
function Loading() {
  const { t } = useTranslation()
  return (
    <div className={styles['loading-state']} role="status" aria-live="polite">
      <img src={loadingIcon} alt="" />
      <div>{t('loading')}</div>
    </div>
  )
}
export default Loading
