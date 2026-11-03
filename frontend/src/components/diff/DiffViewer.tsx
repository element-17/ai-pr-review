import { classNames } from '../../lib/classNames'
import styles from './DiffViewer.module.scss'
import { useEffect, useMemo, useRef } from 'react'
import { useVirtualizer } from '@tanstack/react-virtual'
import { splitDiff, toDiffLines } from '../../lib/diff'
import type { DiffLine } from '../../lib/diff'
import { usePreferences } from '../../lib/preferences'
import type { FileDiff, Issue } from '../../lib/types'
import type { WorkspaceProps } from '../workspace/types'
import { Icon, Link } from '../ui'
import Highlight from './Highlight'

const scrollPositions = new Map<
  string,
  { top: number; left: number }
>()

/** 接收文件 Diff、视图模式、可选定位行及导航信息；返回支持分批展开和问题跳转的 Diff 节点。 */
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
  const scrollRef = useRef<HTMLDivElement>(null)
  const key = `${returnTo.split('?')[0]}:${file.fileId}:${view}`
  const lines = useMemo(() => toDiffLines(file.rows), [file.rows])
  const splitLines = useMemo(
    () => (view === 'split' ? splitDiff(lines) : []),
    [lines, view],
  )
  const issueById = useMemo(
    () => new Map(file.issues.map((issue) => [issue.issueId, issue])),
    [file.issues],
  )
  const focusIndex = useMemo(() => {
    if (!focusLine) return -1
    const matches = (line: DiffLine | null) =>
      line && (file.changeType === 3 ? line.oldLine : line.newLine) === focusLine
    return view === 'unified'
      ? lines.findIndex(matches)
      : splitLines.findIndex((row) => matches(row.left) || matches(row.right))
  }, [focusLine, file.changeType, lines, splitLines, view])
  const virtualizer = useVirtualizer({
    count: view === 'unified' ? lines.length : splitLines.length,
    getScrollElement: () => scrollRef.current,
    estimateSize: () => 21,
    overscan: 12,
    initialOffset: () => (focusLine ? 0 : scrollPositions.get(key)?.top || 0),
  })
  const virtualRows = virtualizer.getVirtualItems()
  const topPadding = virtualRows[0]?.start || 0
  const bottomPadding = virtualRows.length
    ? virtualizer.getTotalSize() - virtualRows[virtualRows.length - 1].end
    : 0

  useEffect(() => {
    const container = scrollRef.current
    const saved = scrollPositions.get(key)
    if (container && saved && !focusLine) container.scrollLeft = saved.left
    if (focusIndex >= 0) virtualizer.scrollToIndex(focusIndex, { align: 'center' })
    return () => {
      if (container)
        scrollPositions.set(key, { top: container.scrollTop, left: container.scrollLeft })
    }
  }, [key, focusLine, focusIndex, virtualizer])
  /** 接收 Diff 行；返回关联问题，删除文件按旧行号范围匹配。 */
  function markers(line: DiffLine): Issue[] {
    if (file.changeType !== 3)
      return line.issueIds.flatMap((id) => {
        const issue = issueById.get(id)
        return issue ? [issue] : []
      })
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
  /** 接收 Diff 行；返回该行关联问题的跳转链接节点数组。 */
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
  /** 接收 Diff 行；返回其有效侧行号是否等于目标定位行。 */
  function focused(line: DiffLine) {
    return (
      !!focusLine &&
      (file.changeType === 3 ? line.oldLine : line.newLine) === focusLine
    )
  }
  /** 接收可空 Diff 行和新旧侧标记；返回并排视图的行号、代码及问题单元格节点。 */
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
        style={{ height: Math.min(650, virtualizer.getTotalSize()) }}
        tabIndex={0}
        role="region"
        aria-label={`${t('files')}: ${file.filePath}`}
      >
        <table className={classNames(styles['diff-table'], styles[view])}>
          <tbody>
            {topPadding > 0 && (
              <tr aria-hidden="true"><td colSpan={4} style={{ height: topPadding }} /></tr>
            )}
            {virtualRows.map((virtualRow) => {
              if (view === 'unified') {
                const line = lines[virtualRow.index]
                return (
                  <tr
                    key={line.index}
                    data-index={virtualRow.index}
                    ref={virtualizer.measureElement}
                    className={classNames(styles['diff-line'], styles[line.type])}
                    data-focused={focused(line)}
                  >
                    <td className={styles['line-number']}>{line.oldLine}</td>
                    <td className={styles['line-number']}>{line.newLine}</td>
                    <td className={styles['line-sign']}>
                      {line.type === 'add' ? '+' : line.type === 'delete' ? '−' : ''}
                    </td>
                    <td className={styles['code-cell']}>
                      <code><Highlight text={line.content} /></code>
                      {issueLinks(line)}
                    </td>
                  </tr>
                )
              }
              const row = splitLines[virtualRow.index]
              return (
                <tr
                  className={styles['diff-line']}
                  key={virtualRow.key}
                  data-index={virtualRow.index}
                  ref={virtualizer.measureElement}
                >
                  {cell(row.left, 'old')}
                  {cell(row.right, 'new')}
                </tr>
              )
            })}
            {bottomPadding > 0 && (
              <tr aria-hidden="true"><td colSpan={4} style={{ height: bottomPadding }} /></tr>
            )}
          </tbody>
        </table>
      </div>
    </>
  )
}
