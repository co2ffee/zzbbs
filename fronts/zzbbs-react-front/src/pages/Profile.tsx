import { useState } from 'react'
import { useParams, Link as RouterLink } from 'react-router-dom'
import { Alert, Avatar, Box, Button, Paper, Stack, Tab, Tabs, TextField, Typography } from '@mui/material'
import { api, fmtTime, Page, TopicItem, User } from '../api'
import { useAuth } from '../auth'
import { Status, TopicList, useAsync } from '../components'

type Tabkey = 'topics' | 'comments' | 'collects' | 'settings'

export default function Profile() {
  const { username = '' } = useParams()
  const { user: me } = useAuth()
  const [tab, setTab] = useState<Tabkey>('topics')
  const [pageNo, setPageNo] = useState(1)
  const info = useAsync(() => api.get<{ user: User; collectCount: number }>(`/api/user/${username}`), [username])
  const list = useAsync(
    () => tab === 'settings' ? Promise.resolve(null) : api.get<any>(`/api/user/${username}/${tab}?pageNo=${pageNo}`),
    [username, tab, pageNo])
  const isMe = me?.username === username

  if (info.loading || info.error || !info.data) return <Status loading={info.loading} error={info.error} />
  const u = info.data.user
  const page = list.data?.[tab] as Page<any> | undefined

  return (
    <>
      <Paper sx={{ p: 3, mb: 2, display: 'flex', gap: 2 }}>
        <Avatar src={u.avatar} sx={{ width: 64, height: 64 }}>{u.username[0]}</Avatar>
        <Box>
          <Typography variant="h6">{u.username}</Typography>
          <Typography variant="body2" color="text.secondary">{u.bio || '这个人很懒，什么都没留下'}</Typography>
          <Typography variant="caption" color="text.secondary">积分 {u.score ?? 0} · 注册于 {fmtTime(u.inTime)} · 收藏 {info.data.collectCount}</Typography>
        </Box>
      </Paper>
      <Tabs value={tab} onChange={(_, v) => { setTab(v); setPageNo(1) }} sx={{ mb: 2 }}>
        <Tab value="topics" label="话题" /><Tab value="comments" label="评论" /><Tab value="collects" label="收藏" />
        {isMe && <Tab value="settings" label="设置" />}
      </Tabs>
      {tab === 'settings' ? <Settings user={u} /> : (
        <>
          <Status loading={list.loading} error={list.error} />
          {page && (tab === 'comments' ? (
            <Paper>{page.records.map((c: any) => (
              <Box key={c.id} sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
                <Typography component={RouterLink} to={`/topic/${c.topicId}`}>{c.title}</Typography>
                <Typography variant="body2" color="text.secondary" noWrap>{c.content}</Typography>
                <Typography variant="caption" color="text.secondary">{fmtTime(c.inTime)}</Typography>
              </Box>
            ))}{page.records.length === 0 && <Box sx={{ p: 2 }}>暂无内容</Box>}</Paper>
          ) : <TopicList page={page as Page<TopicItem>} onPage={setPageNo} />)}
        </>
      )}
    </>
  )
}

function Settings({ user }: { user: User }) {
  const [bio, setBio] = useState(user.bio || '')
  const [website, setWebsite] = useState(user.website || '')
  const [oldPassword, setOld] = useState('')
  const [newPassword, setNew] = useState('')
  const [msg, setMsg] = useState<{ t: 'success' | 'error'; s: string } | null>(null)
  const run = async (fn: () => Promise<unknown>, ok: string) => {
    try { await fn(); setMsg({ t: 'success', s: ok }) } catch (e: any) { setMsg({ t: 'error', s: e.message }) }
  }
  return (
    <Paper sx={{ p: 3 }}>
      <Stack spacing={2}>
        {msg && <Alert severity={msg.t}>{msg.s}</Alert>}
        <TextField label="个人网站" value={website} onChange={(e) => setWebsite(e.target.value)} />
        <TextField label="个人简介" multiline minRows={3} value={bio} onChange={(e) => setBio(e.target.value)} />
        <Button variant="contained" onClick={() => run(() => api.put('/api/settings', { bio, website, emailNotification: 'true' }), '已保存')}>保存资料</Button>
        <Typography variant="subtitle2" sx={{ pt: 2 }}>修改密码</Typography>
        <TextField label="旧密码" type="password" value={oldPassword} onChange={(e) => setOld(e.target.value)} />
        <TextField label="新密码" type="password" value={newPassword} onChange={(e) => setNew(e.target.value)} />
        <Button variant="outlined" onClick={() => run(() => api.put('/api/settings/updatePassword', { oldPassword, newPassword }), '密码已修改')}>修改密码</Button>
      </Stack>
    </Paper>
  )
}
