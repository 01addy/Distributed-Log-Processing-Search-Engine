import { useState } from 'react'
import { ChevronDown, ChevronRight, ExternalLink, GitBranch } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import LevelBadge from './LevelBadge'
import { formatTimestamp } from '../utils/api'

export default function LogRow({ log, idx }) {
  const [expanded, setExpanded] = useState(false)
  const navigate = useNavigate()

  return (
    <div className={`border-b border-border/50 hover:bg-white/[0.02] transition-colors duration-150 animate-fade-in`}
      style={{ animationDelay: `${idx * 30}ms` }}>
      <div className="flex items-start gap-3 px-4 py-3 cursor-pointer" onClick={() => setExpanded(!expanded)}>
        <button className="mt-0.5 text-muted hover:text-white transition-colors">
          {expanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
        </button>
        <div className="w-28 shrink-0">
          <LevelBadge level={log.level} size="xs" />
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-sm text-slate-200 font-mono truncate">{log.message}</p>
        </div>
        <div className="shrink-0 text-right space-y-0.5">
          {log.serviceName && (
            <div className="text-[10px] font-mono text-accent/70 bg-accent/5 border border-accent/10 px-2 py-0.5 rounded">
              {log.serviceName}
            </div>
          )}
          <div className="text-[10px] text-muted font-mono">{formatTimestamp(log.timestamp)}</div>
        </div>
      </div>

      {expanded && (
        <div className="px-4 pb-4 ml-5 border-l-2 border-accent/20 ml-[42px] mr-4 mb-2 animate-fade-in">
          <div className="bg-void rounded-lg border border-border p-4 space-y-3">
            <div className="grid grid-cols-2 gap-3 text-xs">
              {[
                ['Service', log.serviceName], ['Host', log.host],
                ['Trace ID', log.traceId], ['Level', log.level],
                ['Timestamp', formatTimestamp(log.timestamp)],
                ['Ingested', formatTimestamp(log.ingestedAt)],
              ].map(([k, v]) => v ? (
                <div key={k}>
                  <div className="text-muted font-mono uppercase text-[9px] tracking-wider mb-1">{k}</div>
                  <div className="text-slate-300 font-mono">{v}</div>
                </div>
              ) : null)}
            </div>

            {log.message && (
              <div>
                <div className="text-muted font-mono uppercase text-[9px] tracking-wider mb-1">Message</div>
                <div className="text-slate-200 text-sm bg-black/30 rounded p-2 font-mono">{log.message}</div>
              </div>
            )}

            {log.stackTrace && (
              <div>
                <div className="text-danger font-mono uppercase text-[9px] tracking-wider mb-1">Stack Trace</div>
                <pre className="text-xs text-slate-400 font-mono bg-black/40 rounded p-3 overflow-x-auto whitespace-pre-wrap border border-danger/10">
                  {log.stackTrace}
                </pre>
              </div>
            )}

            <div className="flex gap-2 pt-1">
              {log.traceId && (
                <button onClick={() => navigate(`/trace?id=${log.traceId}`)}
                  className="flex items-center gap-1.5 text-xs text-accent border border-accent/20 px-3 py-1.5 rounded-lg hover:bg-accent/10 transition-colors font-mono">
                  <GitBranch size={12} /> Trace Timeline
                </button>
              )}
              <button onClick={() => navigate(`/search?id=${log.id}`)}
                className="flex items-center gap-1.5 text-xs text-muted border border-border px-3 py-1.5 rounded-lg hover:text-white hover:border-white/20 transition-colors font-mono">
                <ExternalLink size={12} /> View Full Log
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
