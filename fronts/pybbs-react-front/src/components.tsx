import { ReactNode, useEffect, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import {
  Alert, Avatar, Box, Chip, CircularProgress, List, ListItem, ListItemAvatar, ListItemText, Pagination, Paper, Stack, Typography,
} from '@mui/material'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import { fmtTime, Page, TopicItem } from './api'

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

export function Markdown({ text, style }: { text: string; style?: string }) {
  // style=RICH 的历史内容是 HTML，MD 是 markdown，统一消毒后渲染
  const html = style === 'RICH' ? text : md.render(text || '')
  return <Box className="md" sx={{ '& img': { maxWidth: '100%' }, '& pre': { overflow: 'auto', p: 1, bgcolor: 'grey.100' } }}
    dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(html) }} />
}

// 通用异步加载
export function useAsync<T>(fn: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    let alive = true
    setLoading(true)
    setError('')
    fn().then((d) => alive && setData(d)).catch((e) => alive && setError(e.message)).finally(() => alive && setLoading(false))
    return () => { alive = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)
  return { data, error, loading, setData }
}

export function Status({ loading, error }: { loading: boolean; error: string }) {
  if (loading) return <Box sx={{ textAlign: 'center', py: 6 }}><CircularProgress /></Box>
  if (error) return <Alert severity="error">{error}</Alert>
  return null
}

export function TopicList({ page, onPage }: { page: Page<TopicItem>; onPage: (n: number) => void }) {
  const pages = Math.max(1, Math.ceil(page.total / page.size))
  return (
    <Paper>
      <List disablePadding>
        {page.records.map((t) => (
          <ListItem key={t.id} divider component={RouterLink} to={`/topic/${t.id}`} sx={{ color: 'inherit' }}>
            <ListItemAvatar><Avatar src={t.avatar}>{t.username?.[0]}</Avatar></ListItemAvatar>
            <ListItemText
              primary={<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
                {t.top && <Chip size="small" color="warning" label="置顶" />}
                {t.good && <Chip size="small" color="success" label="精华" />}
                <span>{t.title}</span>
              </Stack>}
              secondary={`${t.username} · ${fmtTime(t.inTime)} · ${t.commentCount} 评论 · ${t.view} 浏览`}
            />
          </ListItem>
        ))}
        {page.records.length === 0 && <ListItem><ListItemText secondary="暂无内容" /></ListItem>}
      </List>
      {pages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 2 }}>
          <Pagination count={pages} page={page.current} onChange={(_, n) => onPage(n)} />
        </Box>
      )}
    </Paper>
  )
}

export function Captcha({ onRefresh }: { onRefresh?: () => void }) {
  const [k, setK] = useState(Date.now())
  void onRefresh
  return <img src={`/captcha?${k}`} alt="captcha" style={{ height: 40, cursor: 'pointer' }} onClick={() => setK(Date.now())} title="点击刷新" />
}

export const Title = ({ children }: { children: ReactNode }) => <Typography variant="h5" sx={{ mb: 2 }}>{children}</Typography>
