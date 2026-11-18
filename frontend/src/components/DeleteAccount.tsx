import { useEffect, useRef, useState, type FormEvent } from 'react'
import { api, ApiError } from '../api'
import { useAuth } from '../auth'

/** Deleting asks for the password in a dialog that says plainly the account cannot be brought back. */
export default function DeleteAccount() {
  const { refresh } = useAuth()
  const dialog = useRef<HTMLDialogElement>(null)
  const [open, setOpen] = useState(false)
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (open) dialog.current?.showModal()
    else dialog.current?.close()
  }, [open])

  function close() {
    if (busy) return
    setOpen(false)
    setPassword('')
    setError(null)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api('/me', { method: 'DELETE', body: { password } })
      // The session is gone, so loading the account again sends the page back to the login.
      await refresh()
    } catch (caught) {
      setError(caught instanceof ApiError ? (caught.fieldErrors.password ?? caught.message) : 'Настана грешка.')
      setBusy(false)
    }
  }

  return (
    <section className="card danger-zone">
      <h2>Бришење на акаунтот</h2>
      <p className="muted">Се бришат профилот, огласите што ги објави, сликите и сите разговори.</p>
      <button type="button" className="button danger" onClick={() => setOpen(true)}>
        Избриши акаунт
      </button>

      <dialog ref={dialog} className="confirm-dialog" onCancel={(event) => { event.preventDefault(); close() }}>
        <form onSubmit={submit}>
          <h2>Дали сте сигурни?</h2>
          <p className="warning">
            Акаунтот ќе биде избришан засекогаш и <strong>нема да може да се врати</strong>. Ќе се избришат профилот,
            огласите, сликите, пораките и совпаѓањата.
          </p>
          <label>
            За потврда, внесете ја лозинката
            <input
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              aria-invalid={error !== null}
              required
              autoFocus
            />
          </label>
          {error && <small role="alert" className="field-error">{error}</small>}
          <div className="dialog-actions">
            <button type="button" className="button secondary" onClick={close} disabled={busy}>
              Откажи
            </button>
            <button type="submit" className="button danger-solid" disabled={busy || password === ''}>
              {busy ? 'Се брише…' : 'Да, избриши засекогаш'}
            </button>
          </div>
        </form>
      </dialog>
    </section>
  )
}
