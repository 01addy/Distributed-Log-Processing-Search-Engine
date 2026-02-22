import axios from 'axios'

const searchApi = axios.create({ baseURL: 'http://localhost:8083', timeout: 10000 })
const ingestApi = axios.create({ baseURL: 'http://localhost:8081', timeout: 10000 })

export const api = {
  search: (params) => searchApi.get('/api/v1/search', { params }),
  getById: (id) => searchApi.get(`/api/v1/search/${id}`),
  getByTrace: (traceId) => searchApi.get(`/api/v1/search/trace/${traceId}`),
  searchHealth: () => searchApi.get('/api/v1/search/health'),
  ingest: (body) => ingestApi.post('/api/v1/logs', body),
  ingestBatch: (body) => ingestApi.post('/api/v1/logs/batch', body),
  ingestHealth: () => ingestApi.get('/api/v1/logs/health'),
}

export const LEVEL_COLORS = {
  ERROR: '#FF3B5C', WARN: '#FFB800', INFO: '#00D4FF', DEBUG: '#00FF88', TRACE: '#A78BFA'
}

export const formatTimestamp = (ts) => {
  if (!ts) return '—'
  const d = new Date(ts)
  return d.toLocaleString('en-US', { month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })
}

export const timeAgo = (ts) => {
  if (!ts) return ''
  const diff = Date.now() - new Date(ts).getTime()
  if (diff < 60000) return `${Math.floor(diff/1000)}s ago`
  if (diff < 3600000) return `${Math.floor(diff/60000)}m ago`
  if (diff < 86400000) return `${Math.floor(diff/3600000)}h ago`
  return `${Math.floor(diff/86400000)}d ago`
}
