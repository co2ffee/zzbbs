import { useEffect } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { List, ListItem, ListItemText, Paper, Typography } from '@mui/material'
import { api, fmtTime } from '../api'
import { useAuth } from '../auth'
import { Status, Title, useAsync } from '../components'

interface N { id: number; username?: string; action: string; topicId: number; title?: string; inTime: string }

export default function Notifications() {
  const { refreshNotRead } = useAuth()
  const { data, loading, error } = useAsync(() => api.get<{ notRead: N[]; read: N[] }>('/api/notification/list'), [])
  useEffect(() => {
    if (data?.notRead.length) api.get('/api/notification/markRead').then(refreshNotRead).catch(() => {})
  }, [data, refreshNotRead])

  const render = (title: string, items: N[]) => (
    <>
      <Typography variant="subtitle1" sx={{ mt: 2, mb: 1 }}>{title}（{items.length}）</Typography>
      <Paper><List disablePadding>
        {items.map((n) => (
          <ListItem key={n.id} divider component={RouterLink} to={`/topic/${n.topicId}`} sx={{ color: 'inherit' }}>
            <ListItemText primary={`${n.username ?? ''} ${n.action} 了你的话题 ${n.title ?? ''}`} secondary={fmtTime(n.inTime)} />
          </ListItem>
        ))}
        {items.length === 0 && <ListItem><ListItemText secondary="暂无" /></ListItem>}
      </List></Paper>
    </>
  )
  return (
    <>
      <Title>通知</Title>
      <Status loading={loading} error={error} />
      {data && <>{render('未读', data.notRead)}{render('已读', data.read)}</>}
    </>
  )
}
