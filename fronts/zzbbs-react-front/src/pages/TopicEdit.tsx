import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router-dom'
import { Alert, Autocomplete, Box, Button, Paper, Stack, TextField } from '@mui/material'
import { api, Tag } from '../api'
import { Markdown, Title } from '../components'

interface F { title: string; content: string }

export default function TopicEdit() {
  const { id } = useParams()
  const editing = !!id
  const nav = useNavigate()
  const { register, handleSubmit, reset, watch, formState: { errors, isSubmitting } } = useForm<F>({ defaultValues: { title: '', content: '' } })
  const [tags, setTags] = useState<string[]>([])
  const [allTags, setAllTags] = useState<string[]>([])
  const [err, setErr] = useState('')

  useEffect(() => {
    api.get<{ records: Tag[] }>('/api/tags').then((p) => setAllTags(p.records.map((t) => t.name))).catch(() => {})
    if (id) {
      api.get<{ topic: { title: string; content: string }; tags: Tag[] }>(`/api/topic/${id}`)
        .then((d) => { reset({ title: d.topic.title, content: d.topic.content }); setTags(d.tags.map((t) => t.name)) })
        .catch((e) => setErr(e.message))
    }
  }, [id, reset])

  const onSubmit = handleSubmit(async (v) => {
    setErr('')
    try {
      const body = editing ? v : { ...v, tag: tags.join(',') }
      const t = editing ? await api.put<{ id: number }>(`/api/topic/${id}`, body) : await api.post<{ id: number }>('/api/topic', body)
      nav(`/topic/${t.id}`)
    } catch (e: any) { setErr(e.message) }
  })

  return (
    <Paper sx={{ p: 3 }}>
      <Title>{editing ? '编辑话题' : '发布话题'}</Title>
      <Box component="form" onSubmit={onSubmit}>
        <Stack spacing={2}>
          {err && <Alert severity="error">{err}</Alert>}
          <TextField label="标题" {...register('title', { required: '请输入标题' })} error={!!errors.title} helperText={errors.title?.message} />
          <TextField label="内容（Markdown）" multiline minRows={10} {...register('content')} />
          {!editing && (
            <Autocomplete multiple freeSolo options={allTags} value={tags}
              onChange={(_, v) => setTags(v.slice(0, 5))}
              renderInput={(p) => <TextField {...p} label="标签（最多 5 个）" />} />
          )}
          {watch('content') && <Paper variant="outlined" sx={{ p: 2 }}><Markdown text={watch('content')} /></Paper>}
          <Button type="submit" variant="contained" disabled={isSubmitting}>{editing ? '保存' : '发布'}</Button>
        </Stack>
      </Box>
    </Paper>
  )
}
