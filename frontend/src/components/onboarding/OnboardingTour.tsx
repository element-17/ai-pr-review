import { useCallback, useEffect, useId, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { usePreferences } from '../../lib/preferences'
import { navigate } from '../../hooks'
import { Icon } from '../ui'
import { completeTour, getTourSteps, hasCompletedTour, legacyTourStorageKey, tourStorageKey, type TourPage, type TourStep } from './tours'
import styles from './OnboardingTour.module.scss'

interface Placement {
  target: { left: number; top: number; width: number; height: number }
  left: number
  top: number
  side: 'above' | 'below' | 'left' | 'right'
  arrow: number
}

const clamp = (value: number, min: number, max: number) => Math.max(min, Math.min(value, max))

/** 首页及结果页共用入口；首次自动打开，完成或跳过后可随时重放。 */
export default function OnboardingTour({ audience, guest, page }: { audience: string; guest: boolean; page: TourPage }) {
  const { t } = usePreferences()
  const storageKey = tourStorageKey(audience)
  const legacyKey = legacyTourStorageKey(audience, 'home')
  const legacyKeys = useMemo(() => (['home', 'history', 'review'] as const).map(item => legacyTourStorageKey(audience, item)), [audience])
  const [open, setOpen] = useState(() => page === 'home' && !hasCompletedTour(storageKey, legacyKeys))
  const [index, setIndex] = useState(0)
  const steps = useMemo(() => getTourSteps(guest), [guest])

  const close = useCallback(() => {
    completeTour(storageKey, legacyKey)
    setOpen(false)
    setIndex(0)
  }, [legacyKey, storageKey])

  const changeStep = useCallback((nextIndex: number) => {
    const nextStep = steps[nextIndex]
    if (!nextStep) return
    setIndex(nextIndex)
    if (nextStep.page !== page) {
      navigate(nextStep.page === 'tasks' ? '/tasks' : nextStep.page === 'settings' ? '/settings' : '/')
    }
  }, [page, steps])

  const markShown = useCallback(() => {
    // 第一次真正找到目标并展示后就记住，切换页面时不会再次自动弹出。
    completeTour(storageKey, legacyKey)
  }, [legacyKey, storageKey])

  return (
    <>
      {page === 'home' && <button type="button" className="icon-button" aria-label={t('tourLabel')} title={t('tourReplay')} onClick={() => { setIndex(0); setOpen(true) }}>
        <Icon name="help" size={18} />
      </button>}
      {open && steps[index]?.page === page && <TourOverlay steps={steps} index={index} onIndexChange={changeStep} onTargetClick={nextIndex => setIndex(nextIndex)} onClose={close} onShown={markShown} />}
    </>
  )
}

/** 在目标元素实际出现后展示，使用原生模态框提供焦点约束与 Escape 支持。 */
function TourOverlay({ steps, index, onIndexChange, onTargetClick, onClose, onShown }: { steps: TourStep[]; index: number; onIndexChange: (index: number) => void; onTargetClick: (index: number) => void; onClose: () => void; onShown: () => void }) {
  const { t } = usePreferences()
  const id = useId()
  const dialogRef = useRef<HTMLDialogElement>(null)
  const bubbleRef = useRef<HTMLDivElement>(null)
  const nextRef = useRef<HTMLButtonElement>(null)
  const [placement, setPlacement] = useState<Placement | null>(null)
  const shownRef = useRef(false)
  const step = steps[index]
  const last = index === steps.length - 1
  const ready = placement !== null

  useEffect(() => {
    if (ready) nextRef.current?.focus({ preventScroll: true })
  }, [ready])

  useLayoutEffect(() => {
    const dialog = dialogRef.current
    const previousFocus = document.activeElement
    return () => {
      dialog?.close()
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) previousFocus.focus({ preventScroll: true })
    }
  }, [])

  useLayoutEffect(() => {
    const dialog = dialogRef.current
    const bubble = bubbleRef.current
    if (!dialog || !bubble) return
    let target: HTMLElement | null = null
    let frame = 0

    function update() {
      if (!target || !target.isConnected || !bubble || !dialog) return
      const rect = target.getBoundingClientRect()
      if (!rect.width || !rect.height) return
      if (!dialog.open) {
        dialog.show()
        if (!shownRef.current) {
          shownRef.current = true
          onShown()
        }
      }
      const width = document.documentElement.clientWidth
      const height = window.innerHeight
      const margin = 16
      const gap = 18
      const bounds = bubble.getBoundingClientRect()
      const left = clamp(rect.left - 6, 8, width - 8)
      const top = clamp(rect.top - 6, 8, height - 8)
      const right = clamp(rect.right + 6, left, width - 8)
      const bottom = clamp(rect.bottom + 6, top, height - 8)
      const centerX = (left + right) / 2
      const centerY = (top + bottom) / 2
      let side: Placement['side'] = 'below'
      let bubbleLeft = centerX - bounds.width / 2
      let bubbleTop = bottom + gap
      if (bubbleTop + bounds.height > height - margin) {
        if (top - gap - bounds.height >= margin) {
          side = 'above'
          bubbleTop = top - gap - bounds.height
        } else if (right + gap + bounds.width <= width - margin) {
          side = 'right'
          bubbleLeft = right + gap
          bubbleTop = centerY - bounds.height / 2
        } else if (left - gap - bounds.width >= margin) {
          side = 'left'
          bubbleLeft = left - gap - bounds.width
          bubbleTop = centerY - bounds.height / 2
        } else {
          side = top > height - bottom ? 'above' : 'below'
          bubbleTop = side === 'above' ? top - gap - bounds.height : bottom + gap
        }
      }
      bubbleLeft = clamp(bubbleLeft, margin, width - bounds.width - margin)
      bubbleTop = clamp(bubbleTop, margin, height - bounds.height - margin)
      setPlacement({
        target: { left, top, width: right - left, height: bottom - top },
        left: bubbleLeft,
        top: bubbleTop,
        side,
        arrow: side === 'above' || side === 'below'
          ? clamp(centerX - bubbleLeft, 24, bounds.width - 24)
          : clamp(centerY - bubbleTop, 24, bounds.height - 24),
      })
    }

    function scheduleUpdate() {
      cancelAnimationFrame(frame)
      frame = requestAnimationFrame(update)
    }

    function revealTarget() {
      const rect = target?.getBoundingClientRect()
      if (rect && (rect.top < 16 || rect.bottom > window.innerHeight - 16)) {
        target?.scrollIntoView({ block: 'center', inline: 'nearest', behavior: 'instant' })
      }
    }

    function handleResize() {
      revealTarget()
      scheduleUpdate()
    }

    const resizeObserver = new ResizeObserver(scheduleUpdate)
    const observer = new MutationObserver(findTarget)

    function findTarget() {
      const element = document.querySelector<HTMLElement>(`[data-tour="${step.target}"]`)
      if (!element || element === target) return
      const rect = element.getBoundingClientRect()
      if (!rect.width || !rect.height) return
      target = element
      revealTarget()
      resizeObserver.observe(target)
      resizeObserver.observe(bubble!)
      target.addEventListener('click', handleTargetClick, true)
      scheduleUpdate()
    }

    function handleTargetClick() {
      if (index < steps.length - 1) onTargetClick(index + 1)
    }

    observer.observe(document.getElementById('main-content') || document.body, { childList: true, subtree: true })
    findTarget()
    window.addEventListener('resize', handleResize)
    window.addEventListener('scroll', scheduleUpdate, true)
    return () => {
      cancelAnimationFrame(frame)
      observer.disconnect()
      resizeObserver.disconnect()
      target?.removeEventListener('click', handleTargetClick, true)
      window.removeEventListener('resize', handleResize)
      window.removeEventListener('scroll', scheduleUpdate, true)
    }
  }, [index, onTargetClick, onShown, step.target, steps.length])

  return createPortal(
    <dialog
      ref={dialogRef}
      className={styles.layer}
      data-onboarding-tour
      aria-labelledby={`${id}-title`}
      aria-describedby={`${id}-description`}
      onCancel={event => { event.preventDefault(); onClose() }}
      onKeyDown={event => { if (event.key === 'Escape') onClose() }}
    >
      <svg className={styles.backdrop} width="100%" height="100%" aria-hidden="true">
        <defs>
          <mask id={`${id}-mask`}>
            <rect width="100%" height="100%" fill="white" />
            {placement && <rect width={placement.target.width} height={placement.target.height} x={placement.target.left} y={placement.target.top} rx="8" fill="black" />}
          </mask>
        </defs>
        <rect width="100%" height="100%" fill="rgba(17, 20, 16, .62)" mask={`url(#${id}-mask)`} />
      </svg>
      {placement && <div className={styles.spotlight} style={placement.target} aria-hidden="true" />}
      <div
        ref={bubbleRef}
        className={styles.bubble}
        data-side={placement?.side}
        style={{ left: placement?.left, top: placement?.top, visibility: placement ? 'visible' : 'hidden' }}
      >
        {placement && <span className={styles.arrow} aria-hidden="true" style={placement.side === 'above' || placement.side === 'below' ? { left: placement.arrow } : { top: placement.arrow }} />}
        <div className={styles.content}>
          <div className={styles.eyebrow}>
            <span><Icon name="help" size={15} />{t('tourLabel')}</span>
            <button type="button" className={styles.skip} onClick={onClose}>{t('tourSkip')}</button>
          </div>
          <div aria-live="polite" aria-atomic="true">
            <h2 id={`${id}-title`}>{t(step.title)}</h2>
            <p id={`${id}-description`}>{t(step.description)}</p>
            <div className={styles.progress} aria-label={`${t('tourProgress')} ${index + 1} / ${steps.length}`}>
              <span className={styles.dots} aria-hidden="true">
                {steps.map((item, position) => <span key={`${item.page}:${item.target}:${position}`} data-active={position <= index} />)}
              </span>
              <span>{String(index + 1).padStart(2, '0')} / {String(steps.length).padStart(2, '0')}</span>
            </div>
          </div>
          <div className={styles.actions}>
            <button type="button" className="button small" disabled={index === 0} onClick={() => onIndexChange(index - 1)}>{t('tourPrevious')}</button>
            <button ref={nextRef} type="button" className="button primary small" onClick={() => last ? onClose() : onIndexChange(index + 1)}>
              {t(last ? 'tourFinish' : 'tourNext')}<Icon name={last ? 'check' : 'arrow'} size={15} />
            </button>
          </div>
        </div>
      </div>
    </dialog>,
    document.body,
  )
}
