import { useState, useEffect, useCallback } from 'react'
import { PieChart, Pie, Cell, BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts'
import { AlertCircle, Activity, Layers, TrendingUp, RefreshCw } from 'lucide-react'
import StatCard from '../components/StatCard'
import LevelBadge from '../components/LevelBadge'
import { api, LEVEL_COLORS, formatTimestamp } from '../utils/api'

const CustomTooltip = ({ active, payload }) => {
  if (!active || !payload?.length) return null
  return (
    <div className="bg-panel border border-border rounded-lg px-3 py-2 text-xs font-mono">
      <div className="text-white font-500">{payload[0].name}</div>
      <div className="text-accent">{payload[0].value} logs</div>
    </div>
  )
}

export default function Dashboard() {
  const [data, setData] = useState(null)
  const [recent, setRecent] = useState([])
  const [loading, setLoading] = useState(true)
  const [lastRefresh, setLastRefresh] = useState(new Date())

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await api.search({ size: 50, sortOrder: 'desc' })
      const d = res.data
      setData(d)
      setRecent((d.hits || []).slice(0, 8))
      setLastRefresh(new Date())
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load])

  const levelData = data?.levelCounts
    ? Object.entries(data.levelCounts).map(([name, value]) => ({ name, value }))
    : []

  const serviceData = data?.serviceCounts
    ? Object.entries(data.serviceCounts).map(([name, value]) => ({ name, value })).slice(0, 8)
    : []

  const errorCount = data?.levelCounts?.ERROR || 0
  const warnCount = data?.levelCounts?.WARN || 0
  const total = data?.totalHits || 0

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-display font-700 text-white">Dashboard</h1>
          <p className="text-sm text-muted mt-1 font-mono">System overview · Updated {formatTimestamp(lastRefresh)}</p>
        </div>
        <button onClick={load} disabled={loading}
          className="flex items-center gap-2 text-xs text-muted border border-border px-3 py-2 rounded-lg hover:text-accent hover:border-accent/30 transition-all font-mono">
          <RefreshCw size={12} className={loading ? 'animate-spin' : ''} /> Refresh
        </button>
      </div>

      <div className="grid grid-cols-4 gap-4">
        <StatCard label="Total Logs" value={total.toLocaleString()} icon={Layers} color="accent" />
        <StatCard label="Errors" value={errorCount.toLocaleString()} icon={AlertCircle} color="danger" />
        <StatCard label="Warnings" value={warnCount.toLocaleString()} icon={Activity} color="warning" />
        <StatCard label="Services" value={Object.keys(data?.serviceCounts || {}).length} icon={TrendingUp} color="success" />
      </div>

      <div className="grid grid-cols-5 gap-4">
        <div className="col-span-2 bg-panel border border-border rounded-xl p-5">
          <h3 className="text-xs font-mono uppercase tracking-widest text-muted mb-4">Log Level Distribution</h3>
          {levelData.length > 0 ? (
            <>
              <ResponsiveContainer width="100%" height={180}>
                <PieChart>
                  <Pie data={levelData} cx="50%" cy="50%" innerRadius={50} outerRadius={80}
                    dataKey="value" stroke="none">
                    {levelData.map((entry) => (
                      <Cell key={entry.name} fill={LEVEL_COLORS[entry.name] || '#4A5568'} fillOpacity={0.85} />
                    ))}
                  </Pie>
                  <Tooltip content={<CustomTooltip />} />
                </PieChart>
              </ResponsiveContainer>
              <div className="flex flex-wrap gap-2 mt-2">
                {levelData.map(({ name, value }) => (
                  <div key={name} className="flex items-center gap-1.5 text-xs">
                    <div className="w-2 h-2 rounded-full" style={{ background: LEVEL_COLORS[name] }} />
                    <span className="text-muted font-mono">{name}</span>
                    <span className="text-white font-mono">{value}</span>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <div className="h-48 flex items-center justify-center text-muted text-sm">No data</div>
          )}
        </div>

        <div className="col-span-3 bg-panel border border-border rounded-xl p-5">
          <h3 className="text-xs font-mono uppercase tracking-widest text-muted mb-4">Logs by Service</h3>
          {serviceData.length > 0 ? (
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={serviceData} margin={{ top: 0, right: 0, left: -20, bottom: 0 }}>
                <XAxis dataKey="name" tick={{ fill: '#4A5568', fontSize: 10, fontFamily: 'JetBrains Mono' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fill: '#4A5568', fontSize: 10, fontFamily: 'JetBrains Mono' }} axisLine={false} tickLine={false} />
                <Tooltip content={<CustomTooltip />} cursor={{ fill: 'rgba(0,212,255,0.05)' }} />
                <Bar dataKey="value" fill="#00D4FF" fillOpacity={0.7} radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <div className="h-48 flex items-center justify-center text-muted text-sm">No data</div>
          )}
        </div>
      </div>

      <div className="bg-panel border border-border rounded-xl overflow-hidden">
        <div className="px-5 py-4 border-b border-border flex items-center justify-between">
          <h3 className="text-xs font-mono uppercase tracking-widest text-muted">Recent Logs</h3>
          <span className="text-xs text-muted font-mono">{recent.length} shown</span>
        </div>
        <div className="divide-y divide-border/50">
          {loading ? (
            Array(5).fill(0).map((_, i) => (
              <div key={i} className="px-4 py-3 flex gap-3 animate-pulse">
                <div className="w-16 h-4 bg-subtle rounded" />
                <div className="flex-1 h-4 bg-subtle rounded" />
                <div className="w-24 h-4 bg-subtle rounded" />
              </div>
            ))
          ) : recent.length === 0 ? (
            <div className="py-12 text-center text-muted text-sm">No logs found. Start ingesting data.</div>
          ) : (
            recent.map((log, i) => (
              <div key={log.id} className="flex items-center gap-3 px-4 py-3 hover:bg-white/[0.02] transition-colors group"
                style={{ animationDelay: `${i * 40}ms` }}>
                <LevelBadge level={log.level} size="xs" />
                <p className="flex-1 text-xs font-mono text-slate-300 truncate">{log.message}</p>
                {log.serviceName && (
                  <span className="text-[10px] font-mono text-accent/60 shrink-0">{log.serviceName}</span>
                )}
                <span className="text-[10px] font-mono text-muted shrink-0">{formatTimestamp(log.timestamp)}</span>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  )
}
