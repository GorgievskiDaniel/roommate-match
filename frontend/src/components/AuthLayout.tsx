import type { ReactNode } from 'react'

/** The welcome screen around every sign-in form: you on one half, your future roommate on the other. */
export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="auth-layout">
      <section className="auth-intro">
        <h1>Најди цимер со кој навистина можеш да живееш.</h1>
        <p>Одговори на неколку прашања и види кој од студентите ти одговара за стан, куќа или соба во дом.</p>
      </section>
      <div className="auth-halves">
        <section className="auth-half auth-you">
          <h2>Ти</h2>
          <ul className="auth-traits">
            <li>Легнувам рано</li>
            <li>Не пушам</li>
            <li>Учам дома</li>
            <li>Програмирање</li>
            <li>Уреден</li>
          </ul>
        </section>
        <section className="auth-half auth-mate">
          <h2>Цимер</h2>
          <ul className="auth-traits">
            <li>Легнува рано</li>
            <li>Не пуши</li>
            <li>Учи во библиотека</li>
            <li>Програмирање</li>
            <li>Уредна</li>
          </ul>
        </section>
        <div className="auth-form">
          <svg className="auth-figure auth-figure-you" viewBox="0 0 200 300" aria-hidden="true">
            <rect x="86" y="96" width="28" height="50" fill="#f7f2e6" />
            <path d="M20 300 V210 A80 80 0 0 1 180 210 V300 Z" fill="#f0be3c" />
            <path d="M78 132 Q100 158 122 132 Z" fill="#f7f2e6" />
            <circle cx="100" cy="72" r="42" fill="#f7f2e6" />
            <path d="M56 74 A44 44 0 0 1 144 74 C130 50 92 44 56 74 Z" fill="#16241d" />
          </svg>
          <svg className="auth-figure auth-figure-mate" viewBox="0 0 200 300" aria-hidden="true">
            <rect x="48" y="26" width="104" height="150" rx="52" fill="#16241d" />
            <rect x="86" y="96" width="28" height="50" fill="#f7f2e6" />
            <path d="M20 300 V210 A80 80 0 0 1 180 210 V300 Z" fill="#1f4d3a" />
            <path d="M78 132 Q100 158 122 132 Z" fill="#f7f2e6" />
            <circle cx="100" cy="76" r="40" fill="#f7f2e6" />
            <path d="M60 76 A40 40 0 0 1 140 76 C120 62 104 44 60 76 Z" fill="#16241d" />
          </svg>
          {children}
        </div>
      </div>
    </div>
  )
}
