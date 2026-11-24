import type { UserRole } from '../api/types';

export interface JwtClaims {
  sub: number;
  role: UserRole;
  exp?: number;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

export function decodeJwtPayload(token: string): unknown {
  const segments = token.split('.');
  if (segments.length !== 3) return null;

  try {
    const base64 = segments[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=');
    const binary = atob(padded);
    const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
    const json = new TextDecoder().decode(bytes);
    const payload: unknown = JSON.parse(json);
    return isRecord(payload) ? payload : null;
  } catch {
    return null;
  }
}

export function parseJwtClaims(token: string): JwtClaims | null {
  const payload = decodeJwtPayload(token);
  if (!payload) return null;

  const record = payload as Record<string, unknown>;
  const sub = typeof record.sub === 'string' && /^\d+$/.test(record.sub)
    ? Number(record.sub)
    : typeof record.sub === 'number'
      ? record.sub
      : null;

  if (sub === null || !Number.isSafeInteger(sub) || sub <= 0) return null;
  if (record.role !== 'USER' && record.role !== 'ADMIN') return null;

  const exp = typeof record.exp === 'number' && Number.isSafeInteger(record.exp)
    ? record.exp
    : undefined;

  return { sub, role: record.role, exp };
}

