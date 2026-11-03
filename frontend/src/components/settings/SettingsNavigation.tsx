import { useEffect, useRef, useState } from 'react'
import { classNames } from '../../lib/classNames'
import { usePreferences } from '../../lib/preferences'
import { Icon } from '../ui'
import styles from './SettingsNavigation.module.scss'

const categories = [
  { id: 'settings-appearance', title: 'appearance', analysis: false },
  { id: 'settings-profile', title: 'profileSettings', analysis: false, account: true },
  { id: 'settings-model', title: 'modelPreference', analysis: true },
  { id: 'settings-checks', title: 'checks', analysis: true },
  { id: 'settings-directories', title: 'ignoreDirsTitle', analysis: true },
  { id: 'settings-limits', title: 'analysisLimits', analysis: true },
  { id: 'settings-github', title: 'githubBinding', analysis: false, account: true },
  { id: 'settings-password', title: 'profilePassword', analysis: false, account: true },
  { id: 'settings-data', title: 'dataNotice', analysis: false },
] as const
/** 接收分析设置可用标记；返回跟随滚动高亮并可定位设置区域的导航节点。 */
export default function SettingsNavigation({ analysisAvailable, accountAvailable }: { analysisAvailable: boolean; accountAvailable: boolean }) {
  const { t } = usePreferences()
  const sections = categories.filter(category =>
    (analysisAvailable || !category.analysis) && (!('account' in category) || !category.account || accountAvailable),
  )
  const [active, setActive] = useState<string>(categories[0].id)
  const linksRef = useRef<HTMLOListElement>(null)
  const scrollingTo = useRef<string | null>(null)
  useEffect(() => {
    const list = linksRef.current
    const link = list?.querySelector('a[aria-current]')
    if (!list || !link) return
    const bounds = list.getBoundingClientRect()
    const item = link.getBoundingClientRect()
    if (item.left < bounds.left) list.scrollLeft += item.left - bounds.left
    else if (item.right > bounds.right) list.scrollLeft += item.right - bounds.right
  }, [active, sections])
  useEffect(() => {
    let frame = 0
    /** 无参数；按当前滚动位置更新选中分类，无返回值。 */
    const update = () => {
      let current: string = sections[0].id
      for (const section of sections) {
        const element = document.getElementById(section.id)
        if (!element) continue
        const offset = parseFloat(getComputedStyle(element).scrollMarginTop) || 0
        if (element.getBoundingClientRect().top <= offset + 2) current = section.id
      }
      if (window.scrollY > 0 && Math.ceil(window.scrollY + window.innerHeight) >= document.documentElement.scrollHeight - 2) {
        current = sections[sections.length - 1].id
      }
      setActive(current)
    }
    /** 无参数；取消旧帧并在非主动定位期间安排高亮更新，无返回值。 */
    const schedule = () => {
      window.cancelAnimationFrame(frame)
      if (scrollingTo.current) return
      frame = window.requestAnimationFrame(update)
    }
    /** 无参数；清除主动滚动目标以恢复位置跟踪，无返回值。 */
    const clearTarget = () => { scrollingTo.current = null }
    /** 无参数；窗口尺寸变化时解除定位并安排高亮更新，无返回值。 */
    const resize = () => { clearTarget(); schedule() }
    schedule()
    window.addEventListener('scroll', schedule, { passive: true })
    window.addEventListener('scrollend', clearTarget)
    window.addEventListener('wheel', clearTarget, { passive: true })
    window.addEventListener('touchstart', clearTarget, { passive: true })
    window.addEventListener('pointerdown', clearTarget)
    window.addEventListener('keydown', clearTarget)
    window.addEventListener('resize', resize)
    return () => {
      window.cancelAnimationFrame(frame)
      window.removeEventListener('scroll', schedule)
      window.removeEventListener('scrollend', clearTarget)
      window.removeEventListener('wheel', clearTarget)
      window.removeEventListener('touchstart', clearTarget)
      window.removeEventListener('pointerdown', clearTarget)
      window.removeEventListener('keydown', clearTarget)
      window.removeEventListener('resize', resize)
    }
  }, [sections])
  return (
    <aside className={classNames('card', styles.navigation)}>
      <h2>{t('settingsNavigation')}</h2>
      <nav aria-label={t('settingsNavigation')}>
        <ol ref={linksRef} className={styles.links}>
          {sections.map((section, index) => (
            <li key={section.id}>
              <a
                href={`#${section.id}`}
                data-tour={section.id === 'settings-checks' ? 'settings-navigation' : undefined}
                aria-current={active === section.id ? 'location' : undefined}
                onClick={event => {
                  const target = document.getElementById(section.id)
                  if (!target) return
                  event.preventDefault()
                  scrollingTo.current = section.id
                  setActive(section.id)
                  target.focus({ preventScroll: true })
                  target.scrollIntoView({
                    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth',
                    block: 'start',
                  })
                }}
              >
                <span className={styles.number} aria-hidden="true">0{index + 1}</span>
                <span>{t(section.title)}</span>
                <Icon name="chevron" size={13} />
              </a>
            </li>
          ))}
        </ol>
      </nav>
    </aside>
  )
}
