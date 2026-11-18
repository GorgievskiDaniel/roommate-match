// The login token lives in an HttpOnly cookie that the browser sends by itself. Earlier versions kept
// it in localStorage, so a leftover copy is removed.
localStorage.removeItem('roommate-match-token')

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly fieldErrors: Record<string, string> = {},
    /** Set by the backend when the page should react to a particular case. */
    readonly code?: string,
  ) {
    super(message)
  }
}

export async function api<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  let response: Response
  try {
    const isForm = options.body instanceof FormData
    response = await fetch(`/api${path}`, {
      method: options.method ?? 'GET',
      headers: {
        ...(options.body !== undefined && !isForm && { 'Content-Type': 'application/json' }),
      },
      body: isForm ? (options.body as FormData) : options.body !== undefined ? JSON.stringify(options.body) : undefined,
    })
  } catch {
    throw new ApiError(0, 'Серверот не е достапен. Обидете се повторно.')
  }
  if (response.status === 204) {
    return undefined as T
  }
  if (response.ok) {
    return response.json() as Promise<T>
  }
  const problem = await response.json().catch(() => null)
  // Without an answer from the backend itself, a gateway error means the backend is down.
  if (!problem?.detail && [502, 503, 504].includes(response.status)) {
    throw new ApiError(response.status, 'Серверот не е достапен. Обидете се повторно.')
  }
  throw new ApiError(
    response.status,
    problem?.detail ?? 'Настана грешка. Обидете се повторно.',
    problem?.errors,
    problem?.code,
  )
}
