import { useEffect, useState } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { checkHealth, type HealthCheckResult } from './api/health'
import { Layout } from './components/Layout'
import { OverviewPage } from './pages/OverviewPage'
import { ProjectDetailPage } from './pages/ProjectDetailPage'
import { ProjectsPage } from './pages/ProjectsPage'
import { SettingsPage } from './pages/SettingsPage'
import { TestCasePage } from './pages/TestCasePage'
import { TestsPage } from './pages/TestsPage'
import { traceLogger } from './services/traceLogger'

export default function App() {
  const [health, setHealth] = useState<HealthCheckResult | null>(null)

  async function refreshHealth() {
    const result = await checkHealth()
    setHealth(result)
    return result
  }

  useEffect(() => {
    traceLogger.info('UI', 'UI_PAGE_LOADED', 'SmartQA workspace loaded', {
      path: window.location.pathname,
      origin: window.location.origin,
    })
    void refreshHealth()
  }, [])

  return (
    <Layout health={health} onRetryHealth={() => void refreshHealth()}>
      <Routes>
        <Route
          path="/"
          element={<OverviewPage health={health} onRetryHealth={() => void refreshHealth()} />}
        />
        <Route path="/tests" element={<TestsPage />} />
        <Route path="/projects" element={<ProjectsPage />} />
        <Route path="/projects/:projectId" element={<ProjectDetailPage />} />
        <Route path="/projects/:projectId/test-cases/:testCaseId" element={<TestCasePage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Layout>
  )
}
