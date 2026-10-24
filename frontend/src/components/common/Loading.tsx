import styles from './Loading.module.scss'
import useTranslation from '../../i18n/useTranslation'
import loadingIcon from '../../assets/loading.svg'
/** 无参数；返回包含动画图标和本地化文案的加载状态节点。 */
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
