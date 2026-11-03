import { useEffect, useMemo, useState } from 'react'
import ReactEChartsCore from 'echarts-for-react/esm/core'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsOption } from 'echarts'
import { api, query } from '../lib/api'
import { usePreferences } from '../lib/preferences'
import { useSession } from '../hooks'
import type { Page, Task } from '../lib/types'
import { LoginPrompt, PageHeading, ResourceState } from '../components/ui'
import styles from './Analytics.module.scss'

type WindowSize = 7 | 30 | 90
type Daily = { date: string; count: number; minutes: number }
const STATUS_KEYS = ['status1', 'status2', 'status3', 'status4', 'status5', 'status6'] as const
const STATUS_COLORS = ['#9b9d8e', '#d5a75b', '#72947c', '#d18d61', '#c96c68', '#78889b']
echarts.use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

/** Parse documented Asia/Shanghai timestamps consistently across browser locales. */
function timestamp(value: string | null) {
  if (!value) return NaN
  const parsed = Date.parse(value.includes('T') ? value : `${value.replace(' ', 'T')}+08:00`)
  return parsed
}

function duration(task: Task) {
  const start = timestamp(task.createTime)
  const end = timestamp(task.finishTime)
  return task.finishTime && Number.isFinite(start) && Number.isFinite(end) && end >= start
    ? (end - start) / 60000
    : null
}

/** Admin analytics from the authenticated user's task history. */
export default function Analytics() {
  const { t, resolvedTheme } = usePreferences()
  const session = useSession()
  const [windowSize, setWindowSize] = useState<WindowSize>(30)
  const [revision, setRevision] = useState(0)
  const requestKey = `${session?.userInfo.id || 'guest'}:${revision}`
  const [result, setResult] = useState<{ key: string; tasks?: Task[]; error?: unknown }>({ key: '' })

  useEffect(() => {
    if (!session) return
    const controller = new AbortController()
    void (async () => {
      const tasks: Task[] = []
      let page = 1
      let total = Infinity
      while (tasks.length < total) {
        const data = await api.get<Page<Task>>(`/tasks${query({ page, pageSize: 100, sort: 'createTime_asc' })}`, { signal: controller.signal })
        tasks.push(...data.records)
        total = data.total
        if (!data.records.length) break
        page += 1
      }
      if (!controller.signal.aborted) setResult({ key: requestKey, tasks })
    })().catch(error => {
      if (!controller.signal.aborted) setResult({ key: requestKey, error })
    })
    return () => controller.abort()
  }, [session, requestKey])

  const analytics = useMemo(() => {
    const end = new Date()
    end.setHours(0, 0, 0, 0)
    const start = new Date(end)
    start.setDate(start.getDate() - windowSize + 1)
    const days: Daily[] = Array.from({ length: windowSize }, (_, index) => {
      const day = new Date(start)
      day.setDate(start.getDate() + index)
      return { date: `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`, count: 0, minutes: 0 }
    })
    const byDate = new Map(days.map(day => [day.date, day]))
    let elapsed = 0
    let timed = 0
    const statusCounts = new Map<number, number>()
    const repoCounts = new Map<string, number>()
    const durationBuckets = [0, 0, 0, 0, 0]
    for (const task of result.tasks || []) {
      const day = byDate.get(task.createTime.slice(0, 10))
      if (!day) continue
      day.count += 1
      statusCounts.set(task.status, (statusCounts.get(task.status) || 0) + 1)
      const repo = task.pr?.repoFullName || t('analyticsUnknownRepo')
      repoCounts.set(repo, (repoCounts.get(repo) || 0) + 1)
      const minutes = duration(task)
      if (minutes !== null) {
        day.minutes += minutes
        elapsed += minutes
        timed += 1
        durationBuckets[minutes < 1 ? 0 : minutes < 5 ? 1 : minutes < 15 ? 2 : minutes < 60 ? 3 : 4] += 1
      }
    }
    return {
      days, count: days.reduce((sum, day) => sum + day.count, 0), elapsed, timed,
      statuses: [...statusCounts].map(([status, value]) => ({ name: t(STATUS_KEYS[status - 1] || 'analyticsUnknownStatus'), value, itemStyle: { color: STATUS_COLORS[status - 1] || '#888' } })),
      repositories: [...repoCounts].sort((a, b) => b[1] - a[1]).slice(0, 8),
      durationBuckets,
    }
  }, [result.tasks, windowSize, t])

  const loading = !!session && result.key !== requestKey
  const error = result.key === requestKey ? result.error : undefined
  const tasks = result.key === requestKey ? result.tasks : undefined
  const average = analytics.timed ? analytics.elapsed / analytics.timed : 0
  const durationLabel = (minutes: number) => minutes < 1 ? `${Math.round(minutes * 60)}${t('analyticsSeconds')}` : minutes < 60 ? `${Math.round(minutes)}${t('analyticsMinutes')}` : `${Math.floor(minutes / 60)}${t('analyticsHours')} ${Math.round(minutes % 60)}${t('analyticsMinutes')}`
  const textColor = resolvedTheme === 'dark' ? '#b2b1a4' : '#74736b'
  const gridColor = resolvedTheme === 'dark' ? 'rgba(178, 177, 164, .2)' : 'rgba(116, 115, 107, .2)'
  const activityOption: EChartsOption = {
    color: ['#cf815d'], tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 38, right: 16, top: 18, bottom: 32 },
    xAxis: { type: 'category', data: analytics.days.map(day => day.date.slice(5).replace('-', '/')), axisLabel: { color: textColor, interval: Math.max(0, Math.ceil(windowSize / 7) - 1) }, axisLine: { lineStyle: { color: gridColor } }, axisTick: { show: false } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: gridColor } }, axisLabel: { color: textColor } },
    series: [{ type: 'bar', data: analytics.days.map(day => day.count), barMaxWidth: 22, itemStyle: { borderRadius: [3, 3, 0, 0] } }],
  }
  const durationOption: EChartsOption = {
    color: ['#7d9b87'], tooltip: { trigger: 'axis' }, grid: { left: 48, right: 16, top: 18, bottom: 32 },
    xAxis: { type: 'category', data: analytics.days.map(day => day.date.slice(5).replace('-', '/')), axisLabel: { color: textColor, interval: Math.max(0, Math.ceil(windowSize / 7) - 1) }, axisLine: { lineStyle: { color: gridColor } }, axisTick: { show: false } },
    yAxis: { type: 'value', axisLabel: { color: textColor, formatter: `{value} ${t('analyticsMinutes')}` }, splitLine: { lineStyle: { color: gridColor } } },
    series: [{ type: 'line', smooth: true, showSymbol: false, areaStyle: { opacity: .12 }, data: analytics.days.map(day => Number(day.minutes.toFixed(1))) }],
  }
  const statusOption: EChartsOption = {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, left: 'center', textStyle: { color: textColor }, itemWidth: 9, itemHeight: 9 },
    series: [{ type: 'pie', radius: ['48%', '72%'], center: ['50%', '44%'], avoidLabelOverlap: true, itemStyle: { borderColor: resolvedTheme === 'dark' ? '#282a25' : '#fcfaf6', borderWidth: 3 }, label: { show: false }, emphasis: { label: { show: true, fontSize: 18, fontWeight: 'bold', formatter: '{c}' } }, data: analytics.statuses }],
  }
  const repoOption: EChartsOption = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } }, grid: { left: 16, right: 24, top: 12, bottom: 20, containLabel: true },
    xAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: gridColor } }, axisLabel: { color: textColor } },
    yAxis: { type: 'category', inverse: true, data: analytics.repositories.map(([name]) => name), axisLabel: { color: textColor, width: 160, overflow: 'truncate' }, axisLine: { show: false }, axisTick: { show: false } },
    series: [{ type: 'bar', data: analytics.repositories.map(([, count]) => count), barMaxWidth: 20, itemStyle: { color: '#8b9271', borderRadius: [0, 3, 3, 0] } }],
  }
  const durationDistributionOption: EChartsOption = {
    tooltip: { trigger: 'item' }, grid: { left: 38, right: 16, top: 15, bottom: 35 },
    xAxis: { type: 'category', data: ['<1', '1–5', '5–15', '15–60', '>60'].map((value, index) => `${value} ${index === 0 ? t('analyticsMinutes') : t('analyticsMinutes')}`), axisLabel: { color: textColor, interval: 0 }, axisLine: { lineStyle: { color: gridColor } }, axisTick: { show: false } },
    yAxis: { type: 'value', minInterval: 1, axisLabel: { color: textColor }, splitLine: { lineStyle: { color: gridColor } } },
    series: [{ type: 'bar', data: analytics.durationBuckets, barMaxWidth: 34, itemStyle: { color: '#72947c', borderRadius: [3, 3, 0, 0] } }],
  }

  return <div className={styles.page}>
    <PageHeading title="analytics" description="analyticsDescription">
      <div className={styles.actions}>
        <div className={styles.range} aria-label={t('analyticsRange')}>
          {([7, 30, 90] as const).map(days => <button key={days} type="button" aria-pressed={windowSize === days} onClick={() => setWindowSize(days)}>{days}{t('analyticsDays')}</button>)}
        </div>
        <button type="button" className="button small" onClick={() => setRevision(value => value + 1)}>{t('refresh')}</button>
      </div>
    </PageHeading>
    {!session ? <LoginPrompt /> : <>
      <ResourceState loading={loading} error={error} retry={() => setRevision(value => value + 1)} />
      {tasks && <>
        <div className={styles.metrics}>
          <article><span>{t('analyticsTotal')}</span><strong>{analytics.count}</strong><small>{t('analyticsTotalHint')}</small></article>
          <article><span>{t('analyticsTime')}</span><strong>{durationLabel(analytics.elapsed)}</strong><small>{t('analyticsTimeHint')}</small></article>
          <article><span>{t('analyticsAverage')}</span><strong>{durationLabel(average)}</strong><small>{t('analyticsAverageHint').replace('{count}', String(analytics.timed))}</small></article>
          <article><span>{t('analyticsActiveDays')}</span><strong>{analytics.days.filter(day => day.count > 0).length}<i> / {windowSize}</i></strong><small>{t('analyticsActiveDaysHint')}</small></article>
        </div>
        <div className={styles.chartGrid}>
          <section className={`${styles.chartCard} ${styles.wide}`}><div className={styles.chartHeading}><div><span>{t('analyticsActivityLabel')}</span><h2>{t('analyticsActivity')}</h2></div><span className={styles.chartTotal}>{analytics.count} {t('analyticsRecords')}</span></div><ReactEChartsCore echarts={echarts} option={activityOption} style={{ height: 250 }} notMerge lazyUpdate /></section>
          <section className={styles.chartCard}><div className={styles.chartHeading}><div><span>{t('analyticsStatusLabel')}</span><h2>{t('analyticsStatus')}</h2></div></div>{analytics.statuses.length ? <ReactEChartsCore echarts={echarts} option={statusOption} style={{ height: 250 }} notMerge lazyUpdate /> : <div className={styles.chartEmpty}>{t('analyticsNoData')}</div>}</section>
          <section className={styles.chartCard}><div className={styles.chartHeading}><div><span>{t('analyticsDurationLabel')}</span><h2>{t('analyticsDuration')}</h2></div></div><ReactEChartsCore echarts={echarts} option={durationOption} style={{ height: 250 }} notMerge lazyUpdate /></section>
          <section className={styles.chartCard}><div className={styles.chartHeading}><div><span>{t('analyticsDurationLabel')}</span><h2>{t('analyticsDurationDistribution')}</h2></div></div><ReactEChartsCore echarts={echarts} option={durationDistributionOption} style={{ height: 250 }} notMerge lazyUpdate /></section>
          <section className={styles.chartCard}><div className={styles.chartHeading}><div><span>{t('analyticsRepoLabel')}</span><h2>{t('analyticsRepo')}</h2></div></div>{analytics.repositories.length ? <ReactEChartsCore echarts={echarts} option={repoOption} style={{ height: 250 }} notMerge lazyUpdate /> : <div className={styles.chartEmpty}>{t('analyticsNoData')}</div>}</section>
        </div>
        <p className={styles.note}>{t('analyticsMethod')}</p>
      </>}
    </>}
  </div>
}
