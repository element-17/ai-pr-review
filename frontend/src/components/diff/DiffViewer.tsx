import { classNames } from '../../lib/classNames'
import styles from './DiffViewer.module.css'
import { useEffect, useRef, useState } from 'react'
import { splitDiff, toDiffLines } from '../../lib/diff'
import type { DiffLine } from '../../lib/diff'
import { usePreferences } from '../../lib/preferences'
import { useFormatting } from '../../hooks'
import type { FileDiff, Issue } from '../../lib/types'
import type { WorkspaceProps } from '../workspace/types'
import { Icon, Link } from '../ui'
import Highlight from './Highlight'

const scrollPositions = new Map<
  string,
  { top: number; left: number; visible: number }
>()

export default function DiffViewer({
  file,
  view,
  focusLine,
  taskPath,
  returnTo,
}: {
  file: FileDiff
  view: 'unified' | 'split'
  focusLine?: number
  taskPath: WorkspaceProps['taskPath']
  returnTo: string
}) {
  const { t } = usePreferences()
  const { number } = useFormatting()
  const scrollRef = useRef<HTMLDivElement>(null)
  const key = `${returnTo.split('?')[0]}:${file.fileId}:${view}`
  const lines = toDiffLines(file.rows)
  const focusIndex = focusLine
    ? lines.findIndex(
        (line) =>
          (file.changeType === 3 ? line.oldLine : line.newLine) === focusLine,
      )
    : -1
  const initialVisible = Math.max(
    250,
    focusIndex + 40,
    scrollPositions.get(key)?.visible || 0,
  )
  const [visible, setVisible] = useState(initialVisible)
  const count = Math.max(visible, focusIndex + 40)
  const displayed = lines.slice(0, count)
  const visibleRef = useRef(count)
  useEffect(() => {
    visibleRef.current = count
  }, [count])
  useEffect(() => {
    const container = scrollRef.current
    const saved = scrollPositions.get(key)
    if (container && saved && !focusLine) {
      container.scrollTop = saved.top
      container.scrollLeft = saved.left
    }
    const frame = requestAnimationFrame(() => {
      if (focusLine && container)
        container
          .querySelector('[data-focused="true"]')
          ?.scrollIntoView({ block: 'center', inline: 'nearest' })
    })
    return () => {
      cancelAnimationFrame(frame)
      if (container)
        scrollPositions.set(key, {
          top: container.scrollTop,
          left: container.scrollLeft,
          visible: visibleRef.current,
        })
    }
  }, [key, focusLine])
  function markers(line: DiffLine): Issue[] {
    if (file.changeType !== 3)
      return file.issues.filter((issue) => line.issueIds.includes(issue.issueId))
    // 后端只按新文件侧映射 issueIds，已删除文件仍按旧行号匹配
    const number = line.oldLine
    if (number === null || line.type === 'hunk' || line.type === 'meta')
      return []
    return file.issues.filter(
      (issue) =>
        issue.startLine !== null &&
        number >= issue.startLine &&
        number <= (issue.endLine || issue.startLine),
    )
  }
  function issueLinks(line: DiffLine) {
    return markers(line).map((issue) => (
      <Link
        key={issue.issueId}
        className={classNames(
          styles['line-issue'],
          styles[`severity-${issue.severity}`],
        )}
        title={issue.title}
        to={taskPath('/issues', { issueId: issue.issueId, returnTo })}
      >
        <Icon name="alert" size={13} />
        {issue.title}
      </Link>
    ))
  }
  function focused(line: DiffLine) {
    return (
      !!focusLine &&
      (file.changeType === 3 ? line.oldLine : line.newLine) === focusLine
    )
  }
  function cell(line: DiffLine | null, side: 'old' | 'new') {
    return (
      <>
        <td className={styles['line-number']}>
          {line ? (side === 'old' ? line.oldLine : line.newLine) : ''}
        </td>
        <td
          className={classNames(styles['code-cell'], styles[line?.type || ''])}
          data-focused={!!line && focused(line)}
        >
          <code>{line && <Highlight text={line.content} />}</code>
          {line &&
            side === (file.changeType === 3 ? 'old' : 'new') &&
            issueLinks(line)}
        </td>
      </>
    )
  }
  return (
    <>
      <div
        className={styles['diff-scroll']}
        ref={scrollRef}
        tabIndex={0}
        role="region"
        aria-label={`${t('files')}: ${file.filePath}`}
      >
        <table className={classNames(styles['diff-table'], styles[view])}>
          <tbody>
            {view === 'unified'
              ? displayed.map((line) => (
                  <tr
                    key={line.index}
                    className={classNames(
                      styles['diff-line'],
                      styles[line.type],
                    )}
                    data-focused={focused(line)}
                  >
                    <td className={styles['line-number']}>{line.oldLine}</td>
                    <td className={styles['line-number']}>{line.newLine}</td>
                    <td className={styles['line-sign']}>
                      {line.type === 'add'
                        ? '+'
                        : line.type === 'delete'
                          ? '−'
                          : ''}
                    </td>
                    <td className={styles['code-cell']}>
                      <code>
                        <Highlight text={line.content} />
                      </code>
                      {issueLinks(line)}
                    </td>
                  </tr>
                ))
              : splitDiff(displayed).map((row, index) => (
                  <tr className={styles['diff-line']} key={index}>
                    {cell(row.left, 'old')}
                    {cell(row.right, 'new')}
                  </tr>
                ))}
          </tbody>
        </table>
      </div>
      {count < lines.length && (
        <button
          className={classNames('button', styles['load-more'])}
          onClick={() => setVisible(count + 250)}
        >
          {t('loadMore')} ({number(Math.min(count, lines.length))}/{number(lines.length)})
        </button>
      )}
    </>
  )
}
