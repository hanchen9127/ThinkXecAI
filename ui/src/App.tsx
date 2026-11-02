import { ConfigProvider } from 'antd'
import { type ReactNode, Suspense, lazy } from 'react'
import { BrowserRouter, Route, Routes, useLocation } from 'react-router'
import AgentTabContent from './components/tabs/AgentTabContent'
import ChatTabContent from './components/tabs/ChatTabContent'
import KnowledgeBaseTabContent from './components/tabs/KnowledgeBaseTabContent'
import AgentsProvider from './contexts/AgentsProvider'
import ChatSessionsProvider from './contexts/ChatSessionsProvider'
import KnowledgeBasesProvider from './contexts/KnowledgeBasesProvider'
import Content from './layout/Content'
import Layout from './layout/Layout'
import Sidebar from './layout/Sidebar'
import { sectionOf } from './layout/sections'
import { theme } from './theme'

// Each page is its own chunk, loaded when first opened: the chat brings a Markdown parser and
// sanitizer, the agent pages their dialogs (Phase 13 follow-up, the bundle was over Vite's 500 kB).
const AgentChatView = lazy(() => import('./components/views/AgentChatView'))
const AgentHomeView = lazy(() => import('./components/views/AgentHomeView'))
const AgentProfileView = lazy(() => import('./components/views/AgentProfileView'))
const KnowledgeBaseView = lazy(() => import('./components/views/KnowledgeBaseView'))
const NotFoundView = lazy(() => import('./components/views/NotFoundView'))

const TABS = {
  agents: <AgentTabContent />,
  chats: <ChatTabContent />,
  knowledgeBases: <KnowledgeBaseTabContent />,
}

/** The layout and the routes from tech-stack.md → Frontend Architecture; rendered inside a router. */
export function AppRoutes() {
  const section = sectionOf(useLocation().pathname)
  return (
    <Layout>
      <Sidebar active={section}>{section && TABS[section]}</Sidebar>
      <Content>
        <Suspense fallback={<p className="m-0 px-12 py-9 text-muted">Loading…</p>}>
          <Routes>
            <Route path="/" element={<AgentChatView />} />
            <Route path="/agent" element={<AgentHomeView />} />
            <Route path="/agent/:agentId" element={<AgentProfileView />} />
            <Route path="/chat" element={<AgentChatView />} />
            <Route path="/chat/:chatSessionId" element={<AgentChatView />} />
            <Route path="/knowledge-base" element={<KnowledgeBaseView />} />
            <Route path="/knowledge-base/:knowledgeBaseId" element={<KnowledgeBaseView />} />
            <Route path="*" element={<NotFoundView />} />
          </Routes>
        </Suspense>
      </Content>
    </Layout>
  )
}

/** The shared lists every screen reads. */
export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <ConfigProvider theme={theme}>
      <KnowledgeBasesProvider>
        <AgentsProvider>
          <ChatSessionsProvider>{children}</ChatSessionsProvider>
        </AgentsProvider>
      </KnowledgeBasesProvider>
    </ConfigProvider>
  )
}

export default function App() {
  return (
    <AppProviders>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </AppProviders>
  )
}
