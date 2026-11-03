import { classNames } from '../lib/classNames'
import styles from './Home.module.scss'
import { usePreferences } from '../lib/preferences'
import ReviewForm from '../components/review/ReviewForm'
import OpenTaskForm from '../components/review/OpenTaskForm'

/** 接收可选初始查询串；返回登录用户的 PR 分析及任务结果入口页面节点。 */
export default function Home({ search = '' }: { search?: string }) {
  const { t } = usePreferences()
  return (
    <div className={styles['home-page']}>
      <div className={styles['home-masthead']}>
        <span>{t('deskLabel')}</span>
        <span>GITHUB / PULL REQUEST</span>
      </div>
      <div className={styles['review-studio']}>
        <section className={styles['hero']}>
          <h1>
            {t('homeTitle')}
            <em>{t('homeTitleAccent')}</em>
          </h1>
        </section>
        <section className={classNames('card', styles['new-review-card'])}>
          <div className={styles['section-label']}>
            <span className={styles['section-index']}>01 /</span>
            <h2>{t('home')}</h2>
          </div>
          <ReviewForm search={search} />
        </section>
        <figure className={styles['review-scene']}>
          <div className={styles['review-scene-image']}>
            <img
              src={`${import.meta.env.BASE_URL}images/review-desk.webp`}
              alt={t('deskImageAlt')}
              width="1122"
              height="1402"
              fetchPriority="high"
            />
            <div className={styles['scene-note']} aria-hidden="true">
              <span>↳</span> Read between
              <br />
              the lines.
            </div>
            <span className={styles['scene-corner']} aria-hidden="true">
              PR / 01
            </span>
          </div>
          <figcaption>
            <span>{t('deskCaption')}</span>
            <span className={styles['scene-caption-index']}>
              FIG. 01 — REVIEW DESK
            </span>
          </figcaption>
        </figure>
      </div>
      <section className={classNames('card', styles['open-task-card'])}>
        <div className={styles['open-task-intro']}>
          <span className={styles['section-index']}>02 /</span>
          <h2>{t('openTask')}</h2>
          <p>{t('loginHint')}</p>
        </div>
        <OpenTaskForm />
      </section>
    </div>
  )
}
