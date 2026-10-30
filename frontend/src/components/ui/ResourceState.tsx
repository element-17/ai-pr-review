import Loading from '../common/Loading'
import { ErrorNotice } from './ErrorNotice'

/** 接收加载标记、错误和重试回调；优先返回加载节点，其次错误节点，无状态时返回 null。 */
export function ResourceState({
  loading,
  error,
  retry,
}: {
  loading?: boolean
  error?: unknown
  retry?: () => void
}) {
  return loading ? (
    <Loading />
  ) : error ? (
    <ErrorNotice error={error} retry={retry} />
  ) : null
}
