import { normalizeApiError, type ApiError } from '../../api/errors'

export function getAuthError(cause: unknown): ApiError {
  if (
    typeof cause === 'object' && cause !== null &&
    'message' in cause && typeof cause.message === 'string' &&
    'status' in cause && (cause.status === null || typeof cause.status === 'number') &&
    'isNetworkError' in cause && typeof cause.isNetworkError === 'boolean' &&
    'fieldErrors' in cause && typeof cause.fieldErrors === 'object' &&
    cause.fieldErrors !== null && !Array.isArray(cause.fieldErrors) &&
    Object.values(cause.fieldErrors).every((value) => typeof value === 'string')
  ) {
    return {
      message: cause.message,
      status: cause.status,
      isNetworkError: cause.isNetworkError,
      fieldErrors: Object.fromEntries(Object.entries(cause.fieldErrors)),
    }
  }
  return normalizeApiError(cause)
}

export function passwordError(password: string): string | undefined {
  if (!password.trim()) return 'Password is required.'
  if (password.length > 72) return 'Password must not exceed 72 characters.'
  if (new TextEncoder().encode(password).length > 72) {
    return 'Password must not exceed 72 UTF-8 bytes.'
  }
}
