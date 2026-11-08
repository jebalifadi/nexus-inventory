import logoUrl from '../assets/logo.svg'
import logoSvg from '../assets/logo.svg?raw'

/** The Nexus logo ("Live pulse") from src/assets/logo.svg, as an image. */
export function Logo({ size = 34 }: { size?: number }) {
  return <img src={logoUrl} width={size} height={size} role="img" aria-label="Nexus logo" alt="Nexus logo" />
}

/**
 * The same logo drawn inline, so CSS can make its outer ring pulse (login page).
 * The markup is our own file from src/assets, never user input.
 */
export function PulsingLogo({ size = 88 }: { size?: number }) {
  return (
    <span
      className="pulsing-logo"
      role="img"
      aria-label="Nexus logo"
      style={{ width: size, height: size }}
      dangerouslySetInnerHTML={{ __html: logoSvg }}
    />
  )
}
