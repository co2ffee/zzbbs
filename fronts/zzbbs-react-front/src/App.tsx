import { Navigate, Route, Routes } from 'react-router-dom'
import Layout from './Layout'
import { useAuth } from './auth'
import Home from './pages/Home'
import TagTopics from './pages/TagTopics'
import Tags from './pages/Tags'
import TopicDetail from './pages/TopicDetail'
import TopicEdit from './pages/TopicEdit'
import Login from './pages/Login'
import Register from './pages/Register'
import Profile from './pages/Profile'
import Notifications from './pages/Notifications'
import { ReactElement } from 'react'

function RequireAuth({ children }: { children: ReactElement }) {
  const { user } = useAuth()
  return user ? children : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Home />} />
        <Route path="/tags" element={<Tags />} />
        <Route path="/tag/:name" element={<TagTopics />} />
        <Route path="/topic/:id" element={<TopicDetail />} />
        <Route path="/topic/create" element={<RequireAuth><TopicEdit /></RequireAuth>} />
        <Route path="/topic/:id/edit" element={<RequireAuth><TopicEdit /></RequireAuth>} />
        <Route path="/user/:username" element={<Profile />} />
        <Route path="/notifications" element={<RequireAuth><Notifications /></RequireAuth>} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
