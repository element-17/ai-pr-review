import { usePreferences } from '../lib/preferences'
import ReviewForm from '../components/review/ReviewForm'
import OpenTaskForm from '../components/review/OpenTaskForm'
import { Icon, Link } from '../components/ui'
import styles from './GuestHome.module.scss'

/** 接收可选初始查询串；返回游客 PR 分析、凭证访问和登录注册入口页面节点。 */
export default function GuestHome({ search = '' }: { search?: string }) {
  const { t } = usePreferences()
  const returnTo = encodeURIComponent(`/${search ? `?${search}` : ''}`)
  return (
    <div className={styles.page}>
      <div className={styles.masthead}><span>{t('guestKicker')}</span><span>GITHUB / PUBLIC PR</span></div>
      <section className={styles.intro} aria-labelledby="guest-home-title">
        <div>
          <span className={styles.badge}><Icon name="globe" size={15} />{t('guestAccessMode')}</span>
          <h1 id="guest-home-title">{t('guestTitle')}<em>{t('guestTitleAccent')}</em></h1>
          <p>{t('guestDescription')}</p>
        </div>
        <div className={styles['link-note']}>
          <Icon name="shield" size={26} />
          <h2>{t('guestSaveLinkTitle')}</h2>
          <p>{t('guestSaveLinkCopy')}</p>
        </div>
      </section>
      <div className={styles.layout}>
        <section className={`card ${styles.analyze}`} aria-labelledby="guest-analysis-title">
          <div className={styles.heading}><span aria-hidden="true">01 /</span><h2 id="guest-analysis-title">{t('guestAnalyzeTitle')}</h2></div>
          <p className={styles.description}>{t('guestAnalyzeDescription')}</p>
          <ReviewForm search={search} guest />
        </section>
        <div className={styles.side}>
          <section className="card" aria-labelledby="guest-results-title">
            <div className={styles.heading}><span aria-hidden="true">02 /</span><h2 id="guest-results-title">{t('guestAccessTitle')}</h2></div>
            <p className={styles.description}>{t('guestAccessDescription')}</p>
            <OpenTaskForm guest />
            <p className={styles.hint}>{t('guestAccessHint')}</p>
          </section>
          <section className={styles.account} aria-labelledby="guest-account-title">
            <h2 id="guest-account-title">{t('guestAccountTitle')}</h2>
            <p>{t('guestAccountDescription')}</p>
            <div className="actions wrap">
              <Link className="button primary" to={`/login?returnTo=${returnTo}`}>{t('guestLoginAction')}<Icon name="arrow" size={15} /></Link>
              <Link className="button" to={`/register?returnTo=${returnTo}`}>{t('register')}</Link>
            </div>
          </section>
        </div>
      </div>
    </div>
  )
}
