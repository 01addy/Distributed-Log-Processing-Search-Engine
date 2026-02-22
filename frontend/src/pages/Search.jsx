import { useState, useEffect, useCallback } from 'react'
import { Search as SearchIcon, Filter, X, ChevronLeft, ChevronRight, Loader } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import LogRow from '../components/LogRow'
import LevelBadge from '../components/LevelBadge'
import { api } from '../utils/api'

const LEVELS = ['ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE']

export default function Search() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get('q') || '')
  const [service, setService] = useState(searchParams.get('service') || '')
  const [levels, setLevels] = useState([])
  const [page, setPage] = useState(0)
  const [results, setResults] = useState(null)
  const [loading, setLoading] = useState(false)
  const [showFilters, setShowFilters] = useState(false)

  const doSearch = useCallback(async (p = 0) => {
    setLoading(true)
    try {
      const params = { page: p, size: 20, sortOrder: 'desc' }
      if (query.trim()) params.query = query.trim()
      if (service.trim()) params.serviceName = service.trim()
      if (levels.length > 0) params.levels = levels.join(',')
      const res = await api.search(params)
      setResults(res.data)
      setPage(p)
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }, [query, service, levels])

  useEffect(() => { doSearch(0) }, [])

  const toggleLevel = (l) => setLevels(prev => prev.includes(l) ? prev.filter(x => x !== l) : [...prev, l])
  const clearFilters = () => { setQuery(''); setService(''); setLevels([]) }

  const activeFilters = (query ? 1 : 0) + (service ? 1 : 0) + levels.length

  return (
    <div className="space-y-4 animate-fade-in">
      <div>
        <h1 className="text-2xl font-display font-700 text-white">Search Logs</h1>
        <p className="text-sm text-muted mt-1 font-mono">Full-text and filtered log search</p>
      </div>

      <div className="bg-panel border border-border rounded-xl p-4 space-y-3">
        <div className="flex gap-2">
          <div className="flex-1 relative">
            <SearchIcon size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
            <input
              value={query}
              onChange={e => setQuery(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && doSearch(0)}
              placeholder="Search messages, errors, stack traces..."
              className="w-full bg-void border border-border rounded-lg pl-9 pr-4 py-2.5 text-sm font-mono text-white placeholder-muted focus:outline-none focus:border-accent/50 transition-colors"
            />
          </div>
          <button onClick={() => setShowFilters(!showFilters)}
            className={`flex items-center gap-2 px-3 py-2.5 rounded-lg border text-sm font-mono transition-all ${showFilters || activeFilters > 0 ? 'border-accent/30 text-accent bg-accent/5' : 'border-border text-muted hover:text-white'}`}>
            <Filter size={14} />
            Filters
            {activeFilters > 0 && <span className="bg-accent text-void text-[10px] font-700 w-4 h-4 rounded-full flex items-center justify-center">{activeFilters}</span>}
          </button>
          <button onClick={() => doSearch(0)}
            className="px-5 py-2.5 bg-accent text-void font-display font-600 text-sm rounded-lg hover:bg-accent/90 transition-colors">
            Search
          </button>
        </div>

        {showFilters && (
          <div className="border-t border-border pt-3 space-y-3 animate-fade-in">
            <div className="flex gap-3">
              <div className="flex-1">
                <label className="text-[10px] font-mono uppercase tracking-wider text-muted mb-1.5 block">Service Name</label>
                <input value={service} onChange={e => setService(e.target.value)}
                  placeholder="e.g. payment-service"
                  className="w-full bg-void border border-border rounded-lg px-3 py-2 text-sm font-mono text-white placeholder-muted focus:outline-none focus:border-accent/50 transition-colors" />
              </div>
            </div>
            <div>
              <label className="text-[10px] font-mono uppercase tracking-wider text-muted mb-1.5 block">Log Levels</label>
              <div className="flex gap-2">
                {LEVELS.map(l => (
                  <button key={l} onClick={() => toggleLevel(l)}
                    className={`px-3 py-1 rounded-lg border text-xs font-mono transition-all ${levels.includes(l) ? `level-${l} border-current` : 'border-border text-muted hover:border-white/20 hover:text-white'}`}>
                    {l}
                  </button>
                ))}
                {activeFilters > 0 && (
                  <button onClick={clearFilters} className="flex items-center gap-1 px-3 py-1 text-xs text-muted hover:text-danger transition-colors font-mono">
                    <X size={10} /> Clear all
                  </button>
                )}
              </div>
            </div>
          </div>
        )}
      </div>

      <div className="bg-panel border border-border rounded-xl overflow-hidden">
        <div className="px-5 py-3 border-b border-border flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="text-xs font-mono text-muted uppercase tracking-wider">Results</span>
            {results && (
              <span className="text-xs font-mono text-accent bg-accent/10 border border-accent/20 px-2 py-0.5 rounded">
                {results.totalHits?.toLocaleString()} found · {results.tookMs}ms
              </span>
            )}
          </div>
          {results?.totalPages > 1 && (
            <div className="flex items-center gap-2 text-xs font-mono text-muted">
              <button onClick={() => doSearch(page - 1)} disabled={page === 0}
                className="p-1 hover:text-white disabled:opacity-30 transition-colors">
                <ChevronLeft size={14} />
              </button>
              Page {page + 1} of {results.totalPages}
              <button onClick={() => doSearch(page + 1)} disabled={page >= results.totalPages - 1}
                className="p-1 hover:text-white disabled:opacity-30 transition-colors">
                <ChevronRight size={14} />
              </button>
            </div>
          )}
        </div>

        {loading ? (
          <div className="py-16 flex items-center justify-center gap-3 text-muted">
            <Loader size={16} className="animate-spin" />
            <span className="text-sm font-mono">Searching...</span>
          </div>
        ) : results?.hits?.length === 0 ? (
          <div className="py-16 text-center">
            <SearchIcon size={32} className="text-muted mx-auto mb-3 opacity-50" />
            <p className="text-muted text-sm">No logs found matching your query</p>
          </div>
        ) : (
          <div>
            {(results?.hits || []).map((log, i) => <LogRow key={log.id} log={log} idx={i} />)}
          </div>
        )}
      </div>
    </div>
  )
}
