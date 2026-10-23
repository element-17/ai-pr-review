import type { DiffRow } from './types'

// 行号映射以后端 /files/{fileId}/diff 的结构化结果为准，前端只做展示层转换
export interface DiffLine extends DiffRow {
  index: number
}
export interface SplitLine {
  left: DiffLine | null
  right: DiffLine | null
}

export function toDiffLines(rows: DiffRow[]): DiffLine[] {
  return rows.map((row, index) => ({ ...row, index }))
}

export function splitDiff(lines: DiffLine[]): SplitLine[] {
  const result: SplitLine[] = []
  let position = 0
  while (position < lines.length) {
    const line = lines[position]
    if (line.type === 'delete' || line.type === 'add') {
      const removed: DiffLine[] = []
      const added: DiffLine[] = []
      while (lines[position]?.type === 'delete') removed.push(lines[position++])
      while (lines[position]?.type === 'add') added.push(lines[position++])
      for (
        let offset = 0;
        offset < Math.max(removed.length, added.length);
        offset++
      )
        result.push({
          left: removed[offset] || null,
          right: added[offset] || null,
        })
    } else {
      result.push({ left: line, right: line })
      position++
    }
  }
  return result
}
