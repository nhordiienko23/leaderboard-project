import axios, { type AxiosError } from 'axios';

export interface ApiError {
  status: number | null;
  message: string;
  fieldErrors: Record<string, string>;
  error?: string;
  path?: string;
  timestamp?: string;
  isNetworkError: boolean;
}

interface BackendErrorBody {
  status?: number;
  error?: string;
  message?: string;
  path?: string;
  timestamp?: string;
  fieldErrors?: Record<string, string>;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

export function normalizeApiError(error: unknown): ApiError {
  if (!axios.isAxiosError(error)) {
    return {
      status: null,
      message: error instanceof Error ? error.message : 'An unexpected error occurred.',
      fieldErrors: {},
      isNetworkError: false,
    };
  }

  const axiosError = error as AxiosError<unknown>;
  if (!axiosError.response) {
    return {
      status: null,
      message: 'Network error. Check your connection and try again.',
      fieldErrors: {},
      isNetworkError: true,
    };
  }

  const status = axiosError.response.status;
  const body = axiosError.response.data;
  let backendMessage: string | undefined;
  let fieldErrors: Record<string, string> = {};
  let errorName: string | undefined;
  let path: string | undefined;
  let timestamp: string | undefined;

  if (isRecord(body)) {
    const record = body as BackendErrorBody;
    if (typeof record.message === 'string' && record.message.trim()) {
      backendMessage = record.message;
    }
    if (typeof record.error === 'string') errorName = record.error;
    if (typeof record.path === 'string') path = record.path;
    if (typeof record.timestamp === 'string') timestamp = record.timestamp;
    if (isRecord(record.fieldErrors)) {
      fieldErrors = Object.fromEntries(
        Object.entries(record.fieldErrors).filter((entry): entry is [string, string] =>
          typeof entry[1] === 'string'
        ),
      );
    }
  }

  return {
    status,
    message: backendMessage ??
      (axiosError.message && axiosError.message !== 'Request failed with status code ' + status
        ? axiosError.message
        : 'The request could not be completed.'),
    fieldErrors,
    error: errorName,
    path,
    timestamp,
    isNetworkError: false,
  };
}

