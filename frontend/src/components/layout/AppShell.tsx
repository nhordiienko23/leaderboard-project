import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { Menu, X, Newspaper, Trophy, Users, UserCircle, Shield, LogIn, UserPlus, LogOut, ChevronDown } from 'lucide-react'
import { useAuth } from '../../auth/AuthContext'
import { Button } from '../ui/Button'
import { UserName } from '../../features/users/UserName'

interface AppShellProps {
  children: ReactNode
}

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `flex min-h-11 items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
    isActive
      ? 'bg-lb-primary text-white'
      : 'text-lb-muted hover:bg-lb-elevated hover:text-lb-text'
  }`

export function AppShell({ children }: AppShellProps) {
  const [mobileOpen, setMobileOpen] = useState(false)
  const [accountOpen, setAccountOpen] = useState(false)
  const mobileMenuButtonRef = useRef<HTMLButtonElement>(null)
  const mobileNavigationRef = useRef<HTMLElement>(null)
  const auth = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (!mobileOpen) return
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const previousActive = document.activeElement as HTMLElement | null
    const navigation = mobileNavigationRef.current
    const focusableSelector = 'a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])'
    const firstFocusable = navigation?.querySelector<HTMLElement>(focusableSelector)
    firstFocusable?.focus()

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setMobileOpen(false)
        return
      }
      if (event.key !== 'Tab' || !navigation) return
      const focusable = Array.from(navigation.querySelectorAll<HTMLElement>(focusableSelector))
      if (focusable.length === 0) return
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => {
      document.body.style.overflow = previous
      window.removeEventListener('keydown', onKeyDown)
      if (previousActive && document.contains(previousActive)) previousActive.focus()
      else mobileMenuButtonRef.current?.focus()
    }
  }, [mobileOpen])

  const closeNavigation = () => setMobileOpen(false)

  const handleLogout = async () => {
    setAccountOpen(false)
    setMobileOpen(false)
    await auth.logout()
    navigate('/login', { replace: true })
  }

  const links = (
    <nav aria-label="Main navigation" className="flex flex-col gap-1">
      <NavLink to="/posts" className={navLinkClass} onClick={closeNavigation}><Newspaper size={18} aria-hidden="true" />Posts</NavLink>
      <NavLink to="/leaderboard" className={navLinkClass} onClick={closeNavigation}><Trophy size={18} aria-hidden="true" />Leaderboard</NavLink>
      {auth.isAuthenticated && (
        <>
          <NavLink to="/users" className={navLinkClass} onClick={closeNavigation}><Users size={18} aria-hidden="true" />Users</NavLink>
          <NavLink to="/account" className={navLinkClass} onClick={closeNavigation}><UserCircle size={18} aria-hidden="true" />Account</NavLink>
        </>
      )}
      {auth.isAdmin && (
        <NavLink to="/admin" className={navLinkClass} onClick={closeNavigation}><Shield size={18} aria-hidden="true" />Admin</NavLink>
      )}
    </nav>
  )

  const identity = auth.claims ? <UserName userId={auth.claims.sub} retryOnError={false} /> : null

  return (
    <div className="min-h-screen bg-lb-background text-lb-text">
      <header className="sticky top-0 z-40 flex h-16 items-center justify-between border-b border-lb-border bg-lb-surface px-4 lg:hidden">
        <Link to="/posts" className="font-bold text-lb-text" aria-label="Leaderboard home">Leaderboard</Link>
        <button
          ref={mobileMenuButtonRef}
          type="button"
          className="inline-flex h-10 w-10 items-center justify-center rounded-lg border border-lb-border text-lb-text hover:bg-lb-elevated"
          aria-label={mobileOpen ? 'Close navigation menu' : 'Open navigation menu'}
          aria-expanded={mobileOpen}
          aria-controls="mobile-navigation"
          onClick={() => setMobileOpen((open) => !open)}
        >
          {mobileOpen ? <X size={20} aria-hidden="true" /> : <Menu size={20} aria-hidden="true" />}
        </button>
      </header>

      {mobileOpen && (
        <div className="fixed inset-0 z-30 lg:hidden">
          <button type="button" aria-label="Close navigation overlay" className="absolute inset-0 bg-black/60" onClick={closeNavigation} />
          <aside ref={mobileNavigationRef} id="mobile-navigation" className="absolute right-0 top-0 h-full w-[min(20rem,85vw)] border-l border-lb-border bg-lb-surface p-4 pt-20 shadow-xl">
            {links}
            <div className="mt-4 border-t border-lb-border pt-4">
              {!auth.isAuthenticated ? (
                <div className="grid gap-2">
                  <NavLink to="/login" className={navLinkClass} onClick={closeNavigation}><LogIn size={18} aria-hidden="true" />Login</NavLink>
                  <NavLink to="/register" className={navLinkClass} onClick={closeNavigation}><UserPlus size={18} aria-hidden="true" />Register</NavLink>
                </div>
              ) : (
                <div>
                  {identity && <p className="mb-2 px-3 text-sm text-lb-muted">{identity}</p>}
                  <Button variant="ghost" className="w-full justify-start" icon={<LogOut size={18} aria-hidden="true" />} onClick={handleLogout}>Logout</Button>
                </div>
              )}
            </div>
          </aside>
        </div>
      )}

      <div className="lg:grid lg:min-h-screen lg:grid-cols-[15rem_minmax(0,1fr)]">
        <aside className="hidden flex-col border-r border-lb-border bg-lb-surface lg:flex">
          <Link to="/posts" className="flex h-16 items-center border-b border-lb-border px-5 font-bold">Leaderboard</Link>
          <div className="flex-1 p-3">{links}</div>
          <div className="relative border-t border-lb-border p-3">
            {!auth.isAuthenticated ? (
              <div className="grid gap-1">
                <NavLink to="/login" className={navLinkClass}><LogIn size={18} aria-hidden="true" />Login</NavLink>
                <NavLink to="/register" className={navLinkClass}><UserPlus size={18} aria-hidden="true" />Register</NavLink>
              </div>
            ) : (
              <>
                <button
                  type="button"
                  className="flex w-full items-center justify-between rounded-lg px-3 py-2 text-left hover:bg-lb-elevated"
                  aria-expanded={accountOpen}
                  aria-controls="account-menu"
                  onClick={() => setAccountOpen((open) => !open)}
                >
                  <span><span className="block text-sm font-semibold">{identity}</span><span className="block text-xs text-lb-muted">{auth.claims?.role}</span></span>
                  <ChevronDown size={16} className={accountOpen ? 'rotate-180' : ''} aria-hidden="true" />
                </button>
                {accountOpen && (
                  <div id="account-menu" className="absolute bottom-full left-3 right-3 mb-2 rounded-lg border border-lb-border bg-lb-elevated p-2 shadow-lg">
                    <NavLink to="/account" className={navLinkClass} onClick={() => setAccountOpen(false)}><UserCircle size={18} aria-hidden="true" />Account settings</NavLink>
                    <Button variant="ghost" className="mt-1 w-full justify-start" icon={<LogOut size={18} aria-hidden="true" />} onClick={handleLogout}>Logout</Button>
                  </div>
                )}
              </>
            )}
          </div>
        </aside>

        <main className="min-w-0">
          <div className="mx-auto w-full max-w-[1280px] px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
            {children}
          </div>
        </main>
      </div>
    </div>
  )
}