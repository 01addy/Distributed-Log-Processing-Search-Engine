import { useState } from 'react'
import { GitBranch, Search, Clock, AlertCircle } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import LevelBadge from '../components/LevelBadge'
import { api, formatTimestamp, LEVEL_COLORS } from '../utils/api'

export default function Trace() {
  const [searchParams] = useSearchParams()
  const [traceId, setTraceId] = useState(searchParams.get('id') || '')
  const [logs, setLogs] = useState([])
  const [loading, setLoading] = useState(false)
  const [searched, setSearched] = useState(false)

  const search = async () => {
    if (!traceId.trim()) return
    setLoading(true)
    try {
      const res = await api.getByTrace(traceId.trim())
      setLogs(res.data.logs || [])
      setSearched(true)
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }

  const errorCount = logs.filter(l => l.level === 'ERROR').length
  const duration = logs.length >= 2
    ? new Date(logs[logs.length-1].timestamp) - new Date(logs[0].timestamp)
    : 0

  return (
    <div className="space-y-6 animate-fade-in">
      <div>
        <h1 className="text-2xl font-display font-700 text-white">Trace Timeline</h1>
        <p className="text-sm text-muted mt-1 font-mono">Follow a request across all services</p>
      </div>

      <div className="bg-panel border border-border rounded-xl p-4">
        <div className="flex gap-2">
          <div className="flex-1 relative">
            <GitBranch size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
            <input value={traceId} onChange={e => setTraceId(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && search()}
              placeholder="Enter trace ID (e.g. a1b2c3d4e5f67890)"
              className="w-full bg-void border border-border rounded-lg pl-9 pr-4 py-2.5 text-sm font-mono text-white placeholder-muted focus:outline-none focus:border-accent/50 transition-colors"
            />
          </div>
          <button onClick={search}
            className="px-5 py-2.5 bg-accent text-void font-display font-600 text-sm rounded-lg hover:bg-accent/90 transition-colors flex items-center gap-2">
            <Search size={14} /> Find Trace
          </button>
        </div>
      </div>

      {searched && logs.length > 0 && (
        <div className="grid grid-cols-3 gap-4">
          {[
            { label: 'Total Spans', value: logs.length, color: 'text-accent' },
            { label: 'Errors', value: errorCount, color: 'text-danger' },
            { label: 'Duration', value: duration > 0 ? `${duration}ms` : '< 1ms', color: 'text-success' },
          ].map(({ label, value, color }) => (
            <div key={label} className="bg-panel border border-border rounded-xl p-4 text-center">
              <div className={`text-2xl font-display font-700 ${color}`}>{value}</div>
              <div className="text-xs text-muted font-mono mt-1 uppercase tracking-wider">{label}</div>
            </div>
          ))}
        </div>
      )}

      {searched && (
        <div className="bg-panel border border-border rounded-xl overflow-hidden">
          <div className="px-5 py-3 border-b border-border">
            <span className="text-xs font-mono text-muted uppercase tracking-wider">
              {logs.length} spans · Trace: <span className="text-accent">{traceId}</span>
            </span>
          </div>

          {logs.length === 0 ? (
            <div className="py-16 text-center">
              <GitBranch size={32} className="text-muted mx-auto mb-3 opacity-50" />
              <p className="text-muted text-sm">No logs found for this trace ID</p>
            </div>
          ) : (
            <div className="p-5 relative">
              <div className="absolute left-[42px] top-5 bottom-5 w-px bg-gradient-to-b from-accent/50 via-accent/20 to-transparent" />
              <div className="space-y-4">
                {logs.map((log, i) => (
                  <div key={log.id} className="flex gap-4 animate-slide-up" style={{ animationDelay: `${i * 60}ms` }}>
                    <div className="relative z-10 flex flex-col items-center">
                      <div className="w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0"
                        style={{ borderColor: LEVEL_COLORS[log.level] || '#4A5568', background: '#080B14' }}>
                        {log.level === 'ERROR' && <AlertCircle size={10} style={{ color: LEVEL_COLORS.ERROR }} />}
                        {log.level !== 'ERROR' && <div className="w-1.5 h-1.5 rounded-full" style={{ background: LEVEL_COLORS[log.level] }} />}
                      </div>
                      {i < logs.length - 1 && (
                        <div className="text-[9px] font-mono text-muted mt-1 whitespace-nowrap">
                          {i < logs.length - 1 && logs[i+1] ? `+${new Date(logs[i+1].timestamp) - new Date(log.timestamp)}ms` : ''}
                        </div>
                      )}
                    </div>
                    <div className={`flex-1 bg-void border rounded-lg p-3 ${log.level === 'ERROR' ? 'border-danger/30' : 'border-border'}`}>
                      <div className="flex items-center justify-between gap-2 mb-2">
                        <div className="flex items-center gap-2">
                          <LevelBadge level={log.level} size="xs" />
                          {log.serviceName && (
                            <span className="text-[10px] font-mono text-accent/70">{log.serviceName}</span>
                          )}
                        </div>
                        <div className="flex items-center gap-1 text-[10px] text-muted font-mono">
                          <Clock size={9} /> {formatTimestamp(log.timestamp)}
                        </div>
                      </div>
                      <p className="text-sm font-mono text-slate-300">{log.message}</p>
                      {log.stackTrace && (
                        <pre className="text-[10px] font-mono text-danger/70 mt-2 bg-danger/5 rounded p-2 overflow-x-auto whitespace-pre-wrap border border-danger/10">
                          {log.stackTrace.slice(0, 200)}{log.stackTrace.length > 200 ? '...' : ''}
                        </pre>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {!searched && (
        <div className="py-20 text-center">
          <div className="w-16 h-16 rounded-2xl bg-accent/5 border border-accent/20 flex items-center justify-center mx-auto mb-4">
            <GitBranch size={28} className="text-accent/50" />
          </div>
          <p className="text-muted text-sm font-mono">Enter a trace ID to visualize the request timeline</p>
          <p className="text-muted/50 text-xs font-mono mt-1">Trace IDs are found in search results</p>
        </div>
      )}
    </div>
  )
}
