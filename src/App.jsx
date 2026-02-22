import { Routes, Route } from 'react-router-dom'
import { useState, useEffect } from 'react'
import Sidebar from './components/Sidebar'
import Dashboard from './pages/Dashboard'
import Search from './pages/Search'
import Trace from './pages/Trace'
import Ingest from './pages/Ingest'
import { api } from './utils/api'

export default function App() {
  const [health, setHealth] = useState({ ingest: 'checking', search: 'checking' })

  useEffect(() => {
    const check = async () => {
      const [ingest, search] = await Promise.allSettled([
        api.ingestHealth(), api.searchHealth()
      ])
      setHealth({
        ingest: ingest.status === 'fulfilled' ? ingest.value.data.status : 'DOWN',
        search: search.status === 'fulfilled' ? search.value.data.status : 'DOWN',
      })
    }
    check()
    const interval = setInterval(check, 30000)
    return () => clearInterval(interval)
  }, [])

  return (
    <div className="min-h-screen bg-void grid-bg">
      <div className="scanline" />
      <Sidebar health={health} />
      <main className="ml-56 p-8 min-h-screen">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/search" element={<Search />} />
          <Route path="/trace" element={<Trace />} />
          <Route path="/ingest" element={<Ingest />} />
        </Routes>
      </main>
    </div>
  )
}
