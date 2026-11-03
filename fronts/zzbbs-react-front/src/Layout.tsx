import { useState } from 'react'
import { Link as RouterLink, Outlet, useNavigate } from 'react-router-dom'
import {
  AppBar, Avatar, Badge, Box, Button, Container, IconButton, Menu, MenuItem, Toolbar, Typography,
} from '@mui/material'
import NotificationsIcon from '@mui/icons-material/Notifications'
import { useAuth } from './auth'

export default function Layout() {
  const { user, notRead, logout } = useAuth()
  const nav = useNavigate()
  const [anchor, setAnchor] = useState<HTMLElement | null>(null)

  return (
    <>
      <AppBar position="sticky" elevation={1}>
        <Toolbar>
          <Typography variant="h6" component={RouterLink} to="/" sx={{ color: 'inherit', textDecoration: 'none', mr: 3 }}>
            ZZBBS
          </Typography>
          <Button color="inherit" component={RouterLink} to="/tags">标签</Button>
          <Box sx={{ flexGrow: 1 }} />
          {user ? (
            <>
              <Button color="inherit" component={RouterLink} to="/topic/create">发布话题</Button>
              <IconButton color="inherit" onClick={() => nav('/notifications')}>
                <Badge badgeContent={notRead} color="error"><NotificationsIcon /></Badge>
              </IconButton>
              <IconButton onClick={(e) => setAnchor(e.currentTarget)}>
                <Avatar src={user.avatar} sx={{ width: 32, height: 32 }}>{user.username[0]}</Avatar>
              </IconButton>
              <Menu anchorEl={anchor} open={!!anchor} onClose={() => setAnchor(null)}>
                <MenuItem onClick={() => { setAnchor(null); nav(`/user/${user.username}`) }}>个人主页</MenuItem>
                <MenuItem onClick={() => { setAnchor(null); logout(); nav('/') }}>退出登录</MenuItem>
              </Menu>
            </>
          ) : (
            <>
              <Button color="inherit" component={RouterLink} to="/login">登录</Button>
              <Button color="inherit" component={RouterLink} to="/register">注册</Button>
            </>
          )}
        </Toolbar>
      </AppBar>
      <Container maxWidth="md" sx={{ py: 3 }}>
        <Outlet />
      </Container>
    </>
  )
}
