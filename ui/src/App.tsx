import { ConfigProvider } from 'antd'
import type { ReactNode } from 'react'
import { BrowserRouter, Route, Routes, useLocation } from 'react-router'
import AgentTabContent from './components/tabs/AgentTabContent'
import ChatTabContent from './components/tabs/ChatTabContent'
import KnowledgeBaseTabContent from './components/tabs/KnowledgeBaseTabContent'
import AgentChatView from './components/views/AgentChatView'
import AgentHomeView from './components/views/AgentHomeView'
import AgentProfileView from './components/views/AgentProfileView'
import KnowledgeBaseView from './components/views/KnowledgeBaseView'
import NotFoundView from './components/views/NotFoundView'
import AgentsProvider from './contexts/AgentsProvider'
import ChatSessionsProvider from './contexts/ChatSessionsProvider'
import KnowledgeBasesProvider from './contexts/KnowledgeBasesProvider'
import Content from './layout/Content'
import Layout from './layout/Layout'
import Sidebar from './layout/Sidebar'
import { sectionOf } from './layout/sections'
import { theme } from './theme'

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
