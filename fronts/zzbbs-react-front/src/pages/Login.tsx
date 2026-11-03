import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import { Alert, Box, Button, Link, Paper, Stack, TextField } from '@mui/material'
import { api, User } from '../api'
import { useAuth } from '../auth'
import { Captcha, Title } from '../components'

interface F { username: string; password: string; captcha: string }

export default function Login() {
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<F>()
  const { login } = useAuth()
  const nav = useNavigate()
  const [err, setErr] = useState('')
  const [k, setK] = useState(0)

  const onSubmit = handleSubmit(async (v) => {
    setErr('')
    try {
      const r = await api.post<{ user: User; token: string }>('/api/login', v)
      login(r.user, r.token)
      nav('/')
    } catch (e: any) {
      setErr(e.message)
      setK((x) => x + 1)
    }
  })

  return (
    <Paper sx={{ p: 3, maxWidth: 420, mx: 'auto' }}>
      <Title>登录</Title>
      <Box component="form" onSubmit={onSubmit}>
        <Stack spacing={2}>
          {err && <Alert severity="error">{err}</Alert>}
          <TextField label="用户名" {...register('username', { required: '请输入用户名' })}
            error={!!errors.username} helperText={errors.username?.message} />
          <TextField label="密码" type="password" {...register('password', { required: '请输入密码' })}
            error={!!errors.password} helperText={errors.password?.message} />
          <Stack direction="row" spacing={2} sx={{ alignItems: "flex-start" }}>
            <TextField label="验证码" sx={{ flex: 1 }} {...register('captcha', { required: '请输入验证码' })}
              error={!!errors.captcha} helperText={errors.captcha?.message} />
            <Captcha key={k} />
          </Stack>
          <Button type="submit" variant="contained" disabled={isSubmitting}>登录</Button>
          <Link component={RouterLink} to="/register" variant="body2">没有账号？去注册</Link>
        </Stack>
      </Box>
    </Paper>
  )
}
