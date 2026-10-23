import type { AnchorHTMLAttributes } from 'react'
import { navigate } from '../../hooks'

export function Link({
  to,
  children,
  onClick,
  ...props
}: AnchorHTMLAttributes<HTMLAnchorElement> & { to: string }) {
  return (
    <a
      {...props}
      href={to}
      onClick={(event) => {
        onClick?.(event)
        if (
          event.defaultPrevented ||
          event.button !== 0 ||
          event.metaKey ||
          event.ctrlKey ||
          event.shiftKey ||
          event.altKey ||
          props.target
        )
          return
        event.preventDefault()
        navigate(to)
      }}
    >
      {children}
    </a>
  )
}
