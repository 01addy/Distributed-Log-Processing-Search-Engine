import { NavLink } from 'react-router-dom'
import { LayoutDashboard, Search, GitBranch, Send, Activity } from 'lucide-react'

const links = [
  { to: '/', icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/search', icon: Search, label: 'Search' },
  { to: '/trace', icon: GitBranch, label: 'Trace' },
  { to: '/ingest', icon: Send, label: 'Ingest' },
]

export default function Sidebar({ health }) {
  return (
    <aside className="fixed left-0 top-0 h-full w-56 bg-surface border-r border-border flex flex-col z-50">
      <div className="p-6 border-b border-border">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-accent/10 border border-accent/30 flex items-center justify-center glow-accent">
            <Activity size={16} className="text-accent" />
          </div>
          <div>
            <div className="font-display font-700 text-sm text-white tracking-wide">LogStream</div>
            <div className="text-[10px] text-muted font-mono uppercase tracking-widest">Analytics</div>
          </div>
        </div>
      </div>

      <nav className="flex-1 p-3 space-y-1">
        {links.map(({ to, icon: Icon, label }) => (
          <NavLink key={to} to={to} end={to === '/'}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-all duration-200 group
              ${isActive
                ? 'bg-accent/10 text-accent border border-accent/20'
                : 'text-muted hover:text-white hover:bg-white/5 border border-transparent'}`
            }>
            {({ isActive }) => (
              <>
                <Icon size={16} className={isActive ? 'text-accent' : 'text-muted group-hover:text-white'} />
                <span className="font-body font-medium">{label}</span>
              </>
            )}
          </NavLink>
        ))}
      </nav>

      <div className="p-4 border-t border-border space-y-2">
        {[
          { label: 'Ingestion', status: health.ingest },
          { label: 'Search API', status: health.search },
        ].map(({ label, status }) => (
          <div key={label} className="flex items-center justify-between">
            <span className="text-xs text-muted font-mono">{label}</span>
            <div className="flex items-center gap-1.5">
              <div className={`w-1.5 h-1.5 rounded-full ${status === 'UP' ? 'bg-success animate-pulse' : status === 'checking' ? 'bg-warning animate-pulse' : 'bg-danger'}`} />
              <span className={`text-[10px] font-mono uppercase ${status === 'UP' ? 'text-success' : status === 'checking' ? 'text-warning' : 'text-danger'}`}>
                {status}
              </span>
            </div>
          </div>
        ))}
      </div>
    </aside>
  )
}
