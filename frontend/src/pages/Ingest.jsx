import { useState } from 'react'
import { Send, Plus, Trash2, CheckCircle, XCircle, Loader } from 'lucide-react'
import { api } from '../utils/api'

export default function Ingest() {
  const [mode, setMode] = useState('single')
  const [service, setService] = useState('')
  const [host, setHost] = useState('')
  const [singleLog, setSingleLog] = useState('')
  const [batchLogs, setBatchLogs] = useState(['', ''])
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)

  const addLog = () => setBatchLogs(prev => [...prev, ''])
  const removeLog = (i) => setBatchLogs(prev => prev.filter((_, idx) => idx !== i))
  const updateLog = (i, val) => setBatchLogs(prev => prev.map((l, idx) => idx === i ? val : l))

  const submit = async () => {
    if (!service.trim()) return
    setLoading(true)
    setResult(null)
    try {
      if (mode === 'single') {
        const res = await api.ingest({ serviceName: service, host, log: singleLog })
        setResult({ success: true, data: res.data })
      } else {
        const logs = batchLogs.filter(l => l.trim())
        const res = await api.ingestBatch({ serviceName: service, host, logs })
        setResult({ success: true, data: res.data })
      }
    } catch (e) {
      setResult({ success: false, error: e.response?.data?.message || e.message })
    } finally {
      setLoading(false)
    }
  }

  const SAMPLES = [
    { label: 'Logback ERROR', val: '2024-01-15 10:23:45.123 [main] ERROR com.example.PaymentService - Payment timeout after 5000ms traceId=abc123' },
    { label: 'Log4j WARN', val: '2024-01-15 10:24:00,000 WARN [RateLimiter] - Rate limit 90% reached for IP 192.168.1.1' },
    { label: 'JSON', val: '{"level":"INFO","message":"User authenticated","timestamp":"2024-01-15T10:25:00Z","userId":"user_12345"}' },
    { label: 'Plaintext', val: 'ERROR: Database connection pool exhausted, active=50, max=50' },
  ]

  return (
    <div className="space-y-6 animate-fade-in">
      <div>
        <h1 className="text-2xl font-display font-700 text-white">Ingest Logs</h1>
        <p className="text-sm text-muted mt-1 font-mono">Send logs to the ingestion pipeline</p>
      </div>

      <div className="grid grid-cols-3 gap-6">
        <div className="col-span-2 space-y-4">
          <div className="bg-panel border border-border rounded-xl p-5 space-y-4">
            <div className="flex gap-1 p-1 bg-void rounded-lg border border-border w-fit">
              {['single', 'batch'].map(m => (
                <button key={m} onClick={() => setMode(m)}
                  className={`px-4 py-1.5 rounded-md text-xs font-mono uppercase tracking-wider transition-all ${mode === m ? 'bg-accent text-void font-600' : 'text-muted hover:text-white'}`}>
                  {m}
                </button>
              ))}
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-[10px] font-mono uppercase tracking-wider text-muted mb-1.5 block">Service Name *</label>
                <input value={service} onChange={e => setService(e.target.value)}
                  placeholder="payment-service"
                  className="w-full bg-void border border-border rounded-lg px-3 py-2.5 text-sm font-mono text-white placeholder-muted focus:outline-none focus:border-accent/50 transition-colors" />
              </div>
              <div>
                <label className="text-[10px] font-mono uppercase tracking-wider text-muted mb-1.5 block">Host</label>
                <input value={host} onChange={e => setHost(e.target.value)}
                  placeholder="prod-node-01"
                  className="w-full bg-void border border-border rounded-lg px-3 py-2.5 text-sm font-mono text-white placeholder-muted focus:outline-none focus:border-accent/50 transition-colors" />
              </div>
            </div>

            {mode === 'single' ? (
              <div>
                <label className="text-[10px] font-mono uppercase tracking-wider text-muted mb-1.5 block">Log Entry</label>
                <textarea value={singleLog} onChange={e => setSingleLog(e.target.value)} rows={4}
                  placeholder="Paste your log line here..."
                  className="w-full bg-void border border-border rounded-lg px-3 py-2.5 text-sm font-mono text-white placeholder-muted focus:outline-none focus:border-accent/50 transition-colors resize-none" />
              </div>
            ) : (
              <div className="space-y-2">
                <label className="text-[10px] font-mono uppercase tracking-wider text-muted block">Log Entries ({batchLogs.filter(l=>l.trim()).length} valid)</label>
                {batchLogs.map((log, i) => (
                  <div key={i} className="flex gap-2 animate-fade-in">
                    <input value={log} onChange={e => updateLog(i, e.target.value)}
                      placeholder={`Log entry ${i + 1}...`}
                      className="flex-1 bg-void border border-border rounded-lg px-3 py-2 text-sm font-mono text-white placeholder-muted focus:outline-none focus:border-accent/50 transition-colors" />
                    <button onClick={() => removeLog(i)} className="p-2 text-muted hover:text-danger transition-colors">
                      <Trash2 size={14} />
                    </button>
                  </div>
                ))}
                <button onClick={addLog}
                  className="flex items-center gap-2 text-xs text-muted hover:text-accent transition-colors font-mono py-1">
                  <Plus size={12} /> Add log entry
                </button>
              </div>
            )}

            <button onClick={submit} disabled={loading || !service.trim()}
              className="w-full flex items-center justify-center gap-2 py-3 bg-accent text-void font-display font-600 rounded-lg hover:bg-accent/90 disabled:opacity-50 disabled:cursor-not-allowed transition-all">
              {loading ? <Loader size={16} className="animate-spin" /> : <Send size={16} />}
              {loading ? 'Sending...' : mode === 'single' ? 'Send Log' : 'Send Batch'}
            </button>

            {result && (
              <div className={`flex items-start gap-3 p-3 rounded-lg border animate-fade-in ${result.success ? 'bg-success/5 border-success/20' : 'bg-danger/5 border-danger/20'}`}>
                {result.success ? <CheckCircle size={16} className="text-success shrink-0 mt-0.5" /> : <XCircle size={16} className="text-danger shrink-0 mt-0.5" />}
                <div className="text-xs font-mono">
                  {result.success ? (
                    <div className="text-success">
                      {result.data.status === 'accepted' ? `✓ Log accepted · ID: ${result.data.eventId}` : `✓ ${result.data.acceptedCount}/${result.data.requestedCount} logs accepted`}
                    </div>
                  ) : (
                    <div className="text-danger">Error: {result.error}</div>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>

        <div className="space-y-4">
          <div className="bg-panel border border-border rounded-xl p-4">
            <h3 className="text-xs font-mono uppercase tracking-widest text-muted mb-3">Sample Logs</h3>
            <div className="space-y-2">
              {SAMPLES.map(({ label, val }) => (
                <button key={label} onClick={() => { setSingleLog(val); setMode('single') }}
                  className="w-full text-left p-2.5 bg-void border border-border rounded-lg hover:border-accent/30 transition-all group">
                  <div className="text-[10px] font-mono text-accent/70 uppercase tracking-wider mb-1">{label}</div>
                  <div className="text-xs font-mono text-muted group-hover:text-slate-300 transition-colors truncate">{val.slice(0, 60)}...</div>
                </button>
              ))}
            </div>
          </div>

          <div className="bg-panel border border-border rounded-xl p-4">
            <h3 className="text-xs font-mono uppercase tracking-widest text-muted mb-3">Supported Formats</h3>
            <div className="space-y-2">
              {['Logback', 'Log4j', 'JSON', 'Plaintext'].map(f => (
                <div key={f} className="flex items-center gap-2 text-xs">
                  <div className="w-1.5 h-1.5 rounded-full bg-success" />
                  <span className="font-mono text-muted">{f}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
