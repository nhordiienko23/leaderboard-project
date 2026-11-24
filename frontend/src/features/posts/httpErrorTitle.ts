import type { ApiError } from '../../api/errors'

export function httpErrorTitle(error: ApiError): string | undefined {
  if (error.status === 403) return 'Forbidden'
  if (error.status === 404) return 'Not found'
}
