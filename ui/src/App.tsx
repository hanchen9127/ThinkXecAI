import { ConfigProvider } from 'antd'
import { BrowserRouter, Route, Routes, useLocation } from 'react-router'
import AgentTabContent from './components/tabs/AgentTabContent'
import ChatTabContent from './components/tabs/ChatTabContent'
import KnowledgeBaseTabContent from './components/tabs/KnowledgeBaseTabContent'
import AgentChatView from './components/views/AgentChatView'
import KnowledgeBaseView from './components/views/KnowledgeBaseView'
import NotFoundView from './components/views/NotFoundView'
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
          <Route path="/agent" element={<AgentChatView />} />
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

export default function App() {
  return (
    <ConfigProvider theme={theme}>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </ConfigProvider>
  )
}
