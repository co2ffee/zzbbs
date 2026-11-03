import { Link as RouterLink } from 'react-router-dom'
import { Avatar, Box, Card, CardActionArea, CardContent, Typography } from '@mui/material'
import { api, Page, Tag } from '../api'
import { Status, Title, useAsync } from '../components'

export default function Tags() {
  const { data, loading, error } = useAsync(() => api.get<Page<Tag>>('/api/tags'), [])
  return (
    <>
      <Title>标签</Title>
      <Status loading={loading} error={error} />
      <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr", md: "1fr 1fr 1fr" } }}>
        {data?.records.map((t) => (
          <div key={t.id}>
            <Card><CardActionArea component={RouterLink} to={`/tag/${encodeURIComponent(t.name)}`}>
              <CardContent sx={{ display: 'flex', gap: 2 }}>
                <Avatar variant="rounded" src={t.icon}>{t.name[0]}</Avatar>
                <div>
                  <Typography sx={{ fontWeight: 600 }}>{t.name}</Typography>
                  <Typography variant="body2" color="text.secondary" noWrap>{t.description || '暂无描述'}</Typography>
                  <Typography variant="caption" color="text.secondary">{t.topicCount} 个话题</Typography>
                </div>
              </CardContent>
            </CardActionArea></Card>
          </div>
        ))}
      </Box>
    </>
  )
}
