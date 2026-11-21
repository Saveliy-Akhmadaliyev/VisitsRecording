// Крошечные общие компоненты. UI-кит не подключали — админка минимальная намеренно.

import React from 'react'

export function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="card">
      <h2>{title}</h2>
      {children}
    </section>
  )
}

export function Button(props: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return <button className="btn" {...props} />
}

export function TextInput(props: React.InputHTMLAttributes<HTMLInputElement>) {
  return <input className="input" {...props} />
}

export function Select(props: React.SelectHTMLAttributes<HTMLSelectElement>) {
  return <select className="input" {...props} />
}

export function ErrorBox({ error }: { error: string | null }) {
  if (!error) return null
  return <div className="error">{error}</div>
}

export function Empty({ text }: { text: string }) {
  return <p className="empty">{text}</p>
}
