const TOKEN_KEY = 'roommate-match-token'

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly fieldErrors: Record<string, string> = {},
  ) {
    super(message)
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

export async function api<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const token = getToken()
  let response: Response
  try {
    response = await fetch(`/api${path}`, {
      method: options.method ?? 'GET',
      headers: {
        ...(options.body !== undefined && { 'Content-Type': 'application/json' }),
        ...(token && { Authorization: `Bearer ${token}` }),
      },
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    })
  } catch {
    throw new ApiError(0, 'Серверот не е достапен. Обидете се повторно.')
  }
  if (response.ok) {
    return response.json() as Promise<T>
  }
  // The Vite proxy and any gateway answer these when the backend is down.
  if ([502, 503, 504].includes(response.status)) {
    throw new ApiError(response.status, 'Серверот не е достапен. Обидете се повторно.')
  }
  const problem = await response.json().catch(() => null)
  throw new ApiError(response.status, problem?.detail ?? 'Настана грешка. Обидете се повторно.', problem?.errors)
}
