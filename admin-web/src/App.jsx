import { useEffect, useMemo, useState } from 'react'
import { createPortal } from 'react-dom'
import {
  Button, FluentProvider, Input, Label, MessageBar, MessageBarBody, Skeleton, SkeletonItem, Textarea,
  Spinner, Tooltip, webLightTheme,
} from '@fluentui/react-components'
import {
  Add24Regular, Alert24Regular, ArrowClockwise20Regular, Building24Regular, CalendarClock24Regular,
  CheckmarkCircle24Regular, ClipboardTaskListLtr24Regular, DoorArrowRight20Regular,
  Fire24Regular, Navigation20Regular, People24Regular, Person24Regular, Print24Regular,
  ShieldCheckmark24Regular, TextFontSize20Regular, Warning24Regular,
} from '@fluentui/react-icons'
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip as ChartTooltip, XAxis, YAxis } from 'recharts'
import { api } from './api'

const statusText = {
  PENDING: '待巡检', IN_PROGRESS: '巡检中', COMPLETED: '已完成',
  OPEN: '待整改', RESOLVED: '已整改',
}
const cycleText = {
  DAILY: '每日', WEEKLY: '每周', MONTHLY: '每月',
  QUARTERLY: '每季度', SEMI_ANNUAL: '每半年', ANNUAL: '每年',
}
const pageTitle = {
  dashboard: '数据看板', facilities: '设施档案', qrcodes: '二维码标签', inspections: '巡检管理', records: '巡检记录', rectifications: '整改管理', maintenance: '维护保养记录', users: '用户管理', rules: '更新与保养规则', suggestions: '更新/报废建议', risks: '异常风险标记', items: '巡检项配置', audits: '审计日志',
}
const roleOptions = [
  { code: 'GUARD', name: '保安（现场巡检）' },
  { code: 'COLLECTOR', name: '数据采集员（设施建档）' },
  { code: 'ADMIN', name: '系统管理员' },
]
const roleText = { ADMIN: '系统管理员', GUARD: '保安', COLLECTOR: '数据采集员', VISITOR: '访客' }
function todayText() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function Login({ onLogin }) {
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function submit(event) {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      const data = await api.login(username.trim(), password)
      if (data.user?.roleCode !== 'ADMIN') throw new Error('当前账号不是管理员账号')
      localStorage.setItem('campus-fire-token', data.accessToken)
      onLogin(data.user)
    } catch (cause) {
      setError(cause.message)
    } finally {
      setSubmitting(false)
    }
  }

  return <main className="login-page">
    <section className="login-brand" aria-label="系统介绍">
      <div className="brand-mark"><ShieldCheckmark24Regular /></div>
      <div>
        <p className="system-label">校园安全管理</p>
        <h1>消防设施巡检管理系统</h1>
        <p>统一管理设施档案、巡检任务、现场留痕和异常整改。</p>
      </div>
      <div className="login-assurance">
        <ShieldCheckmark24Regular />
        <span>现场扫码、定位、实时拍照，多项信息联合留痕</span>
      </div>
    </section>
    <section className="login-panel">
      <form onSubmit={submit} className="login-form">
        <div><p className="system-label">管理员入口</p><h2>登录系统</h2></div>
        {error && <MessageBar intent="error"><MessageBarBody>{error}</MessageBarBody></MessageBar>}
        <div className="field"><Label htmlFor="username" required>账号</Label><Input id="username" size="large" value={username} onChange={(_, d) => setUsername(d.value)} contentBefore={<Person24Regular />} /></div>
        <div className="field"><Label htmlFor="password" required>密码</Label><Input id="password" size="large" type="password" value={password} onChange={(_, d) => setPassword(d.value)} /></div>
        <Button appearance="primary" size="large" type="submit" disabled={submitting}>{submitting ? <Spinner size="tiny" label="正在登录" /> : '登录'}</Button>
      </form>
    </section>
  </main>
}

function Metric({ icon, label, value, note, warning }) {
  return <article className={`metric ${warning ? 'metric-warning' : ''}`}>
    <div className="metric-icon">{icon}</div>
    <div><span>{label}</span><strong>{value ?? 0}</strong><small>{note}</small></div>
  </article>
}

function DataState() {
  return <div className="dashboard-loading" aria-label="正在加载数据">
    {Array.from({ length: 8 }).map((_, index) => <Skeleton key={index}><SkeletonItem /></Skeleton>)}
  </div>
}

function Empty({ children }) {
  return <div className="empty"><CheckmarkCircle24Regular /><span>{children}</span></div>
}

function Dashboard({ user, onLogout }) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [largeText, setLargeText] = useState(localStorage.getItem('campus-fire-large-text') === 'true')
  const [days, setDays] = useState(30)
  const [page, setPage] = useState('dashboard')

  async function load(silent = false) {
    if (silent !== true) setLoading(true)
    setError('')
    try { setData(await api.overview(days)) } catch (cause) { setError(cause.message) } finally { setLoading(false) }
  }
  useEffect(() => {
    if (page !== 'dashboard') return undefined
    load()
    const refresh = () => { if (document.visibilityState === 'visible') load(true) }
    const timer = window.setInterval(refresh, 15000)
    window.addEventListener('focus', refresh)
    document.addEventListener('visibilitychange', refresh)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener('focus', refresh)
      document.removeEventListener('visibilitychange', refresh)
    }
  }, [days, page])
  useEffect(() => {
    document.documentElement.classList.toggle('large-text', largeText)
    localStorage.setItem('campus-fire-large-text', String(largeText))
  }, [largeText])

  const taskMap = useMemo(() => Object.fromEntries((data?.taskSummary || []).map(item => [item.status, Number(item.count)])), [data])
  const rectMap = useMemo(() => Object.fromEntries((data?.rectificationSummary || []).map(item => [item.status, Number(item.count)])), [data])
  const trend = (data?.inspectionTrend || []).map(item => ({ date: String(item.date).slice(5), count: Number(item.completedCount) }))

  function logout() { localStorage.removeItem('campus-fire-token'); onLogout() }

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="sidebar-brand"><span><ShieldCheckmark24Regular /></span><div><strong>校园消防</strong><small>巡检管理系统</small></div></div>
      <nav aria-label="主要导航">
        <button className={page === 'dashboard' ? 'nav-active' : ''} onClick={() => setPage('dashboard')}><Navigation20Regular />数据看板</button>
        <button className={page === 'facilities' ? 'nav-active' : ''} onClick={() => setPage('facilities')}><Building24Regular />设施档案</button>
        <button className={page === 'qrcodes' ? 'nav-active' : ''} onClick={() => setPage('qrcodes')}><Print24Regular />二维码标签</button>
        <button className={page === 'inspections' ? 'nav-active' : ''} onClick={() => setPage('inspections')}><ClipboardTaskListLtr24Regular />巡检管理</button>
        <button className={page === 'items' ? 'nav-active' : ''} onClick={() => setPage('items')}><ClipboardTaskListLtr24Regular />巡检项配置</button>
        <button className={page === 'records' ? 'nav-active' : ''} onClick={() => setPage('records')}><CheckmarkCircle24Regular />巡检记录</button>
        <button className={page === 'rectifications' ? 'nav-active' : ''} onClick={() => setPage('rectifications')}><Alert24Regular />整改管理</button>
        <button className={page === 'maintenance' ? 'nav-active' : ''} onClick={() => setPage('maintenance')}><CalendarClock24Regular />维护保养记录</button>
        <button className={page === 'users' ? 'nav-active' : ''} onClick={() => setPage('users')}><People24Regular />用户管理</button>
        <button className={page === 'rules' ? 'nav-active' : ''} onClick={() => setPage('rules')}><CalendarClock24Regular />更新与保养规则</button>
        <button className={page === 'suggestions' ? 'nav-active' : ''} onClick={() => setPage('suggestions')}><Warning24Regular />更新/报废建议</button>
        <button className={page === 'risks' ? 'nav-active' : ''} onClick={() => setPage('risks')}><Alert24Regular />异常风险标记</button>
        <button className={page === 'audits' ? 'nav-active' : ''} onClick={() => setPage('audits')}><ClipboardTaskListLtr24Regular />审计日志</button>
      </nav>
      <div className="sidebar-user"><div className="avatar">{user.displayName?.slice(0, 1) || '管'}</div><div><strong>{user.displayName}</strong><small>系统管理员</small></div></div>
    </aside>
    <main className="dashboard">
      <header className="topbar">
        <div><p>校园消防安全</p><h1>{pageTitle[page] || '数据看板'}</h1></div>
        <div className="top-actions">
          <Tooltip content="切换大字号显示" relationship="label"><Button icon={<TextFontSize20Regular />} appearance={largeText ? 'primary' : 'subtle'} onClick={() => setLargeText(v => !v)} aria-pressed={largeText}>大字号</Button></Tooltip>
          <Button icon={<ArrowClockwise20Regular />} appearance="subtle" onClick={load}>刷新</Button>
          <Button icon={<DoorArrowRight20Regular />} appearance="subtle" onClick={logout}>退出</Button>
        </div>
      </header>
      {page === 'facilities' && <FacilityPage />}
      {page === 'qrcodes' && <QrCodePage />}
      {page === 'inspections' && <InspectionPage />}
      {page === 'records' && <InspectionRecordsPage />}
      {page === 'rectifications' && <RectificationPage />}
      {page === 'maintenance' && <MaintenancePage />}
      {page === 'users' && <UsersPage />}
      {page === 'rules' && <UpdateRulesPage />}
      {page === 'suggestions' && <SuggestionsPage />}
      {page === 'risks' && <RisksPage />}
      {page === 'items' && <InspectionItemsPage />}
      {page === 'audits' && <AuditLogsPage />}
      {page === 'dashboard' && error && <MessageBar intent="error"><MessageBarBody>{error}<Button appearance="transparent" onClick={load}>重新加载</Button></MessageBarBody></MessageBar>}
      {page === 'dashboard' && (loading ? <DataState /> : data && <>
        <section className="metrics" aria-label="核心指标">
          <Metric icon={<Fire24Regular />} label="设施总数" value={data.facilityTotal} note="已纳入系统管理" />
          <Metric icon={<ClipboardTaskListLtr24Regular />} label="待巡检" value={taskMap.PENDING} note="需要安排现场检查" />
          <Metric icon={<Warning24Regular />} label="逾期任务" value={data.overdueTaskCount} note="超过计划完成时间" warning={data.overdueTaskCount > 0} />
          <Metric icon={<Alert24Regular />} label="待整改" value={rectMap.OPEN} note="异常设施待闭环" warning={rectMap.OPEN > 0} />
        </section>
        <section className="dashboard-grid">
          <article className="panel trend-panel">
            <div className="panel-head"><div><h2>巡检完成趋势</h2><p>按正式提交记录统计</p></div><select value={days} onChange={event => setDays(Number(event.target.value))} aria-label="趋势统计周期"><option value="7">近7天</option><option value="30">近30天</option><option value="90">近90天</option></select></div>
            {trend.length ? <ResponsiveContainer width="100%" height={280}><LineChart data={trend} margin={{ top: 20, right: 12, left: -20, bottom: 0 }}><CartesianGrid stroke="#e7eaee" vertical={false} /><XAxis dataKey="date" tickLine={false} axisLine={false} /><YAxis allowDecimals={false} tickLine={false} axisLine={false} /><ChartTooltip /><Line type="monotone" dataKey="count" name="完成数量" stroke="#b42318" strokeWidth={3} dot={{ r: 4, fill: '#b42318' }} /></LineChart></ResponsiveContainer> : <Empty>暂无已完成的巡检记录</Empty>}
          </article>
          <article className="panel status-panel">
            <div className="panel-head"><div><h2>巡检任务</h2><p>当前任务执行状态</p></div></div>
            <div className="status-list">{['PENDING', 'IN_PROGRESS', 'COMPLETED'].map(status => <div key={status}><span className={`status-dot ${status.toLowerCase()}`}></span><span>{statusText[status]}</span><strong>{taskMap[status] || 0}</strong></div>)}</div>
          </article>
          <article className="panel facility-panel">
            <div className="panel-head"><div><h2>设施类型分布</h2><p>各类消防设施在用情况</p></div></div>
            {(data.facilitySummary || []).length ? <div className="facility-list">{data.facilitySummary.map(item => <div key={item.facilityType}><div><strong>{item.facilityTypeName}</strong><span>{item.inUseCount || 0} 台在用</span></div><b>{item.totalCount || 0}</b></div>)}</div> : <Empty>暂无设施档案</Empty>}
          </article>
          <article className="panel facility-panel">
            <div className="panel-head"><div><h2>异常部件统计</h2><p>近{days}天巡检记录中异常最多的部件</p></div><Warning24Regular /></div>
            {(data.componentIssueSummary || []).length ? <div className="facility-list">{data.componentIssueSummary.map(item => <div key={`${item.itemCode}:${item.itemName}`}><div><strong>{item.itemName}</strong><span>异常 {item.failCount} 次</span></div><b>{item.failCount}</b></div>)}</div> : <Empty>该时段内没有异常部件记录</Empty>}
          </article>
          <article className="panel due-panel">
            <div className="panel-head"><div><h2>保养到期提醒</h2><p>未来30天内需要维护的设施</p></div><CalendarClock24Regular /></div>
            {(data.maintenanceDueFacilities || []).length ? <div className="table-wrap"><table><thead><tr><th>设施</th><th>位置</th><th>保养日期</th></tr></thead><tbody>{data.maintenanceDueFacilities.map(item => <tr key={item.id}><td><strong>{item.name}</strong><small>{item.facility_no}</small></td><td>{[item.campus, item.building, item.floor].filter(Boolean).join(' / ')}</td><td>{String(item.next_maintenance_at).slice(0, 10)}</td></tr>)}</tbody></table></div> : <Empty>未来30天没有设施需要保养</Empty>}
          </article>
        </section>
      </>)}
    </main>
  </div>
}

const QR_BRAND_TITLE = '湖南科技职业学院'
const QR_BRAND_SUBTITLE = '智慧消防巡检管理平台'
const QR_DEV_CREDIT = '软件学院 2024级软件技术3班 开发团队'

function QrCodePage() {
  const [items, setItems] = useState([])
  const [status, setStatus] = useState('')
  const [keyword, setKeyword] = useState('')
  const [appliedKeyword, setAppliedKeyword] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [busy, setBusy] = useState(false)
  const [school, setSchool] = useState(QR_BRAND_TITLE)
  const [campus, setCampus] = useState('')
  const [building, setBuilding] = useState('')
  const [floors, setFloors] = useState([{ floor: '', labelsText: '' }])
  const [rebindItem, setRebindItem] = useState(null)
  const [rebindFacilityId, setRebindFacilityId] = useState('')
  const [renameItem, setRenameItem] = useState(null)
  const [renameValue, setRenameValue] = useState('')
  const [facilityOptions, setFacilityOptions] = useState([])
  const [selectedIds, setSelectedIds] = useState([])

  async function load() {
    setLoading(true); setError('')
    try {
      const normalizedKeyword = keyword.trim()
      setItems(await api.qrCodes(status, normalizedKeyword))
      setAppliedKeyword(normalizedKeyword)
      setSelectedIds([])
    } catch (cause) { setError(cause.message) } finally { setLoading(false) }
  }
  useEffect(() => { load() }, [status])

  function setFloorRow(index, key, value) {
    setFloors(prev => prev.map((row, i) => i === index ? { ...row, [key]: value } : row))
  }

  function parseLabels(text) {
    return String(text || '').split(/[\n,，、;；]+/).map(value => value.trim()).filter(Boolean)
  }

  async function generate() {
    setError(''); setSuccess('')
    const groups = floors
      .map(row => ({ floor: row.floor.trim(), labels: parseLabels(row.labelsText) }))
      .filter(row => row.floor && row.labels.length)
    if (!school.trim() || !campus.trim() || !building.trim()) return setError('请填写学校、校区和楼栋')
    if (!groups.length) return setError('请至少为一个楼层填写楼层名称和二维码名称')
    if (groups.some(g => g.labels.length > 500)) return setError('每层最多设置 500 个二维码名称')
    if (groups.some(g => g.labels.some(label => label.length > 120))) return setError('二维码名称不能超过 120 个字符')
    if (groups.some(g => new Set(g.labels.map(label => label.toLocaleLowerCase())).size !== g.labels.length)) return setError('同一楼层的二维码名称不能重复')
    setBusy(true)
    try {
      const created = await api.createQrBatch({
        groups: groups.map(g => ({ school: school.trim(), campus: campus.trim(), building: building.trim(), floor: g.floor, labels: g.labels })),
      })
      const createdTokens = new Set(created.map(item => item.token))
      const createdIds = (await api.qrCodes('UNCLAIMED')).filter(item => createdTokens.has(item.token)).map(item => item.id)
      if (createdIds.length !== created.length) throw new Error('新生成二维码读取不完整，请刷新后重新下载')
      await api.downloadQrLabelsPdf('', createdIds)
      await load()
      setSuccess(`已生成 ${created.length} 个二维码，并下载 ${created.length} 页标签 PDF`)
    } catch (cause) { setError(cause.message) } finally { setBusy(false) }
  }

  function hierarchyParts(item) {
    const label = item.location_label || (item.location_no == null ? null : `第${String(item.location_no).padStart(2, '0')}号`)
    return [item.school, item.campus, item.building, item.floor, label].filter(Boolean)
  }

  async function openRebind(item) {
    setError(''); setSuccess(''); setBusy(true)
    try {
      const facilities = await api.facilities('')
      setFacilityOptions(facilities)
      setRebindFacilityId(item.facility_id ? String(item.facility_id) : '')
      setRebindItem(item)
    } catch (cause) { setError(cause.message) } finally { setBusy(false) }
  }

  async function submitRebind() {
    if (!rebindFacilityId) return setError('请选择需要绑定的设施')
    setBusy(true); setError(''); setSuccess('')
    try {
      await api.rebindQrCode(rebindItem.id, Number(rebindFacilityId))
      setRebindItem(null)
      setSuccess('二维码已重新绑定，原设施上的旧绑定已失效')
      await load()
    } catch (cause) { setError(cause.message) } finally { setBusy(false) }
  }

  async function deleteQr(item) {
    const hierarchy = hierarchyParts(item).join(' / ') || `NO.${String(item.serial_no).padStart(3, '0')}`
    const warning = item.status === 'BOUND'
      ? `该二维码已绑定设施。删除后原标签立即失效，相关设施需要重新绑定其他二维码。确定删除「${hierarchy}」吗？`
      : `确定删除二维码「${hierarchy}」吗？删除后可在“已删除”中恢复。`
    if (!window.confirm(warning)) return
    setBusy(true); setError(''); setSuccess('')
    try {
      await api.deleteQrCode(item.id)
      setSuccess('二维码已删除')
      await load()
    } catch (cause) { setError(cause.message) } finally { setBusy(false) }
  }

  async function batchDeleteQr() {
    if (!selectedIds.length) return
    if (!window.confirm(`确定删除已选择的 ${selectedIds.length} 个二维码吗？删除后可在“已删除”中恢复。`)) return
    setBusy(true); setError(''); setSuccess('')
    try {
      const result = await api.batchDeleteQrCodes(selectedIds)
      setSuccess(`已删除 ${result.deleted} 个二维码`)
      await load()
    } catch (cause) { setError(cause.message) } finally { setBusy(false) }
  }

  async function restoreQr(item) {
    setBusy(true); setError(''); setSuccess('')
    try {
      await api.restoreQrCode(item.id)
      setSuccess('二维码已恢复为未绑定状态')
      await load()
    } catch (cause) { setError(cause.message) } finally { setBusy(false) }
  }

  function toggleSelected(id) {
    setSelectedIds(prev => prev.includes(id) ? prev.filter(value => value !== id) : [...prev, id])
  }

  const selectableIds = items.filter(item => item.status !== 'DELETED').map(item => item.id)
  const allSelected = selectableIds.length > 0 && selectableIds.every(id => selectedIds.includes(id))

  async function downloadLabels() {
    setError('')
    setBusy(true)
    try {
      await api.downloadQrLabelsPdf(status, [], appliedKeyword)
    } catch (cause) { setError(cause.message) } finally { setBusy(false) }
  }

  function openRename(item) {
    setError(''); setSuccess('')
    setRenameItem(item)
    setRenameValue(item.location_label || '')
  }

  async function submitRename() {
    const label = renameValue.trim()
    if (!label) return setError('请输入二维码名称')
    if (label.length > 120) return setError('二维码名称不能超过 120 个字符')
    setBusy(true); setError(''); setSuccess('')
    try {
      await api.updateQrLabel(renameItem.id, label)
      setRenameItem(null)
      setRenameValue('')
      setSuccess(`二维码名称已修改为“${label}”`)
      await load()
    } catch (cause) { setError(cause.message) } finally { setBusy(false) }
  }

  return <section className="work-page">
    <article className="panel" style={{ marginBottom: 16 }}>
      <div className="panel-head"><div><h2>分级生成二维码</h2><p>按“学校 → 校区 → 楼栋 → 楼层 → 自定义名称”生成，名称可填写东、西、东楼梯口等现场位置</p></div></div>
      <div className="qr-gen-form">
        <div className="qr-gen-head">
          <div className="field"><Label required>学校</Label><Input value={school} onChange={(_, d) => setSchool(d.value)} placeholder="例如 湖南科技职业学院" /></div>
          <div className="field"><Label required>校区</Label><Input value={campus} onChange={(_, d) => setCampus(d.value)} placeholder="例如 主校区" /></div>
          <div className="field"><Label required>楼栋</Label><Input value={building} onChange={(_, d) => setBuilding(d.value)} placeholder="例如 1号教学楼" /></div>
        </div>
        <div className="qr-gen-rows">
          {floors.map((row, index) => <div key={index} className="qr-gen-row">
            <div className="field"><Label required>楼层</Label><Input value={row.floor} onChange={(_, d) => setFloorRow(index, 'floor', d.value)} placeholder="例如 2层" /></div>
            <div className="field qr-label-names-field"><Label required>二维码名称</Label><Textarea value={row.labelsText} onChange={(_, d) => setFloorRow(index, 'labelsText', d.value)} placeholder="例如 东、东楼梯口、西、西楼梯口、中" resize="vertical" /><span className="field-hint">多个名称用逗号或换行分隔，已识别 {parseLabels(row.labelsText).length} 个</span></div>
            <Button appearance="subtle" disabled={floors.length === 1} onClick={() => setFloors(prev => prev.filter((_, i) => i !== index))}>删除</Button>
          </div>)}
        </div>
        <div className="qr-gen-actions">
          <Button appearance="secondary" icon={<Add24Regular />} onClick={() => setFloors(prev => [...prev, { floor: '', labelsText: '' }])}>添加楼层</Button>
          <Button appearance="primary" disabled={busy} onClick={generate}>{busy ? '正在处理…' : '生成并下载标签 PDF'}</Button>
        </div>
      </div>
      {success && <MessageBar intent="success" style={{ marginTop: 12 }}><MessageBarBody>{success}</MessageBarBody></MessageBar>}
    </article>
    <div className="work-toolbar">
      <Input size="large" value={keyword} onChange={(_, d) => setKeyword(d.value)} placeholder="输入二维码名称、位置、码值或绑定设施" style={{ maxWidth: 320 }} />
      <Button appearance="primary" onClick={() => load()}>查询</Button>
      <Button icon={<Print24Regular />} appearance="secondary" disabled={busy || loading || !items.length || status === 'DELETED'} onClick={downloadLabels}>下载二维码标签 PDF（当前筛选）</Button>
    </div>
    {error && <MessageBar intent="error"><MessageBarBody>{error}</MessageBarBody></MessageBar>}
    <article className="panel data-panel">
      <div className="chips-row">
        {[['', '全部'], ['UNCLAIMED', '未绑定'], ['BOUND', '已绑定'], ['REVOKED', '已作废'], ['DELETED', '已删除']].map(([value, label]) => (
          <button key={value} className={`chip ${status === value ? 'chip-active' : ''}`} onClick={() => setStatus(value)}>{label}</button>
        ))}
        {status !== 'DELETED' && <Button appearance="secondary" disabled={busy || !selectedIds.length} onClick={batchDeleteQr}>批量删除（{selectedIds.length}）</Button>}
      </div>
      {loading ? <Spinner label="正在读取二维码" /> : items.length ? <div className="table-wrap"><table>
        <thead><tr><th className="select-cell"><input type="checkbox" aria-label="选择当前筛选全部二维码" disabled={!selectableIds.length || status === 'DELETED'} checked={allSelected} onChange={() => setSelectedIds(allSelected ? [] : selectableIds)} /></th><th>系统序号</th><th>分级位置 / 自定义名称</th><th>码值</th><th>状态</th><th>绑定设施</th><th>时间</th><th>操作</th></tr></thead>
        <tbody>{items.map(item => <tr key={item.id}>
          <td className="select-cell"><input type="checkbox" aria-label={`选择 NO.${String(item.serial_no).padStart(3, '0')}`} disabled={item.status === 'DELETED'} checked={selectedIds.includes(item.id)} onChange={() => toggleSelected(item.id)} /></td>
          <td><strong>NO.{String(item.serial_no).padStart(3, '0')}</strong></td>
          <td>{hierarchyParts(item).length ? <span><strong>{hierarchyParts(item)[0]}</strong><small>{hierarchyParts(item).slice(1).join(' / ')}</small></span> : '—'}</td>
          <td><code className="token-code">{item.token}</code></td>
          <td><span className={`badge ${item.status === 'UNCLAIMED' ? 'warn' : item.status === 'BOUND' ? 'ok' : ''}`}>{item.status === 'UNCLAIMED' ? '未绑定' : item.status === 'BOUND' ? '已绑定' : item.status === 'DELETED' ? '已删除' : '已作废'}</span></td>
          <td>{item.facility_no ? <span><strong>{item.facility_name}</strong><small>{item.facility_no}</small></span> : '—'}</td>
          <td><span>{String(item.created_at).slice(0, 19).replace('T', ' ')}{item.deleted_at && <small>删除：{String(item.deleted_at).slice(0, 19).replace('T', ' ')}</small>}</span></td>
          <td><span className="row-actions">
            {item.status === 'DELETED' ? <Button size="small" appearance="primary" disabled={busy} onClick={() => restoreQr(item)}>恢复</Button> : <>
              <Button size="small" disabled={busy} onClick={() => openRename(item)}>修改名称</Button>
              <Button size="small" disabled={busy} onClick={() => openRebind(item)}>重新绑定</Button>
              <Button size="small" className="btn-danger" disabled={busy} onClick={() => deleteQr(item)}>删除</Button>
            </>}
          </span></td>
        </tr>)}</tbody>
      </table></div> : <Empty>还没有二维码，先在上方生成一批</Empty>}
    </article>
    <p className="muted-note">使用流程：填写楼层和各二维码的自定义名称 → 生成并打印标签 → 采集员扫码建档。已生成二维码可单独修改名称；重新绑定会保留二维码本身并转移到所选设施。</p>
    {rebindItem && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation">
      <div className="resolve-dialog" role="dialog" aria-modal="true" aria-labelledby="qr-rebind-title">
        <div><h2 id="qr-rebind-title">重新绑定二维码</h2><p>{hierarchyParts(rebindItem).join(' / ') || `NO.${String(rebindItem.serial_no).padStart(3, '0')}`}</p></div>
        <div className="field"><Label htmlFor="qr-rebind-facility" required>目标设施</Label>
          <select id="qr-rebind-facility" className="native-select" value={rebindFacilityId} onChange={event => setRebindFacilityId(event.target.value)}>
            <option value="">请选择设施</option>
            {facilityOptions.map(facility => <option key={facility.id} value={facility.id}>{facility.facilityNo} · {facility.name} · {[facility.campus, facility.building, facility.floor].filter(Boolean).join(' / ')}</option>)}
          </select>
        </div>
        <div className="dialog-actions">
          <Button appearance="secondary" disabled={busy} onClick={() => setRebindItem(null)}>取消</Button>
          <Button appearance="primary" disabled={busy || !rebindFacilityId} onClick={submitRebind}>{busy ? '正在绑定…' : '确认重新绑定'}</Button>
        </div>
      </div>
    </div></FluentProvider>, document.body)}
    {renameItem && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation">
      <div className="resolve-dialog" role="dialog" aria-modal="true" aria-labelledby="qr-rename-title">
        <div><h2 id="qr-rename-title">修改二维码名称</h2><p>{[renameItem.school, renameItem.campus, renameItem.building, renameItem.floor].filter(Boolean).join(' / ')}</p></div>
        <div className="field"><Label htmlFor="qr-label-name" required>自定义名称</Label><Input id="qr-label-name" value={renameValue} onChange={(_, d) => setRenameValue(d.value)} placeholder="例如 东楼梯口" maxLength={120} /></div>
        <div className="dialog-actions">
          <Button appearance="secondary" disabled={busy} onClick={() => { setRenameItem(null); setRenameValue('') }}>取消</Button>
          <Button appearance="primary" disabled={busy || !renameValue.trim()} onClick={submitRename}>{busy ? '正在保存…' : '保存名称'}</Button>
        </div>
      </div>
    </div></FluentProvider>, document.body)}
  </section>
}
function FacilityPage() {
  const [keyword, setKeyword] = useState('')
  const [items, setItems] = useState([])
  const [types, setTypes] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [qrBusy, setQrBusy] = useState(false)
  const [history, setHistory] = useState(null)
  const [lifecycle, setLifecycle] = useState(null)
  const [lifecycleNote, setLifecycleNote] = useState('')
  const [dialog, setDialog] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const emptyForm = { facilityNo: '', facilityType: '', name: '', campus: '', building: '', floor: '', area: '', detailLocation: '', latitude: '', longitude: '', brand: '', model: '', specification: '', manufactureDate: '', commissionedDate: '' }
  const [form, setForm] = useState(emptyForm)
  async function load(value = keyword) {
    setLoading(true); setError('')
    try { setItems(await api.facilities(value)) } catch (cause) { setError(cause.message) } finally { setLoading(false) }
  }
  useEffect(() => { load(''); api.facilityTypes().then(setTypes).catch(() => {}) }, [])
  function openCreate() {
    setForm({ ...emptyForm, facilityType: types[0]?.typeCode || '' })
    setDialog('new')
  }
  function openEdit(item) {
    setForm({
      facilityNo: item.facilityNo || '', facilityType: item.facilityType || '', name: item.name || '',
      campus: item.campus || '', building: item.building || '', floor: item.floor || '', area: item.area || '',
      detailLocation: item.detailLocation || '', latitude: item.latitude ?? '', longitude: item.longitude ?? '',
      brand: item.brand || '', model: item.model || '', specification: item.specification || '',
      manufactureDate: item.manufactureDate || '', commissionedDate: item.commissionedDate || '',
    })
    setDialog(item)
  }
  function payload() {
    return {
      facilityNo: form.facilityNo.trim(), facilityType: form.facilityType, name: form.name.trim(),
      campus: form.campus.trim(), building: form.building.trim(), floor: form.floor.trim(),
      area: form.area.trim(), detailLocation: form.detailLocation.trim(),
      latitude: form.latitude === '' ? null : Number(form.latitude), longitude: form.longitude === '' ? null : Number(form.longitude),
      brand: form.brand.trim() || null, model: form.model.trim() || null, specification: form.specification.trim() || null,
      manufactureDate: form.manufactureDate || null, commissionedDate: form.commissionedDate || null,
    }
  }
  async function submit() {
    if (!form.facilityNo.trim() || !form.facilityType || !form.name.trim() || !form.campus.trim() || !form.building.trim() || !form.floor.trim() || !form.area.trim() || !form.detailLocation.trim()) {
      return setError('请填写设施编号、类型、名称和完整位置信息')
    }
    setSubmitting(true); setError('')
    try {
      if (dialog === 'new') await api.createFacility(payload())
      else await api.updateFacility(dialog.id, payload())
      setDialog(null); load()
    } catch (cause) { setError(cause.message) } finally { setSubmitting(false) }
  }
  async function remove(item) {
    if (!window.confirm(`确定删除设施「${item.name}（${item.facilityNo}）」吗？已有巡检或维护历史的设施无法删除。`)) return
    try { await api.deleteFacility(item.id); load() } catch (cause) { setError(cause.message) }
  }
  async function openQrSheet() {
    if (!items.length) return
    setQrBusy(true); setError('')
    try {
      const ids = items.map(item => item.id)
      await api.downloadQrLabelsPdf('', ids)
    } catch (cause) {
      setError(cause.message)
    } finally { setQrBusy(false) }
  }
  async function exportFacilities() {
    const token = localStorage.getItem('campus-fire-token'); const response = await fetch(api.facilitiesExportUrl(), { headers: token ? { Authorization: `Bearer ${token}` } : {} }); if (!response.ok) return setError('设施台账导出失败'); const blob=await response.blob(); const url=URL.createObjectURL(blob); const link=document.createElement('a'); link.href=url; link.download='facilities.csv'; link.click(); URL.revokeObjectURL(url)
  }
  return <section className="work-page">
    <div className="work-toolbar">
      <Input size="large" value={keyword} onChange={(_, d) => setKeyword(d.value)} placeholder="输入设施编号、名称或位置" />
      <Button appearance="primary" onClick={() => load()}>查询</Button>
      <Button appearance="primary" icon={<Add24Regular />} disabled={!types.length} onClick={openCreate}>新增设施</Button>
      <Button appearance="secondary" onClick={exportFacilities}>导出设施台账</Button>
      <Button icon={<Print24Regular />} appearance="secondary" disabled={!items.length || qrBusy} onClick={openQrSheet}>
        {qrBusy ? '正在生成…' : `下载二维码标签 PDF（${items.length} 个）`}
      </Button>
    </div>
    {error && <MessageBar intent="error"><MessageBarBody>{error}</MessageBarBody></MessageBar>}
    <article className="panel data-panel">
      {loading ? <Spinner label="正在读取设施档案" /> : items.length ? <div className="table-wrap"><table><thead><tr><th>设施编号</th><th>设施名称</th><th>类型</th><th>安装位置</th><th>状态</th><th>预计更新</th><th>下次保养</th><th>操作</th></tr></thead><tbody>{items.map(item => <tr key={item.id}><td><strong>{item.facilityNo}</strong></td><td>{item.name}</td><td>{types.find(type => type.typeCode === item.facilityType)?.typeName || item.facilityType}</td><td>{[item.campus,item.building,item.floor,item.area,item.detailLocation].filter(Boolean).join(' / ')}</td><td><span className={`badge ${item.lifecycleStatus === 'IN_USE' ? 'ok' : ''}`}>{item.lifecycleStatus === 'IN_USE' ? '在用' : item.lifecycleStatus}</span></td><td>{item.expectedUpdateDate || '按规则确认'}</td><td>{item.nextMaintenanceAt ? String(item.nextMaintenanceAt).slice(0,10) : '未设置'}</td><td><span className="row-actions"><Button size="small" onClick={async()=>setHistory({facility:item,data:await api.facilityHistory(item.id)})}>历史</Button><Button size="small" onClick={()=>openEdit(item)}>编辑</Button>{item.lifecycleStatus === 'IN_USE' && <Button size="small" onClick={()=>setLifecycle(item)}>停用/报废</Button>}<Button size="small" className="btn-danger" onClick={()=>remove(item)}>删除</Button></span></td></tr>)}</tbody></table></div> : <Empty>没有找到符合条件的设施</Empty>}
    </article>
    {history && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation"><div className="form-dialog" role="dialog" aria-modal="true"><h2>{history.facility.name} 历史记录</h2><p>巡检 {history.data.inspections?.length || 0} 次，维护 {history.data.maintenance?.length || 0} 次，状态变更 {history.data.lifecycle?.length || 0} 次。</p><div className="table-wrap"><table><tbody>{(history.data.inspections || []).slice(0,8).map(row=><tr key={'i'+row.id}><td>巡检</td><td>{String(row.submitted_at).replace('T',' ').slice(0,16)}</td><td>{row.inspector || '—'}，照片 {row.photo_count || 0} 张</td></tr>)}{(history.data.maintenance || []).slice(0,8).map(row=><tr key={'m'+row.id}><td>维护</td><td>{String(row.maintenance_at).replace('T',' ').slice(0,16)}</td><td>{row.maintainer}：{row.result_note}</td></tr>)}</tbody></table></div><div className="dialog-actions"><Button appearance="primary" onClick={()=>setHistory(null)}>关闭</Button></div></div></div></FluentProvider>, document.body)}
    {lifecycle && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation"><div className="resolve-dialog" role="dialog" aria-modal="true"><h2>变更设施状态</h2><p>{lifecycle.name}</p><select className="native-select" defaultValue="SUSPENDED" id="life-event"><option value="SUSPENDED">暂停使用/维修</option><option value="RETIRED">停用</option><option value="SCRAPPED">报废</option><option value="RESTORED">恢复使用</option></select><Textarea value={lifecycleNote} onChange={(_,d)=>setLifecycleNote(d.value)} placeholder="请填写变更原因" /><div className="dialog-actions"><Button appearance="secondary" onClick={()=>setLifecycle(null)}>取消</Button><Button appearance="primary" onClick={async()=>{if(!lifecycleNote.trim())return setError('请填写状态变更原因'); await api.changeFacilityLifecycle(lifecycle.id,document.getElementById('life-event').value,lifecycleNote.trim()); setLifecycle(null);setLifecycleNote('');load()}}>确认变更</Button></div></div></div></FluentProvider>, document.body)}
    {dialog && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation"><div className="form-dialog" role="dialog" aria-modal="true" aria-labelledby="facility-dialog-title">
      <div><h2 id="facility-dialog-title">{dialog === 'new' ? '新增设施档案' : `编辑设施 ${dialog.facilityNo}`}</h2><p>带 * 为必填项，位置信息用于保安现场定位与扫码巡检。</p></div>
      <div className="form-grid">
        <div className="field"><Label required>设施编号</Label><Input value={form.facilityNo} disabled={dialog !== 'new'} onChange={(_, d) => setForm({ ...form, facilityNo: d.value })} placeholder="例如 HYD-001" /></div>
        <div className="field"><Label required>设施类型</Label>
          <select className="native-select" value={form.facilityType} onChange={event => setForm({ ...form, facilityType: event.target.value })}>
            {types.map(t => <option key={t.typeCode} value={t.typeCode}>{t.typeName}</option>)}
          </select>
        </div>
        <div className="field span2"><Label required>设施名称</Label><Input value={form.name} onChange={(_, d) => setForm({ ...form, name: d.value })} placeholder="例如 3号教学楼东灭火器" /></div>
        <div className="field"><Label required>校区</Label><Input value={form.campus} onChange={(_, d) => setForm({ ...form, campus: d.value })} /></div>
        <div className="field"><Label required>楼栋</Label><Input value={form.building} onChange={(_, d) => setForm({ ...form, building: d.value })} /></div>
        <div className="field"><Label required>楼层</Label><Input value={form.floor} onChange={(_, d) => setForm({ ...form, floor: d.value })} /></div>
        <div className="field"><Label required>区域</Label><Input value={form.area} onChange={(_, d) => setForm({ ...form, area: d.value })} /></div>
        <div className="field span2"><Label required>详细位置</Label><Input value={form.detailLocation} onChange={(_, d) => setForm({ ...form, detailLocation: d.value })} placeholder="例如 东门楼梯口第2根柱旁" /></div>
        <div className="field"><Label>品牌</Label><Input value={form.brand} onChange={(_, d) => setForm({ ...form, brand: d.value })} /></div>
        <div className="field"><Label>型号</Label><Input value={form.model} onChange={(_, d) => setForm({ ...form, model: d.value })} /></div>
        <div className="field span2"><Label>规格</Label><Input value={form.specification} onChange={(_, d) => setForm({ ...form, specification: d.value })} /></div>
        <div className="field"><Label>生产日期</Label><Input type="date" value={form.manufactureDate} onChange={(_, d) => setForm({ ...form, manufactureDate: d.value })} /></div>
        <div className="field"><Label>投用日期</Label><Input type="date" value={form.commissionedDate} onChange={(_, d) => setForm({ ...form, commissionedDate: d.value })} /></div>
        <div className="field"><Label>纬度</Label><Input value={form.latitude} onChange={(_, d) => setForm({ ...form, latitude: d.value })} placeholder="选填" /></div>
        <div className="field"><Label>经度</Label><Input value={form.longitude} onChange={(_, d) => setForm({ ...form, longitude: d.value })} placeholder="选填" /></div>
      </div>
      <div className="dialog-actions">
        <Button appearance="secondary" onClick={() => setDialog(null)}>取消</Button>
        <Button appearance="primary" disabled={submitting} onClick={submit}>{submitting ? '正在保存' : '保存'}</Button>
      </div>
    </div></div></FluentProvider>, document.body)}
  </section>
}

function InspectionRecordsPage() {
  const [keyword, setKeyword] = useState('')
  const [inspector, setInspector] = useState('')
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [voiding, setVoiding] = useState(null)
  const [voidReason, setVoidReason] = useState('')
  const [photoView, setPhotoView] = useState(null)
  async function load(value = keyword, inspectorName = inspector) {
    setLoading(true); setError('')
    try { setItems(await api.inspectionRecords(value.trim(), inspectorName.trim())) } catch (cause) { setError(cause.message) } finally { setLoading(false) }
  }
  useEffect(() => { load('') }, [])
  async function confirmVoid() {
    if (!voidReason.trim()) return setError('请填写作废原因')
    try {
      await api.voidInspectionRecord(voiding.id, voidReason.trim())
      setVoiding(null); setVoidReason(''); load()
    } catch (cause) { setError(cause.message) }
  }
  async function openPhotos(record) {
    setPhotoView({ record, photos: [], error: '' })
    try {
      const list = await api.inspectionRecordPhotos(record.id)
      const token = localStorage.getItem('campus-fire-token')
      const loaded = []
      for (const photo of list) {
        try {
          const response = await fetch(api.inspectionPhotoFileUrl(photo.photoId), { headers: token ? { Authorization: `Bearer ${token}` } : {} })
          if (!response.ok) continue
          loaded.push({ photoId: photo.photoId, url: URL.createObjectURL(await response.blob()) })
        } catch (_) {}
      }
      setPhotoView({ record, photos: loaded, error: loaded.length ? '' : '未找到现场照片文件' })
    } catch (cause) {
      setPhotoView({ record, photos: [], error: cause.message })
    }
  }
  function closePhotos() {
    if (photoView) photoView.photos.forEach(photo => URL.revokeObjectURL(photo.url))
    setPhotoView(null)
  }
  async function exportCsv() {
    const token = localStorage.getItem('campus-fire-token')
    const response = await fetch(api.inspectionRecordsExportUrl(), { headers: token ? { Authorization: `Bearer ${token}` } : {} })
    if (!response.ok) return setError('报表导出失败，请重新登录后重试')
    const blob = await response.blob(); const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = 'inspection-records.csv'; link.click(); URL.revokeObjectURL(url)
  }
  return <section className="work-page">
    <div className="work-toolbar">
      <Input size="large" value={keyword} onChange={(_, d) => setKeyword(d.value)} placeholder="输入设施编号查询" />
      <Input size="large" value={inspector} onChange={(_, d) => setInspector(d.value)} placeholder="输入巡检人员姓名筛选" />
      <Button appearance="primary" onClick={() => load()}>查询</Button>
      <Button appearance="secondary" onClick={exportCsv}>导出 CSV</Button>
    </div>
    {error && <MessageBar intent="error"><MessageBarBody>{error}<Button appearance="transparent" onClick={() => load()}>重新加载</Button></MessageBarBody></MessageBar>}
    <article className="panel data-panel">
      {loading ? <Spinner label="正在读取巡检记录" /> : items.length ? <div className="table-wrap"><table>
        <thead><tr><th>设施</th><th>位置</th><th>巡检人员</th><th>提交时间</th><th>照片</th><th>检查结果</th><th>备注</th><th>操作</th></tr></thead>
        <tbody>{items.map(item => {
          let results = Array.isArray(item.results) ? item.results : []
          if (!results.length) {
            try {
              const raw = typeof item.results_json === 'string' ? JSON.parse(item.results_json) : item.results_json
              if (raw && !Array.isArray(raw)) results = Object.entries(raw).map(([code, status]) => ({ code, name: code, status }))
            } catch (_) {}
          }
          const abnormal = results.filter(result => String(result.status).toUpperCase() === 'FAIL' || String(result.status).toUpperCase() === 'ABNORMAL')
          return <tr key={item.id} className={item.void_reason ? 'text-danger' : ''}>
            <td><strong>{item.name}</strong><small>{item.facility_no}</small></td>
            <td>{[item.campus, item.building, item.floor, item.area].filter(Boolean).join(' / ') || '—'}</td>
            <td>{item.inspector || '—'}</td>
            <td>{item.submitted_at ? String(item.submitted_at).replace('T', ' ').slice(0, 16) : '—'}</td>
            <td>{Number(item.photo_count) ? <span className="photo-link" onClick={() => openPhotos(item)}>{item.photo_count} 张 · 查看</span> : '0 张'}</td>
            <td><span className={`badge ${item.void_reason ? 'warn' : abnormal.length ? 'warn' : 'ok'}`}>{item.void_reason ? '已作废' : abnormal.length ? `异常 ${abnormal.length} 项` : '全部正常'}</span>
              {results.length > 0 && <div className="result-chips">{results.map(result => <span key={result.code} className={`result-chip ${String(result.status).toUpperCase() === 'FAIL' ? 'fail' : ''}`}>{String(result.status).toUpperCase() === 'FAIL' ? '✗' : '✓'} {result.name}</span>)}</div>}
            </td>
            <td className="issue-cell">{item.void_reason ? `作废原因：${item.void_reason}` : item.note || (abnormal.length ? `异常部件：${abnormal.map(result => result.name).join('、')}` : '—')}</td>
            <td>{item.void_reason ? '—' : <Button size="small" className="btn-danger" onClick={() => { setVoiding(item); setVoidReason('') }}>作废</Button>}</td>
          </tr>
        })}</tbody>
      </table></div> : <Empty>暂无正式巡检记录</Empty>}
    </article>
    {voiding && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation"><div className="resolve-dialog" role="dialog" aria-modal="true" aria-labelledby="void-title">
      <h2 id="void-title">作废巡检记录</h2>
      <p>{voiding.name}（{voiding.facility_no}），提交于 {String(voiding.submitted_at).replace('T', ' ').slice(0, 16)}。作废后将保留更正记录，不会物理删除。</p>
      <Textarea resize="vertical" value={voidReason} onChange={(_, d) => setVoidReason(d.value)} placeholder="请填写作废原因，例如：重复提交、内容录入错误" />
      <div className="dialog-actions"><Button appearance="secondary" onClick={() => setVoiding(null)}>取消</Button><Button appearance="primary" onClick={confirmVoid}>确认作废</Button></div>
    </div></div></FluentProvider>, document.body)}
    {photoView && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation" onClick={closePhotos}><div className="resolve-dialog photo-dialog" role="dialog" aria-modal="true" aria-labelledby="photo-title" onClick={event => event.stopPropagation()}>
      <h2 id="photo-title">现场照片 · {photoView.record.name}（{photoView.record.facility_no}）</h2>
      <p>巡检人：{photoView.record.inspector || '—'} · 提交于 {String(photoView.record.submitted_at || '').replace('T', ' ').slice(0, 16)}</p>
      {photoView.error && <MessageBar intent="warning"><MessageBarBody>{photoView.error}</MessageBarBody></MessageBar>}
      <div className="photo-grid">
        {photoView.photos.map(photo => <img key={photo.photoId} src={photo.url} alt="巡检现场照片" className="photo-preview" />)}
      </div>
      <div className="dialog-actions"><Button appearance="secondary" onClick={closePhotos}>关闭</Button></div>
    </div></div></FluentProvider>, document.body)}
  </section>
}

function InspectionPage() {
  const [tab, setTab] = useState('tasks')
  const [users, setUsers] = useState([])
  const [types, setTypes] = useState([])
  useEffect(() => {
    api.users().then(setUsers).catch(() => {})
    api.facilityTypes().then(setTypes).catch(() => {})
  }, [])
  const userMap = useMemo(() => Object.fromEntries(users.map(item => [Number(item.id), item.display_name])), [users])
  const typeMap = useMemo(() => Object.fromEntries(types.map(item => [item.typeCode, item.typeName])), [types])
  return <section className="work-page">
    <div className="tabbar" role="tablist" aria-label="巡检管理">
      <button role="tab" aria-selected={tab === 'tasks'} className={tab === 'tasks' ? 'tab-active' : ''} onClick={() => setTab('tasks')}>巡检任务</button>
      <button role="tab" aria-selected={tab === 'plans'} className={tab === 'plans' ? 'tab-active' : ''} onClick={() => setTab('plans')}>巡检计划</button>
    </div>
    {tab === 'tasks' ? <InspectionTaskPanel users={users} /> : <InspectionPlanPanel users={users} types={types} userMap={userMap} typeMap={typeMap} />}
  </section>
}

function InspectionTaskPanel({ users }) {
  const [status, setStatus] = useState('')
  const [keyword, setKeyword] = useState('')
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [adjusting, setAdjusting] = useState(null)
  const [assignee, setAssignee] = useState('')
  const [dueDate, setDueDate] = useState(todayText())
  const [submitting, setSubmitting] = useState(false)
  async function load(currentStatus = status, currentKeyword = keyword) {
    setLoading(true); setError('')
    try { setData(await api.inspectionTasks(currentStatus, currentKeyword.trim())) } catch (cause) { setError(cause.message) } finally { setLoading(false) }
  }
  useEffect(() => { load(status, keyword) }, [status])
  const summary = useMemo(() => Object.fromEntries((data?.summary || []).map(item => [item.status, Number(item.count)])), [data])
  const overdue = Number(data?.overdueCount) || 0
  const items = data?.tasks || []
  const guards = users.filter(item => item.roleCode === 'GUARD')
  async function saveAdjust() {
    if (!dueDate) return setError('请选择截止日期')
    setSubmitting(true); setError('')
    try {
      await api.updateInspectionTask(adjusting.id, { assignedUserId: assignee ? Number(assignee) : null, dueDate })
      setAdjusting(null); load()
    } catch (cause) { setError(cause.message) } finally { setSubmitting(false) }
  }
  async function cancelTask(task) {
    if (!window.confirm(`确定取消任务「${task.name}（${task.facility_no}）」吗？`)) return
    try { await api.deleteInspectionTask(task.id); load() } catch (cause) { setError(cause.message) }
  }
  return <>
    <div className="chips" aria-label="任务统计">
      <span className="chip">待巡检<b>{summary.PENDING || 0}</b></span>
      <span className="chip">巡检中<b>{summary.IN_PROGRESS || 0}</b></span>
      <span className="chip">已完成<b>{summary.COMPLETED || 0}</b></span>
      <span className={`chip ${overdue ? 'chip-danger' : ''}`}>逾期<b>{overdue}</b></span>
    </div>
    <div className="work-toolbar">
      <select value={status} onChange={event => setStatus(event.target.value)} aria-label="任务状态筛选">
        <option value="">全部状态</option>
        <option value="PENDING">待巡检</option>
        <option value="IN_PROGRESS">巡检中</option>
        <option value="COMPLETED">已完成</option>
      </select>
      <Input size="large" value={keyword} onChange={(_, d) => setKeyword(d.value)} placeholder="搜索设施编号、名称、楼宇或人员" />
      <Button appearance="primary" onClick={() => load()}>查询</Button>
    </div>
    {error && <MessageBar intent="error"><MessageBarBody>{error}<Button appearance="transparent" onClick={() => load()}>重新加载</Button></MessageBarBody></MessageBar>}
    <article className="panel data-panel">
      {loading ? <Spinner label="正在读取巡检任务" /> : items.length ? <div className="table-wrap"><table>
        <thead><tr><th>设施</th><th>类型</th><th>位置</th><th>所属计划</th><th>指派给</th><th>截止日期</th><th>状态</th><th>完成时间</th><th>操作</th></tr></thead>
        <tbody>{items.map(item => {
          const overdueRow = item.status !== 'COMPLETED' && item.due_date && String(item.due_date).slice(0, 10) < todayText()
          return <tr key={item.id}>
            <td><strong>{item.name}</strong><small>{item.facility_no}</small></td>
            <td>{item.facility_type_name || '—'}</td>
            <td>{[item.campus, item.building, item.floor, item.area].filter(Boolean).join(' / ')}</td>
            <td>{item.plan_name || '—'}</td>
            <td>{item.assignee_name || '未指派'}</td>
            <td className={overdueRow ? 'text-danger' : ''}>{String(item.due_date).slice(0, 10)}{overdueRow ? '（已逾期）' : ''}</td>
            <td><span className={`badge ${item.status === 'COMPLETED' ? 'ok' : item.status === 'IN_PROGRESS' ? 'info' : 'warn'}`}>{statusText[item.status] || item.status}</span></td>
            <td>{item.completed_at ? String(item.completed_at).replace('T', ' ').slice(0, 16) : '—'}</td>
            <td>{item.status === 'PENDING' ? <span className="row-actions"><Button size="small" onClick={() => { setAdjusting(item); setAssignee(item.assigned_user_id ? String(item.assigned_user_id) : ''); setDueDate(String(item.due_date).slice(0, 10)) }}>调整</Button><Button size="small" className="btn-danger" onClick={() => cancelTask(item)}>取消</Button></span> : '—'}</td>
          </tr>
        })}</tbody>
      </table></div> : <Empty>当前没有符合条件的巡检任务</Empty>}
    </article>
    {adjusting && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation"><div className="resolve-dialog" role="dialog" aria-modal="true" aria-labelledby="adjust-title">
      <h2 id="adjust-title">调整巡检任务</h2>
      <p>{adjusting.name}（{adjusting.facility_no}），当前状态：待巡检。可重新指派保安或修改截止日期。</p>
      <div className="field"><Label htmlFor="adjust-assignee">指派保安</Label>
        <select id="adjust-assignee" className="native-select" value={assignee} onChange={event => setAssignee(event.target.value)}>
          <option value="">不指定</option>
          {guards.map(item => <option key={item.id} value={item.id}>{item.display_name}（{item.username}）</option>)}
        </select>
      </div>
      <div className="field"><Label htmlFor="adjust-date" required>截止日期</Label><Input id="adjust-date" type="date" value={dueDate} onChange={(_, d) => setDueDate(d.value)} /></div>
      <div className="dialog-actions"><Button appearance="secondary" onClick={() => setAdjusting(null)}>取消</Button><Button appearance="primary" disabled={submitting} onClick={saveAdjust}>{submitting ? '正在保存' : '保存调整'}</Button></div>
    </div></div></FluentProvider>, document.body)}
  </>
}

function InspectionPlanPanel({ users, types, userMap, typeMap }) {
  const [plans, setPlans] = useState([])
  const [campuses, setCampuses] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState(null)
  const [generating, setGenerating] = useState(null)
  const [form, setForm] = useState({ planName: '', cycleType: 'WEEKLY', facilityType: '', campus: '', assignedUserId: '', startDate: todayText(), endDate: '', enabled: true })
  const [dueDate, setDueDate] = useState(todayText())
  const [submitting, setSubmitting] = useState(false)
  const guards = users.filter(item => item.roleCode === 'GUARD')
  async function load() {
    setLoading(true); setError('')
    try {
      setPlans(await api.inspectionPlans())
      const facilities = await api.facilities()
      setCampuses([...new Set((facilities || []).map(item => item.campus).filter(Boolean))].sort())
    } catch (cause) { setError(cause.message) } finally { setLoading(false) }
  }
  useEffect(() => { load() }, [])
  function openEdit(plan) {
    setForm({
      planName: plan.plan_name || '', cycleType: plan.cycle_type || 'WEEKLY',
      facilityType: plan.facility_type || '', campus: plan.campus || '',
      assignedUserId: plan.assigned_user_id ? String(plan.assigned_user_id) : '',
      startDate: String(plan.start_date).slice(0, 10), endDate: plan.end_date ? String(plan.end_date).slice(0, 10) : '',
      enabled: plan.enabled === 1,
    })
    setEditing(plan)
  }
  async function createPlan() {
    if (!form.planName.trim() || !form.startDate) return setError('请填写计划名称和开始日期')
    setSubmitting(true); setError('')
    try {
      await api.createPlan({
        planName: form.planName.trim(), cycleType: form.cycleType,
        facilityType: form.facilityType || null, campus: form.campus.trim() || null,
        assignedUserId: form.assignedUserId ? Number(form.assignedUserId) : null,
        startDate: form.startDate, endDate: form.endDate || null,
      })
      setCreating(false)
      setSuccess('巡检计划已创建，可点击"生成任务"为计划生成巡检任务')
      load()
    } catch (cause) { setError(cause.message) } finally { setSubmitting(false) }
  }
  async function saveEdit() {
    if (!form.planName.trim() || !form.startDate) return setError('请填写计划名称和开始日期')
    setSubmitting(true); setError('')
    try {
      await api.updatePlan(editing.id, {
        planName: form.planName.trim(), cycleType: form.cycleType,
        facilityType: form.facilityType || null, campus: form.campus.trim() || null,
        assignedUserId: form.assignedUserId ? Number(form.assignedUserId) : null,
        startDate: form.startDate, endDate: form.endDate || null,
        enabled: form.enabled,
      })
      setEditing(null); setSuccess('巡检计划已更新'); load()
    } catch (cause) { setError(cause.message) } finally { setSubmitting(false) }
  }
  async function removePlan(plan) {
    if (!window.confirm(`确定删除巡检计划「${plan.plan_name}」吗？删除后不再显示；已生成的巡检任务和巡检记录会保留。`)) return
    try { await api.deletePlan(plan.id); load() } catch (cause) { setError(cause.message) }
  }
  async function generate() {
    if (!dueDate) return setError('请选择任务截止日期')
    setSubmitting(true); setError('')
    try {
      const result = await api.generateTasks(generating, dueDate)
      setGenerating(null)
      const count = Number(result?.createdCount) || 0
      if (count > 0) {
        setSuccess(`已生成 ${count} 个巡检任务，同一设施同一天不会重复生成`)
      } else {
        const plan = plans.find(item => item.id === generating)
        setError(`没有生成任何巡检任务：没有符合该计划筛选条件的在用设施（设施类型：${plan?.facility_type ? (typeMap[plan.facility_type] || plan.facility_type) : '全部类型'}，校区：${plan?.campus || '全部校区'}）。请确认已有对应类型且在用的设施档案，且校区与设施档案一致。`)
      }
      load()
    } catch (cause) { setError(cause.message) } finally { setSubmitting(false) }
  }
  const planFields = (disabled) => <>
    <div className="field span2"><Label htmlFor="plan-name" required>计划名称</Label><Input id="plan-name" value={form.planName} onChange={(_, d) => setForm({ ...form, planName: d.value })} placeholder="例如：教学楼灭火器周检" /></div>
    <div className="field"><Label htmlFor="plan-cycle" required>巡检周期</Label>
      <select id="plan-cycle" className="native-select" value={form.cycleType} onChange={event => setForm({ ...form, cycleType: event.target.value })}>
        {Object.entries(cycleText).map(([value, text]) => <option key={value} value={value}>{text}</option>)}
      </select>
    </div>
    <div className="field"><Label htmlFor="plan-type">设施类型</Label>
      <select id="plan-type" className="native-select" value={form.facilityType} onChange={event => setForm({ ...form, facilityType: event.target.value })}>
        <option value="">全部类型</option>
        {types.map(item => <option key={item.typeCode} value={item.typeCode}>{item.typeName}</option>)}
      </select>
    </div>
    <div className="field"><Label htmlFor="plan-campus">校区</Label>
      <select id="plan-campus" className="native-select" value={form.campus} onChange={event => setForm({ ...form, campus: event.target.value })}>
        <option value="">全部校区</option>
        {campuses.map(item => <option key={item} value={item}>{item}</option>)}
      </select>
      {campuses.length === 0 && <small className="field-hint">还没有设施档案，建档后可按校区筛选</small>}
    </div>
    <div className="field"><Label htmlFor="plan-assignee">指派保安</Label>
      <select id="plan-assignee" className="native-select" value={form.assignedUserId} onChange={event => setForm({ ...form, assignedUserId: event.target.value })}>
        <option value="">不指定</option>
        {guards.map(item => <option key={item.id} value={item.id}>{item.display_name}（{item.username}）</option>)}
      </select>
    </div>
    <div className="field"><Label htmlFor="plan-start" required>开始日期</Label><Input id="plan-start" type="date" value={form.startDate} onChange={(_, d) => setForm({ ...form, startDate: d.value })} /></div>
    <div className="field"><Label htmlFor="plan-end">结束日期</Label><Input id="plan-end" type="date" value={form.endDate} onChange={(_, d) => setForm({ ...form, endDate: d.value })} /></div>
    {!disabled && <div className="field"><Label htmlFor="plan-enabled">计划状态</Label>
      <select id="plan-enabled" className="native-select" value={form.enabled ? '1' : '0'} onChange={event => setForm({ ...form, enabled: event.target.value === '1' })}>
        <option value="1">启用</option>
        <option value="0">停用</option>
      </select>
    </div>}
  </>
  return <>
    <div className="work-toolbar">
      <Button appearance="primary" icon={<Add24Regular />} onClick={() => { setForm({ planName: '', cycleType: 'WEEKLY', facilityType: '', campus: '', assignedUserId: '', startDate: todayText(), endDate: '', enabled: true }); setCreating(true) }}>新建计划</Button>
      {success && <MessageBar intent="success" className="toolbar-message"><MessageBarBody>{success}</MessageBarBody></MessageBar>}
    </div>
    {error && <MessageBar intent="error"><MessageBarBody>{error}</MessageBarBody></MessageBar>}
    <article className="panel data-panel">
      {loading ? <Spinner label="正在读取巡检计划" /> : plans.length ? <div className="table-wrap"><table>
        <thead><tr><th>计划名称</th><th>巡检周期</th><th>设施类型</th><th>校区</th><th>指派给</th><th>有效期</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>{plans.map(plan => <tr key={plan.id}>
          <td><strong>{plan.plan_name}</strong></td>
          <td>{cycleText[plan.cycle_type] || plan.cycle_type}</td>
          <td>{plan.facility_type ? (typeMap[plan.facility_type] || plan.facility_type) : '全部类型'}</td>
          <td>{plan.campus || '全部校区'}</td>
          <td>{plan.assigned_user_id ? (userMap[Number(plan.assigned_user_id)] || `用户 ${plan.assigned_user_id}`) : '未指派'}</td>
          <td>{String(plan.start_date).slice(0, 10)} ~ {plan.end_date ? String(plan.end_date).slice(0, 10) : '长期'}</td>
          <td><span className={`badge ${plan.enabled ? 'ok' : ''}`}>{plan.enabled ? '启用' : '停用'}</span></td>
          <td><span className="row-actions">{plan.enabled ? <Button size="small" appearance="primary" onClick={() => { setDueDate(todayText()); setGenerating(plan.id) }}>生成任务</Button> : null}<Button size="small" onClick={() => openEdit(plan)}>编辑</Button><Button size="small" className="btn-danger" onClick={() => removePlan(plan)}>删除</Button></span></td>
        </tr>)}</tbody>
      </table></div> : <Empty>还没有巡检计划，点击"新建计划"开始</Empty>}
    </article>
    {creating && <div className="dialog-backdrop" role="presentation"><div className="form-dialog" role="dialog" aria-modal="true" aria-labelledby="plan-title">
      <div><h2 id="plan-title">新建巡检计划</h2><p>计划用于按设施类型、校区批量生成巡检任务，可指定保安负责执行。</p></div>
      <div className="form-grid">{planFields(true)}</div>
      <div className="dialog-actions">
        <Button appearance="secondary" onClick={() => setCreating(false)}>取消</Button>
        <Button appearance="primary" disabled={submitting} onClick={createPlan}>{submitting ? '正在保存' : '创建计划'}</Button>
      </div>
    </div></div>}
    {editing && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation"><div className="form-dialog" role="dialog" aria-modal="true" aria-labelledby="plan-edit-title">
      <div><h2 id="plan-edit-title">编辑巡检计划</h2><p>修改计划信息，停用后不再生成新任务。</p></div>
      <div className="form-grid">{planFields(false)}</div>
      <div className="dialog-actions">
        <Button appearance="secondary" onClick={() => setEditing(null)}>取消</Button>
        <Button appearance="primary" disabled={submitting} onClick={saveEdit}>{submitting ? '正在保存' : '保存修改'}</Button>
      </div>
    </div></div></FluentProvider>, document.body)}
    {generating && <div className="dialog-backdrop" role="presentation"><div className="resolve-dialog" role="dialog" aria-modal="true" aria-labelledby="generate-title">
      <h2 id="generate-title">生成巡检任务</h2>
      <p>系统会为符合条件的在用设施各生成一个任务，同一设施在同一天不会重复生成。生成后保安可在小程序端看到并执行。</p>
      <div className="field"><Label htmlFor="generate-date" required>任务截止日期</Label><Input id="generate-date" type="date" value={dueDate} onChange={(_, d) => setDueDate(d.value)} /></div>
      <div className="dialog-actions"><Button appearance="secondary" onClick={() => setGenerating(null)}>取消</Button><Button appearance="primary" disabled={submitting} onClick={generate}>{submitting ? '正在生成' : '确认生成'}</Button></div>
    </div></div>}
  </>
}

function UsersPage() {
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [creating, setCreating] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [form, setForm] = useState({ username: '', displayName: '', password: '', phone: '', roleCode: 'GUARD' })
  const [editing, setEditing] = useState(null)
  const [editForm, setEditForm] = useState({ displayName: '', phone: '', roleCode: 'GUARD', enabled: true, password: '' })
  async function load() {
    setLoading(true); setError('')
    try { setItems(await api.users()) } catch (cause) { setError(cause.message) } finally { setLoading(false) }
  }
  useEffect(() => { load() }, [])
  async function create() {
    if (!form.username.trim() || !form.displayName.trim() || form.password.length < 8) {
      setError('请填写账号、姓名，密码至少8位'); return
    }
    setSubmitting(true); setError('')
    try {
      await api.createUser({
        username: form.username.trim(), displayName: form.displayName.trim(),
        password: form.password, phone: form.phone.trim() || null, roleCode: form.roleCode,
      })
      setCreating(false); setSuccess(`账号 ${form.username.trim()} 已创建，初始密码请在首次登录后提醒修改`)
      setForm({ username: '', displayName: '', password: '', phone: '', roleCode: 'GUARD' })
      load()
    } catch (cause) { setError(cause.message) } finally { setSubmitting(false) }
  }
  function openEdit(item) {
    setEditing(item)
    setEditForm({
      displayName: item.display_name || '', phone: item.phone || '',
      roleCode: item.roleCode || 'GUARD', enabled: item.enabled !== 0 && item.enabled !== false, password: '',
    })
  }
  async function saveEdit() {
    if (!editForm.displayName.trim()) return setError('请填写姓名')
    if (editForm.password && editForm.password.length < 8) return setError('重置密码至少8位，留空表示不修改')
    setSubmitting(true); setError('')
    try {
      await api.updateUser(editing.id, {
        displayName: editForm.displayName.trim(), roleCode: editForm.roleCode,
        phone: editForm.phone.trim() || null, enabled: editForm.enabled,
        password: editForm.password ? editForm.password : null,
      })
      setEditing(null); setSuccess(`账号 ${editing.username} 信息已更新`)
      load()
    } catch (cause) { setError(cause.message) } finally { setSubmitting(false) }
  }
  async function removeUser(item) {
    if (!window.confirm(`确定删除账号「${item.username}（${item.display_name}）」吗？已有业务记录的账号无法删除，请改用停用。`)) return
    try { await api.deleteUser(item.id); load() } catch (cause) { setError(cause.message) }
  }
  return <section className="work-page">
    <div className="work-toolbar">
      <Button appearance="primary" icon={<Add24Regular />} onClick={() => setCreating(true)}>新建账号</Button>
      {success && <MessageBar intent="success" className="toolbar-message"><MessageBarBody>{success}</MessageBarBody></MessageBar>}
    </div>
    {error && <MessageBar intent="error"><MessageBarBody>{error}</MessageBarBody></MessageBar>}
    <article className="panel data-panel">
      {loading ? <Spinner label="正在读取账号列表" /> : items.length ? <div className="table-wrap"><table>
        <thead><tr><th>账号</th><th>姓名</th><th>角色</th><th>联系电话</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>{items.map(item => <tr key={item.id}>
          <td><strong>{item.username}</strong></td>
          <td>{item.display_name}</td>
          <td>{roleText[item.roleCode] || item.roleName || item.roleCode}</td>
          <td>{item.phone || '—'}</td>
          <td><span className={`badge ${item.enabled ? 'ok' : ''}`}>{item.enabled ? '启用' : '停用'}</span></td>
          <td><span className="row-actions"><Button size="small" onClick={() => openEdit(item)}>编辑</Button><Button size="small" className="btn-danger" onClick={() => removeUser(item)}>删除</Button></span></td>
        </tr>)}</tbody>
      </table></div> : <Empty>还没有账号</Empty>}
    </article>
    {editing && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation"><div className="form-dialog" role="dialog" aria-modal="true" aria-labelledby="edit-user-title">
      <div><h2 id="edit-user-title">编辑账号</h2><p>修改账号 {editing.username} 的信息，重置密码留空表示保持不变。</p></div>
      <div className="form-grid">
        <div className="field"><Label>登录账号</Label><Input value={editing.username} disabled /></div>
        <div className="field"><Label htmlFor="edit-user-name" required>姓名</Label><Input id="edit-user-name" value={editForm.displayName} onChange={(_, d) => setEditForm({ ...editForm, displayName: d.value })} /></div>
        <div className="field"><Label htmlFor="edit-user-role" required>角色</Label>
          <select id="edit-user-role" className="native-select" value={editForm.roleCode} onChange={event => setEditForm({ ...editForm, roleCode: event.target.value })}>
            {roleOptions.map(role => <option key={role.code} value={role.code}>{role.name}</option>)}
          </select>
        </div>
        <div className="field"><Label htmlFor="edit-user-phone">联系电话</Label><Input id="edit-user-phone" value={editForm.phone} onChange={(_, d) => setEditForm({ ...editForm, phone: d.value })} placeholder="选填" /></div>
        <div className="field"><Label htmlFor="edit-user-enabled" required>账号状态</Label>
          <select id="edit-user-enabled" className="native-select" value={editForm.enabled ? '1' : '0'} onChange={event => setEditForm({ ...editForm, enabled: event.target.value === '1' })}>
            <option value="1">启用</option>
            <option value="0">停用</option>
          </select>
        </div>
        <div className="field"><Label htmlFor="edit-user-password">重置密码</Label><Input id="edit-user-password" type="password" value={editForm.password} onChange={(_, d) => setEditForm({ ...editForm, password: d.value })} placeholder="留空表示不修改，填写则至少8位" /></div>
      </div>
      <div className="dialog-actions">
        <Button appearance="secondary" onClick={() => setEditing(null)}>取消</Button>
        <Button appearance="primary" disabled={submitting} onClick={saveEdit}>{submitting ? '正在保存' : '保存修改'}</Button>
      </div>
    </div></div></FluentProvider>, document.body)}
    {creating && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation"><div className="form-dialog" role="dialog" aria-modal="true" aria-labelledby="user-title">
      <div><h2 id="user-title">新建账号</h2><p>为保安或数据采集员创建登录账号，账号创建后即可在小程序端登录使用。</p></div>
      <div className="form-grid">
        <div className="field"><Label htmlFor="user-username" required>登录账号</Label><Input id="user-username" value={form.username} onChange={(_, d) => setForm({ ...form, username: d.value })} placeholder="例如 zhangsan" /></div>
        <div className="field"><Label htmlFor="user-name" required>姓名</Label><Input id="user-name" value={form.displayName} onChange={(_, d) => setForm({ ...form, displayName: d.value })} placeholder="例如 张三" /></div>
        <div className="field"><Label htmlFor="user-password" required>初始密码（至少8位）</Label><Input id="user-password" type="password" value={form.password} onChange={(_, d) => setForm({ ...form, password: d.value })} placeholder="请设置初始密码" /></div>
        <div className="field"><Label htmlFor="user-role" required>角色</Label>
          <select id="user-role" className="native-select" value={form.roleCode} onChange={event => setForm({ ...form, roleCode: event.target.value })}>
            {roleOptions.map(role => <option key={role.code} value={role.code}>{role.name}</option>)}
          </select>
        </div>
        <div className="field span2"><Label htmlFor="user-phone">联系电话</Label><Input id="user-phone" value={form.phone} onChange={(_, d) => setForm({ ...form, phone: d.value })} placeholder="选填" /></div>
      </div>
      <div className="dialog-actions">
        <Button appearance="secondary" onClick={() => setCreating(false)}>取消</Button>
        <Button appearance="primary" disabled={submitting} onClick={create}>{submitting ? '正在创建' : '创建账号'}</Button>
      </div>
    </div></div></FluentProvider>, document.body)}
  </section>
}

function UpdateRulesPage() {
  const [items, setItems] = useState([])
  const [types, setTypes] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const emptyForm = { ruleName: '', facilityType: '', serviceLifeYears: '', maintenanceCycleMonths: '', legalBasis: '', description: '', enabled: true }
  const [form, setForm] = useState(emptyForm)
  async function load() {
    setLoading(true); setError('')
    try { setItems(await api.facilityUpdateRules()) } catch (cause) { setError(cause.message) } finally { setLoading(false) }
  }
  useEffect(() => { load(); api.facilityTypes().then(setTypes).catch(() => {}) }, [])
  function openEdit(rule) {
    setForm({
      ruleName: rule.rule_name || '', facilityType: rule.facility_type || '',
      serviceLifeYears: rule.service_life_years ?? '', maintenanceCycleMonths: rule.maintenance_cycle_months ?? '',
      legalBasis: rule.legal_basis || '', description: rule.description || '',
      enabled: rule.enabled === 1,
    })
    setEditing(rule)
  }
  function payload() {
    return {
      ruleName: form.ruleName.trim(), facilityType: form.facilityType || null,
      serviceLifeYears: form.serviceLifeYears ? Number(form.serviceLifeYears) : null,
      maintenanceCycleMonths: form.maintenanceCycleMonths ? Number(form.maintenanceCycleMonths) : null,
      legalBasis: form.legalBasis.trim() || null, description: form.description.trim() || null,
    }
  }
  async function create() {
    if (!form.ruleName.trim()) return setError('请填写规则名称')
    setSubmitting(true); setError('')
    try {
      await api.createFacilityUpdateRule(payload())
      setCreating(false); setSuccess('更新与保养规则已保存'); setForm(emptyForm); load()
    } catch (cause) { setError(cause.message) } finally { setSubmitting(false) }
  }
  async function saveEdit() {
    if (!form.ruleName.trim()) return setError('请填写规则名称')
    setSubmitting(true); setError('')
    try {
      await api.updateFacilityUpdateRule(editing.id, { ...payload(), enabled: form.enabled })
      setEditing(null); setSuccess('规则已更新'); load()
    } catch (cause) { setError(cause.message) } finally { setSubmitting(false) }
  }
  async function remove(rule) {
    if (!window.confirm(`确定删除规则「${rule.rule_name}」吗？已被设施引用的规则无法删除，可先停用。`)) return
    try { await api.deleteFacilityUpdateRule(rule.id); load() } catch (cause) { setError(cause.message) }
  }
  const ruleFields = (isEdit) => <>
    <div className="field span2"><Label htmlFor="rule-name" required>规则名称</Label><Input id="rule-name" value={form.ruleName} onChange={(_, d) => setForm({ ...form, ruleName: d.value })} placeholder="例如：灭火器通用保养规则" /></div>
    <div className="field"><Label htmlFor="rule-type">设施类型</Label><select id="rule-type" className="native-select" value={form.facilityType} onChange={event => setForm({ ...form, facilityType: event.target.value })}><option value="">全部类型</option>{types.map(item => <option key={item.typeCode} value={item.typeCode}>{item.typeName}</option>)}</select></div>
    <div className="field"><Label htmlFor="rule-life">技术更新年限（年）</Label><Input id="rule-life" type="number" min="1" value={form.serviceLifeYears} onChange={(_, d) => setForm({ ...form, serviceLifeYears: d.value })} placeholder="如 10" /></div>
    <div className="field"><Label htmlFor="rule-cycle">保养周期（月）</Label><Input id="rule-cycle" type="number" min="1" value={form.maintenanceCycleMonths} onChange={(_, d) => setForm({ ...form, maintenanceCycleMonths: d.value })} placeholder="如 6" /></div>
    {isEdit && <div className="field"><Label htmlFor="rule-enabled">规则状态</Label>
      <select id="rule-enabled" className="native-select" value={form.enabled ? '1' : '0'} onChange={event => setForm({ ...form, enabled: event.target.value === '1' })}>
        <option value="1">启用</option><option value="0">停用</option>
      </select>
    </div>}
    <div className="field"><Label htmlFor="rule-legal">法规/标准依据</Label><Input id="rule-legal" value={form.legalBasis} onChange={(_, d) => setForm({ ...form, legalBasis: d.value })} placeholder="填写标准、厂家说明书或校内制度" /></div>
    <div className="field span2"><Label htmlFor="rule-description">补充说明</Label><Textarea id="rule-description" value={form.description} onChange={(_, d) => setForm({ ...form, description: d.value })} placeholder="说明适用条件和需要人工确认的事项" /></div>
  </>
  return <section className="work-page">
    <div className="work-toolbar"><Button appearance="primary" icon={<Add24Regular />} onClick={() => { setForm(emptyForm); setCreating(true) }}>新增规则</Button>{success && <MessageBar intent="success" className="toolbar-message"><MessageBarBody>{success}</MessageBarBody></MessageBar>}</div>
    {error && <MessageBar intent="error"><MessageBarBody>{error}<Button appearance="transparent" onClick={load}>重新加载</Button></MessageBarBody></MessageBar>}
    <article className="panel data-panel">
      {loading ? <Spinner label="正在读取更新与保养规则" /> : items.length ? <div className="table-wrap"><table>
        <thead><tr><th>规则名称</th><th>设施类型</th><th>技术更新年限</th><th>保养周期</th><th>法规依据</th><th>说明</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>{items.map(item => <tr key={item.id}>
          <td><strong>{item.rule_name}</strong></td>
          <td>{item.facility_type_name || '全部类型'}</td>
          <td>{item.service_life_years ? `${item.service_life_years} 年` : '按厂家/标准确认'}</td>
          <td>{item.maintenance_cycle_months ? `${item.maintenance_cycle_months} 个月` : '未设置'}</td>
          <td className="issue-cell">{item.legal_basis || '未填写'}</td>
          <td className="issue-cell">{item.description || '未填写'}</td>
          <td><span className={`badge ${item.enabled ? 'ok' : ''}`}>{item.enabled ? '启用' : '停用'}</span></td>
          <td><span className="row-actions"><Button size="small" onClick={() => openEdit(item)}>编辑</Button><Button size="small" className="btn-danger" onClick={() => remove(item)}>删除</Button></span></td>
        </tr>)}</tbody>
      </table></div> : <Empty>暂无规则，请先新增一条</Empty>}
    </article>
    {creating && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation"><div className="form-dialog" role="dialog" aria-modal="true" aria-labelledby="rule-title"><div><h2 id="rule-title">新增更新与保养规则</h2><p>年限和周期仅作为管理提醒，最终以现行法规、厂家说明书及学校确认结果为准。</p></div><div className="form-grid">{ruleFields(false)}</div><div className="dialog-actions"><Button appearance="secondary" onClick={() => setCreating(false)}>取消</Button><Button appearance="primary" disabled={submitting} onClick={create}>{submitting ? '正在保存' : '保存规则'}</Button></div></div></div></FluentProvider>, document.body)}
    {editing && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation"><div className="form-dialog" role="dialog" aria-modal="true" aria-labelledby="rule-edit-title"><div><h2 id="rule-edit-title">编辑更新与保养规则</h2><p>停用后不再作为新设施的默认规则。</p></div><div className="form-grid">{ruleFields(true)}</div><div className="dialog-actions"><Button appearance="secondary" onClick={() => setEditing(null)}>取消</Button><Button appearance="primary" disabled={submitting} onClick={saveEdit}>{submitting ? '正在保存' : '保存修改'}</Button></div></div></div></FluentProvider>, document.body)}
  </section>
}

function RectificationPage() {
  const [status, setStatus] = useState('OPEN')
  const [items, setItems] = useState([])
  const [facilities, setFacilities] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [resolving, setResolving] = useState(null)
  const [note, setNote] = useState(''), [evidence, setEvidence] = useState('')
  const [dialog, setDialog] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const emptyForm = { facilityId: '', issueSummary: '', dueDate: '' }
  const [form, setForm] = useState(emptyForm)
  async function load(value = status) {
    setLoading(true); setError('')
    try { setItems(await api.rectificationList(value)) } catch (cause) { setError(cause.message) } finally { setLoading(false) }
  }
  useEffect(() => {
    load(status)
    api.facilities('').then(setFacilities).catch(() => {})
  }, [status])
  async function submit() {
    if (!form.facilityId || !form.issueSummary.trim()) return setError('请选择设施并填写异常内容')
    setSubmitting(true); setError('')
    try {
      if (dialog === 'new') await api.createRectification({ facilityId: Number(form.facilityId), issueSummary: form.issueSummary.trim(), dueDate: form.dueDate || null })
      else await api.updateRectification(dialog.id, { facilityId: Number(form.facilityId), issueSummary: form.issueSummary.trim(), dueDate: form.dueDate || null })
      setDialog(null); load()
    } catch (cause) { setError(cause.message) } finally { setSubmitting(false) }
  }
  async function remove(item) {
    if (!window.confirm(`确定删除「${item.name}」的整改单吗？仅待整改状态可以删除。`)) return
    try { await api.deleteRectification(item.id); load() } catch (cause) { setError(cause.message) }
  }
  async function resolve() {
    if (!note.trim()) return setError('请填写整改结果')
    try { await api.resolveRectification(resolving, note.trim()); if (evidence.trim()) await api.addRectificationEvidence(resolving, evidence.split(/\s+/).filter(Boolean)); setResolving(null); setNote(''); setEvidence(''); load() } catch (cause) { setError(cause.message) }
  }
  async function exportRectifications() { const token=localStorage.getItem('campus-fire-token'); const response=await fetch(api.rectificationsExportUrl(),{headers:token?{Authorization:`Bearer ${token}`}:{}}); if(!response.ok)return setError('整改报表导出失败'); const blob=await response.blob();const url=URL.createObjectURL(blob);const link=document.createElement('a');link.href=url;link.download='rectifications.csv';link.click();URL.revokeObjectURL(url) }
  return <section className="work-page">
    <div className="work-toolbar"><select value={status} onChange={event => setStatus(event.target.value)}><option value="OPEN">待整改</option><option value="RESOLVED">已整改</option><option value="">全部</option></select><Button icon={<Add24Regular />} appearance="primary" onClick={() => { setForm(emptyForm); setDialog('new') }}>新建整改单</Button><Button appearance="secondary" onClick={exportRectifications}>导出整改报表</Button></div>
    {error && <MessageBar intent="error"><MessageBarBody>{error}</MessageBarBody></MessageBar>}
    <article className="panel data-panel">
      {loading ? <Spinner label="正在读取整改单" /> : items.length ? <div className="table-wrap"><table><thead><tr><th>设施</th><th>位置</th><th>异常内容</th><th>整改期限</th><th>状态</th><th>操作</th></tr></thead><tbody>{items.map(item => <tr key={item.id}><td><strong>{item.name}</strong><small>{item.facility_no}</small></td><td>{[item.campus,item.building,item.floor,item.area].filter(Boolean).join(' / ')}</td><td className="issue-cell">{item.issue_summary}</td><td>{item.due_date || '未设置'}</td><td><span className={`badge ${item.status === 'RESOLVED' ? 'ok' : 'warn'}`}>{statusText[item.status] || item.status}</span></td><td><span className="row-actions">{item.status === 'OPEN' ? <><Button size="small" appearance="primary" onClick={() => setResolving(item.id)}>填写整改结果</Button><Button size="small" onClick={() => { setForm({ facilityId: String(item.facility_id), issueSummary: item.issue_summary || '', dueDate: item.due_date ? String(item.due_date).slice(0, 10) : '' }); setDialog(item) }}>编辑</Button><Button size="small" className="btn-danger" onClick={() => remove(item)}>删除</Button></> : item.resolution_note || '已完成'}</span></td></tr>)}</tbody></table></div> : <Empty>当前没有整改单</Empty>}
    </article>
    {dialog && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation"><div className="resolve-dialog" role="dialog" aria-modal="true" aria-labelledby="rect-title">
      <h2 id="rect-title">{dialog === 'new' ? '新建整改单' : '编辑整改单'}</h2>
      <p>{dialog === 'new' ? '用于登记线下发现的异常，提交后进入待整改流程。' : '仅待整改状态可以编辑。'}</p>
      <div className="field"><Label required>设施</Label>
        <select className="native-select" value={form.facilityId} disabled={dialog !== 'new'} onChange={event => setForm({ ...form, facilityId: event.target.value })}>
          <option value="">请选择设施</option>
          {facilities.map(f => <option key={f.id} value={f.id}>{f.facilityNo} {f.name}</option>)}
        </select>
      </div>
      <div className="field"><Label required>异常内容</Label><Textarea resize="vertical" value={form.issueSummary} onChange={(_, d) => setForm({ ...form, issueSummary: d.value })} placeholder="例如：灭火器压力表指针在红区" /></div>
      <div className="field"><Label>整改期限</Label><Input type="date" value={form.dueDate} onChange={(_, d) => setForm({ ...form, dueDate: d.value })} /></div>
      <div className="dialog-actions"><Button appearance="secondary" onClick={() => setDialog(null)}>取消</Button><Button appearance="primary" disabled={submitting} onClick={submit}>{submitting ? '正在保存' : '保存'}</Button></div>
    </div></div></FluentProvider>, document.body)}
    {resolving && <div className="dialog-backdrop" role="presentation"><div className="resolve-dialog" role="dialog" aria-modal="true" aria-labelledby="resolve-title"><h2 id="resolve-title">填写整改结果</h2><p>确认设施问题已经处理完毕后再提交，提交后将记录处理人员和时间。</p><Textarea resize="vertical" value={note} onChange={(_, d) => setNote(d.value)} placeholder="例如：已更换压力不足的灭火器，并重新粘贴检查标签。" /><Input value={evidence} onChange={(_,d)=>setEvidence(d.value)} placeholder="凭证链接（可填多个，空格分隔）" /><div className="dialog-actions"><Button appearance="secondary" onClick={() => setResolving(null)}>取消</Button><Button appearance="primary" onClick={resolve}>确认完成整改</Button></div></div></div>}
  </section>
}

function MaintenancePage() {
  const [facilities, setFacilities] = useState([]), [items, setItems] = useState([])
  const [facilityId, setFacilityId] = useState(''), [loading, setLoading] = useState(true)
  const [error, setError] = useState(''), [dialog, setDialog] = useState(null)
  const emptyForm = { facilityId: '', maintenanceType: 'ROUTINE', maintenanceAt: new Date().toISOString().slice(0, 16), maintainer: '', resultNote: '', nextMaintenanceAt: '' }
  const [form, setForm] = useState(emptyForm)
  async function load(id = facilityId) {
    setLoading(true)
    try {
      if (!facilities.length) setFacilities(await api.facilities(''))
      setItems(await api.maintenanceRecords(id || ''))
    } catch (e) { setError(e.message) } finally { setLoading(false) }
  }
  useEffect(() => { load('') }, [])
  function openEdit(record) {
    setForm({
      facilityId: String(record.facility_id), maintenanceType: record.maintenance_type || 'ROUTINE',
      maintenanceAt: String(record.maintenance_at).replace('T', ' ').replace(' ', 'T').slice(0, 16),
      maintainer: record.maintainer || '', resultNote: record.result_note || '',
      nextMaintenanceAt: record.next_maintenance_at ? String(record.next_maintenance_at).replace('T', ' ').replace(' ', 'T').slice(0, 16) : '',
    })
    setDialog(record)
  }
  async function submit() {
    if (!form.facilityId || !form.maintainer.trim() || !form.resultNote.trim()) return setError('请选择设施并填写维护单位和结果')
    const payload = {
      facilityId: Number(form.facilityId), maintenanceType: form.maintenanceType,
      maintenanceAt: form.maintenanceAt.replace('T', ' '), maintainer: form.maintainer.trim(),
      resultNote: form.resultNote.trim(),
      nextMaintenanceAt: form.nextMaintenanceAt ? form.nextMaintenanceAt.replace('T', ' ') : null,
    }
    try {
      if (dialog === 'new') await api.createMaintenanceRecord(payload)
      else await api.updateMaintenanceRecord(dialog.id, payload)
      setDialog(null); load(form.facilityId)
    } catch (e) { setError(e.message) }
  }
  async function remove(record) {
    if (!window.confirm(`确定删除「${record.name}」的维护记录吗？`)) return
    try { await api.deleteMaintenanceRecord(record.id); load() } catch (e) { setError(e.message) }
  }
  const typeText = { ROUTINE: '日常保养', INSPECTION: '专业检测', REPAIR: '维修更换' }
  return <section className="work-page">
    <div className="work-toolbar">
      <select className="native-select" value={facilityId} onChange={e => { setFacilityId(e.target.value); load(e.target.value) }}>
        <option value="">全部设施</option>
        {facilities.map(f => <option key={f.id} value={f.id}>{f.facilityNo} {f.name}</option>)}
      </select>
      <Button appearance="primary" icon={<Add24Regular />} onClick={() => { setForm({ ...emptyForm, facilityId }); setDialog('new') }}>新增维护记录</Button>
    </div>
    {error && <MessageBar intent="error"><MessageBarBody>{error}</MessageBarBody></MessageBar>}
    <article className="panel data-panel">
      {loading ? <Spinner label="正在读取维护记录" /> : items.length ? <div className="table-wrap"><table>
        <thead><tr><th>设施</th><th>类型</th><th>维护单位</th><th>维护时间</th><th>结果</th><th>下次保养</th><th>操作</th></tr></thead>
        <tbody>{items.map(i => <tr key={i.id}>
          <td><strong>{i.name}</strong><small>{i.facility_no}</small></td>
          <td>{typeText[i.maintenance_type] || i.maintenance_type}</td>
          <td>{i.maintainer}</td>
          <td>{String(i.maintenance_at).replace('T', ' ').slice(0, 16)}</td>
          <td className="issue-cell">{i.result_note}</td>
          <td>{i.next_maintenance_at || '未设置'}</td>
          <td><span className="row-actions"><Button size="small" onClick={() => openEdit(i)}>编辑</Button><Button size="small" className="btn-danger" onClick={() => remove(i)}>删除</Button></span></td>
        </tr>)}</tbody>
      </table></div> : <Empty>暂无维护保养记录</Empty>}
    </article>
    {dialog && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation"><div className="form-dialog" role="dialog" aria-modal="true" aria-labelledby="maintenance-title">
      <div><h2 id="maintenance-title">{dialog === 'new' ? '新增维护保养记录' : '编辑维护保养记录'}</h2><p>填写维护信息，如设置了下次保养时间会同步更新设施档案。</p></div>
      <div className="form-grid">
        <div className="field span2"><Label required>设施</Label>
          <select className="native-select" value={form.facilityId} onChange={e => setForm({ ...form, facilityId: e.target.value })}>
            <option value="">请选择设施</option>
            {facilities.map(f => <option key={f.id} value={f.id}>{f.facilityNo} {f.name}</option>)}
          </select>
        </div>
        <div className="field"><Label>记录类型</Label>
          <select className="native-select" value={form.maintenanceType} onChange={e => setForm({ ...form, maintenanceType: e.target.value })}>
            <option value="ROUTINE">日常保养</option><option value="INSPECTION">专业检测</option><option value="REPAIR">维修更换</option>
          </select>
        </div>
        <div className="field"><Label>维护时间</Label><Input type="datetime-local" value={form.maintenanceAt} onChange={(_, d) => setForm({ ...form, maintenanceAt: d.value })} /></div>
        <div className="field"><Label required>维护单位/人员</Label><Input value={form.maintainer} onChange={(_, d) => setForm({ ...form, maintainer: d.value })} placeholder="例如 学校后勤维修组" /></div>
        <div className="field"><Label>下次保养时间</Label><Input type="datetime-local" value={form.nextMaintenanceAt} onChange={(_, d) => setForm({ ...form, nextMaintenanceAt: d.value })} /></div>
        <div className="field span2"><Label required>维护结果</Label><Textarea resize="vertical" value={form.resultNote} onChange={(_, d) => setForm({ ...form, resultNote: d.value })} placeholder="例如 已完成压力测试并更换密封圈" /></div>
      </div>
      <div className="dialog-actions">
        <Button appearance="secondary" onClick={() => setDialog(null)}>取消</Button>
        <Button appearance="primary" onClick={submit}>{dialog === 'new' ? '保存记录' : '保存修改'}</Button>
      </div>
    </div></div></FluentProvider>, document.body)}
  </section>
}

function SuggestionsPage() {
  const [items, setItems] = useState([]), [facilities, setFacilities] = useState([]), [loading, setLoading] = useState(true), [error, setError] = useState(''), [selected, setSelected] = useState(null), [note, setNote] = useState('')
  const [creating, setCreating] = useState(false), [form, setForm] = useState({ facilityId: '', suggestionType: 'UPDATE', reason: '' })
  async function load() { setLoading(true); try { setItems(await api.facilitySuggestions()) } catch (e) { setError(e.message) } finally { setLoading(false) } }
  useEffect(() => { load(); api.facilities('').then(setFacilities).catch(() => {}) }, [])
  async function create() {
    if (!form.facilityId || !form.reason.trim()) return setError('请选择设施并填写建议依据')
    try {
      await api.createFacilitySuggestion({ facilityId: Number(form.facilityId), suggestionType: form.suggestionType, reason: form.reason.trim() })
      setCreating(false); setForm({ facilityId: '', suggestionType: 'UPDATE', reason: '' }); load()
    } catch (e) { setError(e.message) }
  }
  async function remove(item) {
    if (!window.confirm(`确定删除「${item.name}」的建议吗？仅待处理状态可以删除。`)) return
    try { await api.deleteFacilitySuggestion(item.id); load() } catch (e) { setError(e.message) }
  }
  const typeText = { UPDATE: '技术更新', SCRAP: '报废', RETIRED: '停用' }
  return <section className="work-page">
    <div className="work-toolbar">
      <Button appearance="primary" icon={<Add24Regular />} onClick={() => { setForm({ facilityId: '', suggestionType: 'UPDATE', reason: '' }); setCreating(true) }}>新建建议</Button>
      <Button appearance="secondary" onClick={load}>刷新</Button>
    </div>
    {error && <MessageBar intent="error"><MessageBarBody>{error}</MessageBarBody></MessageBar>}
    <article className="panel data-panel">
      {loading ? <Spinner label="正在读取更新/报废建议" /> : items.length ? <div className="table-wrap"><table>
        <thead><tr><th>设施</th><th>建议类型</th><th>依据</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>{items.map(item => <tr key={item.id}>
          <td><strong>{item.name}</strong><small>{item.facility_no}</small></td>
          <td>{typeText[item.suggestion_type] || item.suggestion_type}</td>
          <td className="issue-cell">{item.reason}</td>
          <td><span className={`badge ${item.status === 'OPEN' ? 'warn' : 'ok'}`}>{item.status === 'OPEN' ? '待处理' : item.status}</span></td>
          <td><span className="row-actions">{item.status === 'OPEN' ? <><Button size="small" appearance="primary" onClick={() => { setSelected(item); setNote('') }}>处理建议</Button><Button size="small" className="btn-danger" onClick={() => remove(item)}>删除</Button></> : item.action_note || '已处理'}</span></td>
        </tr>)}</tbody>
      </table></div> : <Empty>暂无更新或报废建议</Empty>}
    </article>
    {creating && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation"><div className="resolve-dialog" role="dialog" aria-modal="true" aria-labelledby="suggest-create-title">
      <div><h2 id="suggest-create-title">新建更新/报废建议</h2><p>登记设施的更新或报废建议，处理后可同步变更设施状态。</p></div>
      <div className="field"><Label required>设施</Label>
        <select className="native-select" value={form.facilityId} onChange={e => setForm({ ...form, facilityId: e.target.value })}>
          <option value="">请选择设施</option>
          {facilities.map(f => <option key={f.id} value={f.id}>{f.facilityNo} {f.name}</option>)}
        </select>
      </div>
      <div className="field"><Label required>建议类型</Label>
        <select className="native-select" value={form.suggestionType} onChange={e => setForm({ ...form, suggestionType: e.target.value })}>
          <option value="UPDATE">技术更新</option><option value="RETIRED">停用</option><option value="SCRAP">报废</option>
        </select>
      </div>
      <div className="field"><Label required>建议依据</Label><Textarea resize="vertical" value={form.reason} onChange={(_, d) => setForm({ ...form, reason: d.value })} placeholder="例如：灭火器已过有效期，依据 GB50444 建议报废" /></div>
      <div className="dialog-actions"><Button appearance="secondary" onClick={() => setCreating(false)}>取消</Button><Button appearance="primary" onClick={create}>保存建议</Button></div>
    </div></div></FluentProvider>, document.body)}
    {selected && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation"><div className="resolve-dialog" role="dialog" aria-modal="true" aria-labelledby="suggest-title"><h2 id="suggest-title">处理设施建议</h2><p>{selected.name}（{selected.facility_no}）</p><select className="native-select" id="suggest-status" defaultValue="CONFIRMED"><option value="CONFIRMED">确认建议</option><option value="DEFERRED">延期观察</option><option value="INSPECTION_REQUIRED">安排专业送检</option><option value="RETIRED">停用</option><option value="SCRAPPED">报废</option></select><Textarea value={note} onChange={(_, d) => setNote(d.value)} placeholder="填写处理理由" /><div className="dialog-actions"><Button appearance="secondary" onClick={() => setSelected(null)}>取消</Button><Button appearance="primary" onClick={async () => { if (!note.trim()) return setError('请填写处理理由'); await api.actionFacilitySuggestion(selected.id, document.getElementById('suggest-status').value, note.trim()); setSelected(null); load() }}>确认处理</Button></div></div></div></FluentProvider>, document.body)}
  </section>
}

function RisksPage() {
  const [items, setItems] = useState([]), [loading, setLoading] = useState(true), [error, setError] = useState('')
  async function load() { setLoading(true); try { setItems(await api.inspectionRisks()) } catch (e) { setError(e.message) } finally { setLoading(false) } }
  useEffect(() => { load() }, [])
  async function remove(item) {
    if (!window.confirm('确定删除该风险标记吗？')) return
    try { await api.deleteInspectionRisk(item.id); load() } catch (e) { setError(e.message) }
  }
  return <section className="work-page">
    <div className="work-toolbar"><Button appearance="secondary" onClick={load}>刷新</Button></div>
    {error && <MessageBar intent="error"><MessageBarBody>{error}</MessageBarBody></MessageBar>}
    <article className="panel data-panel">
      {loading ? <Spinner label="正在读取异常风险标记" /> : items.length ? <div className="table-wrap"><table>
        <thead><tr><th>巡检人员</th><th>风险类型</th><th>证据</th><th>时间</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>{items.map(item => <tr key={item.id}>
          <td>{item.inspector}</td>
          <td>{item.risk_type}</td>
          <td className="issue-cell">{item.evidence}</td>
          <td>{String(item.created_at).replace('T', ' ').slice(0, 16)}</td>
          <td><span className={`badge ${item.status === 'OPEN' ? 'warn' : 'ok'}`}>{item.status === 'OPEN' ? '待复核' : item.status}</span></td>
          <td><span className="row-actions">{item.status === 'OPEN' ? <Button size="small" appearance="primary" onClick={async () => { await api.reviewInspectionRisk(item.id, 'REVIEWED'); load() }}>标记已复核</Button> : '已复核'}<Button size="small" className="btn-danger" onClick={() => remove(item)}>删除</Button></span></td>
        </tr>)}</tbody>
      </table></div> : <Empty>暂无异常风险标记</Empty>}
    </article>
  </section>
}

function AuditLogsPage() {
  const [items, setItems] = useState([])
  const [action, setAction] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  async function load(value = action) {
    setLoading(true); setError('')
    try { setItems(await api.auditLogs(value)) } catch (cause) { setError(cause.message) } finally { setLoading(false) }
  }
  useEffect(() => { load('') }, [])
  const actionOptions = [...new Set(items.map(item => item.action_code).filter(Boolean))]
  return <section className="work-page">
    <div className="work-toolbar">
      <select className="native-select" value={action} onChange={event => { setAction(event.target.value); load(event.target.value) }} aria-label="按操作类型筛选">
        <option value="">全部操作类型</option>
        {actionOptions.map(code => <option key={code} value={code}>{code}</option>)}
      </select>
      <Button appearance="secondary" onClick={() => load()}>刷新</Button>
    </div>
    {error && <MessageBar intent="error"><MessageBarBody>{error}<Button appearance="transparent" onClick={() => load()}>重新加载</Button></MessageBarBody></MessageBar>}
    <article className="panel data-panel">
      {loading ? <Spinner label="正在读取审计日志" /> : items.length ? <div className="table-wrap"><table><thead><tr><th>操作人</th><th>操作类型</th><th>目标</th><th>时间</th><th>详情</th></tr></thead><tbody>
        {items.map(item => <tr key={item.id}><td>{item.display_name || item.username || '系统'}</td><td><span className="badge">{item.action_code}</span></td><td>{[item.target_type, item.target_id].filter(Boolean).join(' / ') || '—'}</td><td>{String(item.created_at || '').replace('T', ' ').slice(0, 19)}</td><td className="issue-cell"><code>{item.detail_json || '—'}</code></td></tr>)}
      </tbody></table></div> : <Empty>暂无审计日志</Empty>}
    </article>
  </section>
}

function InspectionItemsPage() {
  const [types, setTypes] = useState([])
  const [typeCode, setTypeCode] = useState('')
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [dialog, setDialog] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [typeManager, setTypeManager] = useState(false)
  const [typeEditing, setTypeEditing] = useState(null)
  const [typeSubmitting, setTypeSubmitting] = useState(false)
  const emptyForm = { itemCode: '', itemName: '', inspectionStandard: '', requiredFlag: true, sortOrder: '0', enabled: true }
  const emptyTypeForm = { typeCode: '', typeName: '' }
  const [form, setForm] = useState(emptyForm)
  const [typeForm, setTypeForm] = useState(emptyTypeForm)
  async function load(code = typeCode) {
    if (!code) return
    setLoading(true); setError('')
    try { setItems(await api.inspectionItems(code)) } catch (cause) { setError(cause.message) } finally { setLoading(false) }
  }
  async function refreshTypes(preferredCode = typeCode) {
    const list = await api.managedFacilityTypes()
    setTypes(list)
    const nextCode = list.some(type => type.typeCode === preferredCode) ? preferredCode : (list[0]?.typeCode || '')
    setTypeCode(nextCode)
    if (nextCode) await load(nextCode)
    else setItems([])
  }
  useEffect(() => { refreshTypes('').catch(cause => setError(cause.message)) }, [])
  function openCreate() { setForm(emptyForm); setDialog('new') }
  function openEdit(item) {
    setForm({
      itemCode: item.item_code || '', itemName: item.item_name || '', inspectionStandard: item.inspection_standard || '',
      requiredFlag: item.required_flag === 1, sortOrder: String(item.sort_order ?? '0'),
      enabled: item.enabled === 1,
    })
    setDialog(item)
  }
  async function submit() {
    if (!form.itemCode.trim() || !form.itemName.trim() || !form.inspectionStandard.trim()) return setError('请填写检查项编号、部件名称和检查标准')
    setSubmitting(true); setError('')
    const payload = {
      facilityType: typeCode, itemCode: form.itemCode.trim(), itemName: form.itemName.trim(), inspectionStandard: form.inspectionStandard.trim(),
      requiredFlag: form.requiredFlag, sortOrder: form.sortOrder ? Number(form.sortOrder) : 0,
    }
    try {
      if (dialog === 'new') await api.createInspectionItem(payload)
      else await api.updateInspectionItem(dialog.id, { ...payload, enabled: form.enabled })
      setDialog(null); load()
    } catch (cause) { setError(cause.message) } finally { setSubmitting(false) }
  }
  async function remove(item) {
    if (!window.confirm(`确定删除检查项「${item.item_name}」吗？`)) return
    try { await api.deleteInspectionItem(item.id); load() } catch (cause) { setError(cause.message) }
  }
  function openTypeCreate() { setTypeEditing('new'); setTypeForm(emptyTypeForm) }
  function openTypeEdit(type) { setTypeEditing(type); setTypeForm({ typeCode: type.typeCode, typeName: type.typeName }) }
  async function submitType() {
    if (!typeForm.typeCode.trim() || !typeForm.typeName.trim()) return setError('请填写设施类型编号和名称')
    setTypeSubmitting(true); setError('')
    try {
      const saved = typeEditing === 'new'
        ? await api.createFacilityType(typeForm)
        : await api.updateFacilityType(typeEditing.id, typeForm)
      setTypeEditing(null); setTypeForm(emptyTypeForm)
      await refreshTypes(saved.typeCode)
    } catch (cause) { setError(cause.message) } finally { setTypeSubmitting(false) }
  }
  async function removeType(type) {
    if (!window.confirm(`确定删除设施类型「${type.typeName}」吗？有关联数据时系统会阻止删除。`)) return
    try { await api.deleteFacilityType(type.id); await refreshTypes(typeCode === type.typeCode ? '' : typeCode) } catch (cause) { setError(cause.message) }
  }
  return <section className="work-page">
    <div className="work-toolbar">
      <select className="native-select" value={typeCode} onChange={event => { setTypeCode(event.target.value); load(event.target.value) }} aria-label="设施类型筛选">
        {types.map(t => <option key={t.typeCode} value={t.typeCode}>{t.typeName}</option>)}
      </select>
      <Button appearance="primary" icon={<Add24Regular />} disabled={!typeCode} onClick={openCreate}>新增检查项</Button>
      <Button appearance="secondary" onClick={() => { setTypeManager(true); setTypeEditing(null) }}>设施类型管理</Button>
    </div>
    {error && <MessageBar intent="error"><MessageBarBody>{error}</MessageBarBody></MessageBar>}
    <article className="panel data-panel">
      {loading ? <Spinner label="正在读取巡检检查项" /> : items.length ? <div className="table-wrap"><table>
        <thead><tr><th>检查项编号</th><th>部件名称</th><th>检查标准</th><th>是否必检</th><th>排序</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>{items.map(item => <tr key={item.id}>
          <td><strong>{item.item_code}</strong></td>
          <td>{item.item_name}</td>
          <td>{item.inspection_standard || '—'}</td>
          <td>{item.required_flag ? '必检' : '选检'}</td>
          <td>{item.sort_order}</td>
          <td><span className={`badge ${item.enabled ? 'ok' : ''}`}>{item.enabled ? '启用' : '停用'}</span></td>
          <td><span className="row-actions"><Button size="small" onClick={() => openEdit(item)}>编辑</Button><Button size="small" className="btn-danger" onClick={() => remove(item)}>删除</Button></span></td>
        </tr>)}</tbody>
      </table></div> : <Empty>当前类型暂无检查项</Empty>}
    </article>
    {dialog && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation"><div className="form-dialog" role="dialog" aria-modal="true" aria-labelledby="item-title">
      <div><h2 id="item-title">{dialog === 'new' ? '新增巡检检查项' : '编辑巡检检查项'}</h2><p>检查项是保安扫码巡检时逐项确认的内容，停用后不再出现在巡检清单中。</p></div>
      <div className="form-grid">
        <div className="field"><Label htmlFor="item-code" required>检查项编号</Label><Input id="item-code" value={form.itemCode} onChange={(_, d) => setForm({ ...form, itemCode: d.value })} placeholder="例如 PRESSURE_CHECK" /></div>
        <div className="field"><Label htmlFor="item-name" required>部件名称</Label><Input id="item-name" value={form.itemName} onChange={(_, d) => setForm({ ...form, itemName: d.value })} placeholder="例如 箱门、水带、枪头" /></div>
        <div className="field span2"><Label htmlFor="item-standard" required>检查标准</Label><Textarea id="item-standard" value={form.inspectionStandard} onChange={(_, d) => setForm({ ...form, inspectionStandard: d.value })} placeholder="例如 箱门完好、开启正常，玻璃和标识清晰" /></div>
        <div className="field"><Label htmlFor="item-required">是否必检</Label>
          <select id="item-required" className="native-select" value={form.requiredFlag ? '1' : '0'} onChange={event => setForm({ ...form, requiredFlag: event.target.value === '1' })}>
            <option value="1">必检</option><option value="0">选检</option>
          </select>
        </div>
        <div className="field"><Label htmlFor="item-sort">排序</Label><Input id="item-sort" type="number" value={form.sortOrder} onChange={(_, d) => setForm({ ...form, sortOrder: d.value })} /></div>
        {dialog !== 'new' && <div className="field"><Label htmlFor="item-enabled">状态</Label>
          <select id="item-enabled" className="native-select" value={form.enabled ? '1' : '0'} onChange={event => setForm({ ...form, enabled: event.target.value === '1' })}>
            <option value="1">启用</option><option value="0">停用</option>
          </select>
        </div>}
      </div>
      <div className="dialog-actions"><Button appearance="secondary" onClick={() => setDialog(null)}>取消</Button><Button appearance="primary" disabled={submitting} onClick={submit}>{submitting ? '正在保存' : '保存'}</Button></div>
    </div></div></FluentProvider>, document.body)}
    {typeManager && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation"><div className="form-dialog" role="dialog" aria-modal="true" aria-labelledby="facility-type-title">
      <div><h2 id="facility-type-title">设施类型管理</h2><p>新增类型后可用于设施建档、巡检计划和检查项配置。有关联数据的类型不能直接删除。</p></div>
      {typeEditing ? <div className="form-grid">
        <div className="field"><Label htmlFor="type-code" required>类型编号</Label><Input id="type-code" value={typeForm.typeCode} onChange={(_, d) => setTypeForm({ ...typeForm, typeCode: d.value })} placeholder="例如 FIRE_DOOR" /></div>
        <div className="field"><Label htmlFor="type-name" required>类型名称</Label><Input id="type-name" value={typeForm.typeName} onChange={(_, d) => setTypeForm({ ...typeForm, typeName: d.value })} placeholder="例如 防火门" /></div>
        <div className="dialog-actions span2"><Button appearance="secondary" onClick={() => setTypeEditing(null)}>取消编辑</Button><Button appearance="primary" disabled={typeSubmitting} onClick={submitType}>{typeSubmitting ? '正在保存' : '保存类型'}</Button></div>
      </div> : <Button appearance="primary" icon={<Add24Regular />} onClick={openTypeCreate}>新增设施类型</Button>}
      <div className="table-wrap"><table><thead><tr><th>类型编号</th><th>类型名称</th><th>操作</th></tr></thead><tbody>{types.map(type => <tr key={type.id}><td><strong>{type.typeCode}</strong></td><td>{type.typeName}</td><td><span className="row-actions"><Button size="small" onClick={() => openTypeEdit(type)}>编辑</Button><Button size="small" className="btn-danger" onClick={() => removeType(type)}>删除</Button></span></td></tr>)}</tbody></table></div>
      <div className="dialog-actions"><Button appearance="primary" onClick={() => { setTypeManager(false); setTypeEditing(null) }}>完成</Button></div>
    </div></div></FluentProvider>, document.body)}
  </section>
}

export default function App() {
  const [user, setUser] = useState(null)
  const [checking, setChecking] = useState(Boolean(localStorage.getItem('campus-fire-token')))
  useEffect(() => {
    if (!checking) return
    api.me().then(current => current.roleCode === 'ADMIN' ? setUser(current) : localStorage.removeItem('campus-fire-token')).finally(() => setChecking(false))
  }, [])
  if (checking) return <div className="boot"><Spinner size="large" label="正在进入系统" /></div>
  return user ? <Dashboard user={user} onLogout={() => setUser(null)} /> : <Login onLogin={setUser} />
}
