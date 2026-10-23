import Loading from '../common/Loading'
import { ErrorNotice } from './ErrorNotice'

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
