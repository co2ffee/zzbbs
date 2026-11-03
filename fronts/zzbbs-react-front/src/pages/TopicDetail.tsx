import { useState } from 'react'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import {
  Alert, Avatar, Box, Button, Chip, Divider, IconButton, Paper, Stack, TextField, Typography,
} from '@mui/material'
import ThumbUpIcon from '@mui/icons-material/ThumbUp'
import { api, CommentNode, fmtTime, Tag, User } from '../api'
import { useAuth } from '../auth'
import { Markdown, Status, useAsync } from '../components'

interface Detail {
  topic: { id: number; title: string; content: string; style?: string; inTime: string; view: number; userId: number; upIds?: string; top?: boolean; good?: boolean }
  tags: Tag[]
  comments: CommentNode[]
  topicUser: User
  collects: unknown[]
  collect?: unknown
}

const count = (ids?: string) => (ids ? ids.split(',').filter(Boolean).length : 0)

function flatten(list: CommentNode[], out: CommentNode[] = []) {
  for (const c of list) {
    out.push(c)
    if (c.children) Object.values(c.children).forEach((arr) => flatten(arr, out))
  }
  return out
}

export default function TopicDetail() {
  const { id } = useParams()
  const { user } = useAuth()
  const nav = useNavigate()
  const { data, loading, error, setData } = useAsync(() => api.get<Detail>(`/api/topic/${id}`), [id])
  const [content, setContent] = useState('')
  const [reply, setReply] = useState<CommentNode | null>(null)
  const [msg, setMsg] = useState('')

  const reload = () => api.get<Detail>(`/api/topic/${id}`).then(setData)
  const guard = async (fn: () => Promise<unknown>) => {
    setMsg('')
    try { await fn() } catch (e: any) { setMsg(e.message) }
  }

  if (loading || error || !data) return <Status loading={loading} error={error} />
  const { topic, tags, comments, topicUser } = data
  const flat = flatten(comments)
  const mine = user && topic.userId === user.id

  return (
    <>
      <Paper sx={{ p: 3, mb: 2 }}>
        <Typography variant="h5">{topic.title}</Typography>
        <Stack direction="row" spacing={1} sx={{ alignItems: "center", my: 1 }}>
          <Avatar src={topicUser.avatar} sx={{ width: 24, height: 24 }} />
          <Typography component={RouterLink} to={`/user/${topicUser.username}`} variant="body2">{topicUser.username}</Typography>
          <Typography variant="caption" color="text.secondary">{fmtTime(topic.inTime)} · {topic.view} 浏览</Typography>
          {tags.map((t) => <Chip key={t.id} size="small" label={t.name} component={RouterLink} to={`/tag/${encodeURIComponent(t.name)}`} clickable />)}
        </Stack>
        <Divider sx={{ mb: 2 }} />
        <Markdown text={topic.content} style={topic.style} />
        <Stack direction="row" spacing={1} sx={{ mt: 2 }}>
          <Button size="small" startIcon={<ThumbUpIcon />} onClick={() => guard(async () => { if (!user) return nav('/login'); await api.get(`/api/topic/${id}/vote`); await reload() })}>
            赞 {count(topic.upIds)}
          </Button>
          <Button size="small" onClick={() => guard(async () => {
            if (!user) return nav('/login')
            data.collect ? await api.del(`/api/collect/${id}`) : await api.post(`/api/collect/${id}`)
            await reload()
          })}>{data.collect ? '取消收藏' : '收藏'} ({data.collects.length})</Button>
          {mine && <>
            <Button size="small" component={RouterLink} to={`/topic/${id}/edit`}>编辑</Button>
            <Button size="small" color="error" onClick={() => confirm('确定删除该话题？') && guard(async () => { await api.del(`/api/topic/${id}`); nav('/') })}>删除</Button>
          </>}
        </Stack>
      </Paper>

      {msg && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setMsg('')}>{msg}</Alert>}

      <Paper sx={{ p: 2, mb: 2 }}>
        <Typography variant="subtitle1" sx={{ mb: 1 }}>{flat.length} 条评论</Typography>
        {flat.map((c) => (
          <Box key={c.id} sx={{ display: 'flex', gap: 1.5, py: 1.5, pl: Math.min(c.layer ?? 0, 4) * 3, borderTop: 1, borderColor: 'divider' }}>
            <Avatar src={c.avatar} sx={{ width: 32, height: 32 }}>{c.username?.[0]}</Avatar>
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }} component={RouterLink} to={`/user/${c.username}`}>{c.username}</Typography>
              <Typography variant="caption" color="text.secondary" sx={{ ml: 1 }}>{fmtTime(c.inTime)}</Typography>
              <Markdown text={c.content} />
              <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
                <IconButton size="small" onClick={() => guard(async () => { if (!user) return nav('/login'); await api.get(`/api/comment/${c.id}/vote`); await reload() })}>
                  <ThumbUpIcon fontSize="inherit" />
                </IconButton>
                <Typography variant="caption">{count(c.upIds)}</Typography>
                {user && <Button size="small" onClick={() => setReply(c)}>回复</Button>}
                {user && user.id === c.userId && (
                  <Button size="small" color="error" onClick={() => confirm('确定删除评论？') && guard(async () => { await api.del(`/api/comment/${c.id}`); await reload() })}>删除</Button>
                )}
              </Stack>
            </Box>
          </Box>
        ))}
      </Paper>

      {user ? (
        <Paper sx={{ p: 2 }}>
          {reply && <Chip label={`回复 @${reply.username}`} onDelete={() => setReply(null)} sx={{ mb: 1 }} />}
          <TextField fullWidth multiline minRows={3} placeholder="写下你的评论（支持 Markdown）" value={content} onChange={(e) => setContent(e.target.value)} />
          <Button sx={{ mt: 1 }} variant="contained" disabled={!content.trim()}
            onClick={() => guard(async () => {
              await api.post('/api/comment', { content: reply ? `@${reply.username} ${content}` : content, topicId: id, commentId: reply?.id })
              setContent(''); setReply(null); await reload()
            })}>发表评论</Button>
        </Paper>
      ) : (
        <Alert severity="info">请 <RouterLink to="/login">登录</RouterLink> 后评论</Alert>
      )}
    </>
  )
}
