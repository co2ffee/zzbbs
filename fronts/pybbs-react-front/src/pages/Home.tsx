import { useSearchParams } from 'react-router-dom'
import { Tab, Tabs } from '@mui/material'
import { api, Page, TopicItem } from '../api'
import { Status, TopicList, useAsync } from '../components'

const TABS = [['all', '全部'], ['good', '精华'], ['newest', '最新'], ['noanswer', '无人问津']]

export default function Home() {
  const [sp, setSp] = useSearchParams()
  const tab = sp.get('tab') || 'all'
  const pageNo = Number(sp.get('pageNo') || 1)
  const { data, loading, error } = useAsync(
    () => api.get<Page<TopicItem>>(`/api/?tab=${tab}&pageNo=${pageNo}`), [tab, pageNo])
  return (
    <>
      <Tabs value={tab} onChange={(_, v) => setSp({ tab: v })} sx={{ mb: 2 }}>
        {TABS.map(([v, l]) => <Tab key={v} value={v} label={l} />)}
      </Tabs>
      <Status loading={loading} error={error} />
      {data && <TopicList page={data} onPage={(n) => setSp({ tab, pageNo: String(n) })} />}
    </>
  )
}
