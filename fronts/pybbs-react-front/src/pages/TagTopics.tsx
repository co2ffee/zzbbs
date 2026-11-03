import { useParams, useSearchParams } from 'react-router-dom'
import { api, Page, Tag, TopicItem } from '../api'
import { Status, Title, TopicList, useAsync } from '../components'

export default function TagTopics() {
  const { name = '' } = useParams()
  const [sp, setSp] = useSearchParams()
  const pageNo = Number(sp.get('pageNo') || 1)
  const { data, loading, error } = useAsync(
    () => api.get<{ tag: Tag; page: Page<TopicItem> }>(`/api/tag/${encodeURIComponent(name)}?pageNo=${pageNo}`), [name, pageNo])
  return (
    <>
      <Title>标签：{name}</Title>
      <Status loading={loading} error={error} />
      {data && <TopicList page={data.page} onPage={(n) => setSp({ pageNo: String(n) })} />}
    </>
  )
}
