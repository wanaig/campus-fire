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

const statusText = { OPEN: '待整改', RESOLVED: '已整改' }
const pageTitle = {
  dashboard: '数据看板', facilities: '设施档案', qrcodes: '二维码标签', status: '巡检状态', records: '巡检记录', rectifications: '整改管理', maintenance: '维护保养记录', users: '用户管理', items: '巡检项配置', audits: '审计日志',
}
const inspectionStateText = { NORMAL: '正常', OVERDUE: '已超期', NEVER: '从未巡检' }
const roleOptions = [
  { code: 'GUARD', name: '保安（现场巡检）' },
  { code: 'COLLECTOR', name: '数据采集员（设施建档）' },
  { code: 'ADMIN', name: '系统管理员' },
]
const roleText = { ADMIN: '系统管理员', GUARD: '保安', COLLECTOR: '数据采集员', VISITOR: '访客' }
// 系统当前仅保留室内消火栓一种设施类型，新建/筛选入口默认选中它；类型列表缺失时回退第一个
const DEFAULT_FACILITY_TYPE = 'FIRE_HYDRANT'
function defaultTypeCode(types) {
  return types.some(item => item.typeCode === DEFAULT_FACILITY_TYPE) ? DEFAULT_FACILITY_TYPE : (types[0]?.typeCode || '')
}
// 设施名称由位置字段拼接生成（与小程序采集端保持一致）：校区-楼栋-楼层-区域[-详细位置] + 类型名
const NAME_LOCATION_FIELDS = ['campus', 'building', 'floor', 'area', 'detailLocation']
function joinAutoName(form, typeName) {
  const location = NAME_LOCATION_FIELDS.map(field => String(form[field] || '').trim()).filter(Boolean).join('-')
  return location ? (typeName ? `${location} ${typeName}` : location) : ''
}
// 巡检以设施为主体：超期未巡检的判定阈值，默认 30 天（1 个月）
const OVERDUE_DAY_OPTIONS = [
  { value: 7, label: '7 天' }, { value: 15, label: '15 天' }, { value: 30, label: '30 天（1 个月）' },
  { value: 60, label: '60 天' }, { value: 90, label: '90 天（一个季度）' },
]

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

  const inspection = data?.inspectionSummary || {}
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
        <button className={page === 'status' ? 'nav-active' : ''} onClick={() => setPage('status')}><ClipboardTaskListLtr24Regular />巡检状态</button>
        <button className={page === 'items' ? 'nav-active' : ''} onClick={() => setPage('items')}><ClipboardTaskListLtr24Regular />巡检项配置</button>
        <button className={page === 'records' ? 'nav-active' : ''} onClick={() => setPage('records')}><CheckmarkCircle24Regular />巡检记录</button>
        <button className={page === 'rectifications' ? 'nav-active' : ''} onClick={() => setPage('rectifications')}><Alert24Regular />整改管理</button>
        <button className={page === 'maintenance' ? 'nav-active' : ''} onClick={() => setPage('maintenance')}><CalendarClock24Regular />维护保养记录</button>
        <button className={page === 'users' ? 'nav-active' : ''} onClick={() => setPage('users')}><People24Regular />用户管理</button>
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
      {page === 'status' && <InspectionStatusPage />}
      {page === 'records' && <InspectionRecordsPage />}
      {page === 'rectifications' && <RectificationPage />}
      {page === 'maintenance' && <MaintenancePage />}
      {page === 'users' && <UsersPage />}
      {page === 'items' && <InspectionItemsPage />}
      {page === 'audits' && <AuditLogsPage />}
      {page === 'dashboard' && error && <MessageBar intent="error"><MessageBarBody>{error}<Button appearance="transparent" onClick={load}>重新加载</Button></MessageBarBody></MessageBar>}
      {page === 'dashboard' && (loading ? <DataState /> : data && <>
        <section className="metrics" aria-label="核心指标">
          <Metric icon={<Fire24Regular />} label="设施总数" value={data.facilityTotal} note="已纳入系统管理" />
          <Metric icon={<CheckmarkCircle24Regular />} label="30天内已巡检" value={inspection.recent} note="按设施统计巡检时效" />
          <Metric icon={<Warning24Regular />} label="超期未巡检" value={inspection.overdue} note="超过30天未巡检，含从未巡检" warning={(inspection.overdue || 0) > 0} />
          <Metric icon={<Alert24Regular />} label="待整改" value={rectMap.OPEN} note="异常设施待闭环" warning={rectMap.OPEN > 0} />
        </section>
        <section className="dashboard-grid">
          <article className="panel trend-panel">
            <div className="panel-head"><div><h2>巡检完成趋势</h2><p>按正式提交记录统计</p></div><select value={days} onChange={event => setDays(Number(event.target.value))} aria-label="趋势统计周期"><option value="7">近7天</option><option value="30">近30天</option><option value="90">近90天</option></select></div>
            {trend.length ? <ResponsiveContainer width="100%" height={280}><LineChart data={trend} margin={{ top: 20, right: 12, left: -20, bottom: 0 }}><CartesianGrid stroke="#e7eaee" vertical={false} /><XAxis dataKey="date" tickLine={false} axisLine={false} /><YAxis allowDecimals={false} tickLine={false} axisLine={false} /><ChartTooltip /><Line type="monotone" dataKey="count" name="完成数量" stroke="#b42318" strokeWidth={3} dot={{ r: 4, fill: '#b42318' }} /></LineChart></ResponsiveContainer> : <Empty>暂无已完成的巡检记录</Empty>}
          </article>
          <article className="panel status-panel">
            <div className="panel-head"><div><h2>巡检时效</h2><p>以设施为主体，统计最近巡检时间</p></div></div>
            <div className="status-list">{[['30天内已巡检', inspection.recent, 'completed'], ['超期未巡检', inspection.overdue, 'danger'], ['从未巡检', inspection.neverInspected, 'never']].map(([label, value, dot]) => <div key={label}><span className={`status-dot ${dot}`}></span><span>{label}</span><strong>{value || 0}</strong></div>)}</div>
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

/** 分页页码列表：始终包含首页、末页和当前页附近页码，页数多时中间用省略号折叠 */
function paginationPages(current, total) {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)
  const pages = [1]
  if (current > 3) pages.push('…')
  for (let i = Math.max(2, current - 1); i <= Math.min(total - 1, current + 1); i++) pages.push(i)
  if (current < total - 2) pages.push('…')
  pages.push(total)
  return pages
}

/** 码值展示：截断显示避免表格过宽，点击一键复制完整码值 */
function CopyableToken({ token }) {
  const [copied, setCopied] = useState(false)
  async function copy() {
    try {
      await navigator.clipboard.writeText(token)
    } catch (_) {
      // 非安全上下文（如局域网 IP 访问）时退化为选区复制
      const input = document.createElement('textarea')
      input.value = token
      document.body.appendChild(input)
      input.select()
      document.execCommand('copy')
      input.remove()
    }
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }
  return <span className="token-cell" title={`${token}（点击复制完整码值）`} onClick={copy}>
    <code className="token-code">{token.length > 18 ? `${token.slice(0, 10)}…${token.slice(-6)}` : token}</code>
    <span className={`token-copy ${copied ? 'ok' : ''}`}>{copied ? '已复制 ✓' : '复制'}</span>
  </span>
}

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
  const [commonFloorsText, setCommonFloorsText] = useState('')
  const [commonLabelsText, setCommonLabelsText] = useState('')
  const [specialFloors, setSpecialFloors] = useState([{ floor: '', labelsText: '' }])
  const [renameItem, setRenameItem] = useState(null)
  const [renameValue, setRenameValue] = useState('')
  const [selectedIds, setSelectedIds] = useState([])
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(20)
  const [total, setTotal] = useState(0)

  async function load(nextPage = page, nextSize = size) {
    setLoading(true); setError('')
    try {
      const normalizedKeyword = keyword.trim()
      const result = await api.qrCodes(status, normalizedKeyword, nextPage, nextSize)
      const totalCount = result.total || 0
      // 当前页超出范围（如末页数据被删除）时回退到最后一页
      const lastPage = Math.max(1, Math.ceil(totalCount / nextSize))
      if (!result.items.length && nextPage > lastPage) {
        setPage(lastPage)
        setItems(await api.qrCodes(status, normalizedKeyword, lastPage, nextSize).then(r => r.items || []))
        setTotal(totalCount)
      } else {
        setPage(nextPage)
        setSize(nextSize)
        setItems(result.items || [])
        setTotal(totalCount)
      }
      setAppliedKeyword(normalizedKeyword)
      setSelectedIds([])
    } catch (cause) { setError(cause.message) } finally { setLoading(false) }
  }
  useEffect(() => { setPage(1); load(1, size) /* eslint-disable-line react-hooks/exhaustive-deps */ }, [status])

  const totalPages = Math.max(1, Math.ceil(total / size))

  function goPage(target) {
    const next = Math.min(Math.max(1, target), totalPages)
    if (busy || next === page) return
    setPage(next)
    load(next, size)
  }

  function setSpecialRow(index, key, value) {
    setSpecialFloors(prev => prev.map((row, i) => i === index ? { ...row, [key]: value } : row))
  }

  function parseLabels(text) {
    return String(text || '').split(/[\n,，、;；]+/).map(value => value.trim()).filter(Boolean)
  }

  /** 解析楼层列表：支持「1层、2层、B1」与「1-6」连续楼层简写，返回 null 表示格式非法。 */
  function parseFloors(text) {
    const entries = String(text || '').split(/[\n,，、;；]+/).map(value => value.trim()).filter(Boolean)
    const floors = []
    for (const entry of entries) {
      const range = entry.match(/^([+-]?\d+)\s*[-~～]\s*([+-]?\d+)$/)
      if (range) {
        const start = Number(range[1])
        const end = Number(range[2])
        if (!Number.isInteger(start) || !Number.isInteger(end) || start < 1 || end < start || end - start >= 100) return null
        for (let i = start; i <= end; i++) floors.push(`${i}层`)
      } else {
        floors.push(/层$/.test(entry) ? entry : `${entry}层`)
      }
    }
    return [...new Set(floors)]
  }

  async function generate() {
    setError(''); setSuccess('')
    if (!school.trim() || !campus.trim() || !building.trim()) return setError('请填写学校、校区和楼栋')
    const commonFloors = parseFloors(commonFloorsText)
    if (commonFloors === null) return setError('通用楼层格式不正确，支持「1层、B1」或「1-6」连续楼层简写')
    const commonLabels = parseLabels(commonLabelsText)
    if (!commonFloors.length && !commonLabels.length && specialFloors.every(row => !row.floor.trim() && !parseLabels(row.labelsText).length)) {
      return setError('请填写通用楼层和二维码名称，或在下方添加特殊楼层')
    }
    if (commonFloors.length && !commonLabels.length) return setError('请填写通用二维码名称（使用通用名称的楼层已填写）')
    if (!commonFloors.length && commonLabels.length) return setError('请填写使用通用名称的楼层（通用二维码名称已填写）')

    const groups = []
    const usedFloors = new Set()
    const pushGroup = (floor, labels) => {
      if (usedFloors.has(floor)) return `楼层「${floor}」重复：通用楼层与特殊楼层不能重叠`
      if (labels.length > 500) return '每层最多设置 500 个二维码名称'
      if (labels.some(label => label.length > 120)) return '二维码名称不能超过 120 个字符'
      if (new Set(labels.map(label => label.toLocaleLowerCase())).size !== labels.length) return `楼层「${floor}」的二维码名称不能重复`
      usedFloors.add(floor)
      groups.push({ school: school.trim(), campus: campus.trim(), building: building.trim(), floor, labels })
      return null
    }
    let planned = 0
    for (const floor of commonFloors) {
      const problem = pushGroup(floor, commonLabels)
      if (problem) return setError(problem)
      planned += commonLabels.length
    }
    for (const row of specialFloors) {
      if (!row.floor.trim() && !parseLabels(row.labelsText).length) continue
      const floors = parseFloors(row.floor)
      const labels = parseLabels(row.labelsText)
      if (floors === null || !floors.length) return setError(`特殊楼层「${row.floor}」格式不正确，支持「4层」或「4-5」`)
      if (!labels.length) return setError(`特殊楼层「${row.floor}」请填写该层的二维码名称`)
      for (const floor of floors) {
        const problem = pushGroup(floor, labels)
        if (problem) return setError(problem)
        planned += labels.length
      }
    }
    if (!groups.length) return setError('请至少为一个楼层填写二维码名称')
    if (planned > 1000) return setError(`一次最多生成 1000 个二维码（当前约 ${planned} 个），请分批生成`)
    setBusy(true)
    try {
      const created = await api.createQrBatch({ groups })
      const createdIds = created.map(item => item.id)
      if (createdIds.some(id => id == null)) throw new Error('新生成二维码读取不完整，请刷新后重新下载')
      await api.downloadQrLabelsPdf('', createdIds)
      await load(1, size)
      setSuccess(`已生成 ${created.length} 个二维码，并下载 ${created.length} 页标签 PDF`)
    } catch (cause) { setError(cause.message) } finally { setBusy(false) }
  }

  function hierarchyParts(item) {
    const label = item.location_label || (item.location_no == null ? null : `第${String(item.location_no).padStart(2, '0')}号`)
    return [item.school, item.campus, item.building, item.floor, label].filter(Boolean)
  }

  async function resetQr(item) {
    const hierarchy = hierarchyParts(item).join(' / ') || `NO.${String(item.serial_no).padStart(3, '0')}`
    if (!window.confirm(`确定将「${hierarchy}」重置为未绑定吗？\n将同步删除其绑定的设施档案及巡检记录等关联数据，码值保持不变，采集员可重新扫码采集。`)) return
    setBusy(true); setError(''); setSuccess('')
    try {
      await api.unbindQrCode(item.id)
      setSuccess('二维码已重置为未绑定，码值不变，采集员可重新扫码采集')
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

  const previewCommonFloors = parseFloors(commonFloorsText)
  const commonFloorCount = previewCommonFloors ? previewCommonFloors.length : 0
  const commonLabelCount = parseLabels(commonLabelsText).length
  let plannedTotal = commonFloorCount * commonLabelCount
  for (const row of specialFloors) {
    const floors = parseFloors(row.floor)
    const labels = parseLabels(row.labelsText)
    if (floors && floors.length && labels.length) plannedTotal += floors.length * labels.length
  }

  return <section className="work-page">
    <article className="panel" style={{ marginBottom: 16 }}>
      <div className="panel-head"><div><h2>分级生成二维码</h2><p>按“学校 → 校区 → 楼栋 → 楼层 → 二维码名称”生成；多数楼层共用一组名称，名称不同的楼层在“特殊楼层”单独填写</p></div></div>
      <div className="qr-gen-form">
        <div className="qr-gen-head">
          <div className="field"><Label required>学校</Label><Input value={school} onChange={(_, d) => setSchool(d.value)} placeholder="例如 湖南科技职业学院" /></div>
          <div className="field"><Label required>校区</Label><Input value={campus} onChange={(_, d) => setCampus(d.value)} placeholder="例如 主校区" /></div>
          <div className="field"><Label required>楼栋</Label><Input value={building} onChange={(_, d) => setBuilding(d.value)} placeholder="例如 1号教学楼" /></div>
        </div>
        <div className="qr-gen-rows">
          <div className="qr-gen-row">
            <div className="field"><Label>使用通用名称的楼层</Label><Input value={commonFloorsText} onChange={(_, d) => setCommonFloorsText(d.value)} placeholder="例如 1-3、5-6、B1" /><span className="field-hint">支持「1-6」连续楼层简写，已识别 {commonFloorCount} 个楼层</span></div>
            <div className="field qr-label-names-field"><Label>通用二维码名称</Label><Textarea value={commonLabelsText} onChange={(_, d) => setCommonLabelsText(d.value)} placeholder="例如 东、东楼梯口、西、西楼梯口、中" resize="vertical" /><span className="field-hint">多个名称用逗号或换行分隔，已识别 {commonLabelCount} 个，上述楼层全部共用这一组</span></div>
          </div>
        </div>
        {specialFloors.map((row, index) => <div key={index} className="qr-gen-row">
          <div className="field"><Label>特殊楼层</Label><Input value={row.floor} onChange={(_, d) => setSpecialRow(index, 'floor', d.value)} placeholder="楼层，例如 4层 或 4-5" /></div>
          <div className="field qr-label-names-field"><Label>该层二维码名称</Label><Textarea value={row.labelsText} onChange={(_, d) => setSpecialRow(index, 'labelsText', d.value)} placeholder="与通用名称不同时填写，例如 东、西" resize="vertical" /><span className="field-hint">整行留空则不生成</span></div>
          <Button appearance="subtle" disabled={specialFloors.length === 1} onClick={() => setSpecialFloors(prev => prev.filter((_, i) => i !== index))}>删除</Button>
        </div>)}
        <div className="qr-gen-actions">
          <Button appearance="secondary" icon={<Add24Regular />} onClick={() => setSpecialFloors(prev => [...prev, { floor: '', labelsText: '' }])}>添加特殊楼层</Button>
          <Button appearance="primary" disabled={busy} onClick={generate}>{busy ? '正在处理…' : plannedTotal ? `生成并下载标签 PDF（${plannedTotal} 个二维码）` : '生成并下载标签 PDF'}</Button>
        </div>
      </div>
      {success && <MessageBar intent="success" style={{ marginTop: 12 }}><MessageBarBody>{success}</MessageBarBody></MessageBar>}
    </article>
    <div className="work-toolbar">
      <Input size="large" value={keyword} onChange={(_, d) => setKeyword(d.value)} placeholder="输入二维码名称、位置、码值或绑定设施" style={{ maxWidth: 320 }} />
      <Button appearance="primary" onClick={() => { setPage(1); load(1, size) }}>查询</Button>
      <Button icon={<Print24Regular />} appearance="secondary" disabled={busy || loading || !items.length || status === 'DELETED'} onClick={downloadLabels}>下载二维码标签 PDF（当前筛选）</Button>
    </div>
    {error && <MessageBar intent="error"><MessageBarBody>{error}</MessageBarBody></MessageBar>}
    <article className="panel data-panel">
      <div className="chips-row">
        {[['', '全部'], ['UNCLAIMED', '未绑定'], ['BOUND', '已绑定'], ['DELETED', '已删除']].map(([value, label]) => (
          <button key={value} className={`chip ${status === value ? 'chip-active' : ''}`} onClick={() => setStatus(value)}>{label}</button>
        ))}
        {status !== 'DELETED' && <Button appearance="secondary" disabled={busy || !selectedIds.length} onClick={batchDeleteQr}>批量删除（{selectedIds.length}）</Button>}
      </div>
      {loading ? <Spinner label="正在读取二维码" /> : items.length ? <div className="table-wrap"><table className="qr-table">
        <thead><tr><th className="select-cell"><input type="checkbox" aria-label="选择本页全部二维码" disabled={!selectableIds.length || status === 'DELETED'} checked={allSelected} onChange={() => setSelectedIds(allSelected ? [] : selectableIds)} /></th><th>系统序号</th><th>分级位置 / 自定义名称</th><th>码值</th><th>状态</th><th>绑定设施</th><th>时间</th><th>操作</th></tr></thead>
        <tbody>{items.map(item => <tr key={item.id}>
          <td className="select-cell"><input type="checkbox" aria-label={`选择 NO.${String(item.serial_no).padStart(3, '0')}`} disabled={item.status === 'DELETED'} checked={selectedIds.includes(item.id)} onChange={() => toggleSelected(item.id)} /></td>
          <td><strong>NO.{String(item.serial_no).padStart(3, '0')}</strong></td>
          <td>{hierarchyParts(item).length ? <span className="cell-clamp" title={hierarchyParts(item).join(' / ')}><strong>{hierarchyParts(item)[0]}</strong><small>{hierarchyParts(item).slice(1).join(' / ')}</small></span> : '—'}</td>
          <td><CopyableToken token={item.token} /></td>
          <td><span className={`badge ${item.status === 'UNCLAIMED' ? 'warn' : item.status === 'BOUND' ? 'ok' : ''}`}>{item.status === 'UNCLAIMED' ? '未绑定' : item.status === 'BOUND' ? '已绑定' : '已删除'}</span></td>
          <td>{item.facility_no ? <span className="cell-clamp" title={`${item.facility_name}（${item.facility_no}）`}><strong>{item.facility_name}</strong><small>{item.facility_no}</small></span> : '—'}</td>
          <td><span>{String(item.created_at).slice(0, 16).replace('T', ' ')}{item.deleted_at && <small>删除：{String(item.deleted_at).slice(0, 16).replace('T', ' ')}</small>}</span></td>
          <td><span className="row-actions">
            {item.status === 'DELETED' ? <Button size="small" appearance="primary" disabled={busy} onClick={() => restoreQr(item)}>恢复</Button> : <>
              <Button size="small" disabled={busy} onClick={() => openRename(item)}>修改名称</Button>
              {item.status === 'BOUND' && <Button size="small" disabled={busy} onClick={() => resetQr(item)}>重置为未绑定</Button>}
              <Button size="small" className="btn-danger" disabled={busy} onClick={() => deleteQr(item)}>删除</Button>
            </>}
          </span></td>
        </tr>)}</tbody>
      </table></div> : <Empty>还没有二维码，先在上方生成一批</Empty>}
      {!loading && total > 0 && <div className="pagination-bar">
        <span className="pagination-info">共 {total} 条 · 第 {page} / {totalPages} 页</span>
        <Button size="small" disabled={page <= 1 || busy} onClick={() => goPage(1)}>首页</Button>
        <Button size="small" disabled={page <= 1 || busy} onClick={() => goPage(page - 1)}>上一页</Button>
        {paginationPages(page, totalPages).map((p, index) => p === '…'
          ? <span key={`ellipsis-${index}`} className="pagination-ellipsis">…</span>
          : <button key={p} className={`page-btn ${p === page ? 'page-btn-active' : ''}`} disabled={busy} onClick={() => goPage(p)} aria-label={`第 ${p} 页`}>{p}</button>)}
        <Button size="small" disabled={page >= totalPages || busy} onClick={() => goPage(page + 1)}>下一页</Button>
        <Button size="small" disabled={page >= totalPages || busy} onClick={() => goPage(totalPages)}>末页</Button>
        <select value={size} onChange={event => { const s = Number(event.target.value); setSize(s); setPage(1); load(1, s) }} aria-label="每页条数">
          <option value={20}>每页 20 条</option>
          <option value={50}>每页 50 条</option>
          <option value={100}>每页 100 条</option>
        </select>
      </div>}
    </article>
    <p className="muted-note">使用流程：填写楼层和各二维码的自定义名称 → 生成并打印标签 → 采集员扫码建档。已生成二维码可单独修改名称；重置为未绑定只解除与设施的绑定，码值保持不变，采集员可重新扫码采集。</p>
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
  const [componentsView, setComponentsView] = useState(null)
  const [photoView, setPhotoView] = useState(null)
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
      // 设施只能由采集员扫空白码建档（一物一码），管理端仅编辑：编号原样回传，名称按位置自动拼接
      facilityNo: form.facilityNo.trim(),
      facilityType: form.facilityType, name: joinAutoName(form, types.find(type => type.typeCode === form.facilityType)?.typeName),
      campus: form.campus.trim(), building: form.building.trim(), floor: form.floor.trim(),
      area: form.area.trim(), detailLocation: form.detailLocation.trim(),
      latitude: form.latitude === '' ? null : Number(form.latitude), longitude: form.longitude === '' ? null : Number(form.longitude),
      brand: form.brand.trim() || null, model: form.model.trim() || null, specification: form.specification.trim() || null,
      manufactureDate: form.manufactureDate || null, commissionedDate: form.commissionedDate || null,
    }
  }
  async function submit() {
    if (!form.facilityType || !form.campus.trim() || !form.building.trim() || !form.floor.trim() || !form.area.trim()) {
      return setError('请填写设施类型和完整位置信息（校区、楼栋、楼层、区域）')
    }
    setSubmitting(true); setError('')
    try {
      await api.updateFacility(dialog.id, payload())
      setDialog(null); load()
    } catch (cause) { setError(cause.message) } finally { setSubmitting(false) }
  }
  async function remove(item) {
    if (!window.confirm(`确定删除设施「${item.name}（${item.facilityNo}）」吗？\n将同步删除其全部巡检记录、现场照片、整改单、维护记录等关联数据，不可恢复。`)) return
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
  async function openFacilityPhotos(item) {
    setPhotoView({ facility: item, photos: [], error: '' })
    try {
      const list = await api.facilityPhotos(item.id)
      const token = localStorage.getItem('campus-fire-token')
      const loaded = []
      for (const photo of list) {
        try {
          const response = await fetch(api.facilityPhotoFileUrl(photo.photoId), { headers: token ? { Authorization: `Bearer ${token}` } : {} })
          if (!response.ok) continue
          loaded.push({ photoId: photo.photoId, url: URL.createObjectURL(await response.blob()) })
        } catch (_) {}
      }
      setPhotoView({ facility: item, photos: loaded, error: loaded.length ? '' : '未找到设施初始照片文件' })
    } catch (cause) {
      setPhotoView({ facility: item, photos: [], error: cause.message })
    }
  }
  function closeFacilityPhotos() {
    if (photoView) photoView.photos.forEach(photo => URL.revokeObjectURL(photo.url))
    setPhotoView(null)
  }
  return <section className="work-page">
    <div className="work-toolbar">
      <Input size="large" value={keyword} onChange={(_, d) => setKeyword(d.value)} placeholder="输入设施编号、名称或位置" />
      <Button appearance="primary" onClick={() => load()}>查询</Button>
      <Button appearance="secondary" onClick={exportFacilities}>导出设施台账</Button>
      <Button icon={<Print24Regular />} appearance="secondary" disabled={!items.length || qrBusy} onClick={openQrSheet}>
        {qrBusy ? '正在生成…' : `下载二维码标签 PDF（${items.length} 个）`}
      </Button>
    </div>
    {error && <MessageBar intent="error"><MessageBarBody>{error}</MessageBarBody></MessageBar>}
    <article className="panel data-panel">
      {loading ? <Spinner label="正在读取设施档案" /> : items.length ? <div className="table-wrap"><table><thead><tr><th>设施编号</th><th>设施名称</th><th>类型</th><th>安装位置</th><th>状态</th><th>登记部件</th><th>初始照片</th><th>预计更新</th><th>下次保养</th><th>操作</th></tr></thead><tbody>{items.map(item => <tr key={item.id}><td><strong>{item.facilityNo}</strong></td><td>{item.name}</td><td>{types.find(type => type.typeCode === item.facilityType)?.typeName || item.facilityType}</td><td>{[item.campus,item.building,item.floor,item.area,item.detailLocation].filter(Boolean).join(' / ')}</td><td><span className={`badge ${item.lifecycleStatus === 'IN_USE' ? 'ok' : ''}`}>{item.lifecycleStatus === 'IN_USE' ? '在用' : item.lifecycleStatus}</span></td><td>{item.componentCount ? `${item.componentCount} 项` : '未登记'}</td><td>{item.photoCount ? <span className="photo-link" onClick={() => openFacilityPhotos(item)}>{item.photoCount} 张 · 查看</span> : '未拍摄'}</td><td>{item.expectedUpdateDate || '按规则确认'}</td><td>{item.nextMaintenanceAt ? String(item.nextMaintenanceAt).slice(0,10) : '未设置'}</td><td><span className="row-actions"><Button size="small" onClick={async()=>setHistory({facility:item,data:await api.facilityHistory(item.id)})}>历史</Button><Button size="small" onClick={async()=>setComponentsView({facility:item,data:await api.facilityDetail(item.id)})}>部件</Button><Button size="small" onClick={()=>openEdit(item)}>编辑</Button>{item.lifecycleStatus === 'IN_USE' && <Button size="small" onClick={()=>setLifecycle(item)}>停用/报废</Button>}<Button size="small" className="btn-danger" onClick={()=>remove(item)}>删除</Button></span></td></tr>)}</tbody></table></div> : <Empty>没有找到符合条件的设施</Empty>}
    </article>
    {history && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation"><div className="form-dialog" role="dialog" aria-modal="true"><h2>{history.facility.name} 历史记录</h2><p>巡检 {history.data.inspections?.length || 0} 次，维护 {history.data.maintenance?.length || 0} 次，状态变更 {history.data.lifecycle?.length || 0} 次。</p><div className="table-wrap"><table><tbody>{(history.data.inspections || []).slice(0,8).map(row=><tr key={'i'+row.id}><td>巡检</td><td>{String(row.submitted_at).replace('T',' ').slice(0,16)}</td><td>{row.inspector || '—'}，照片 {row.photo_count || 0} 张</td></tr>)}{(history.data.maintenance || []).slice(0,8).map(row=><tr key={'m'+row.id}><td>维护</td><td>{String(row.maintenance_at).replace('T',' ').slice(0,16)}</td><td>{row.maintainer}：{row.result_note}</td></tr>)}</tbody></table></div><div className="dialog-actions"><Button appearance="primary" onClick={()=>setHistory(null)}>关闭</Button></div></div></div></FluentProvider>, document.body)}
    {componentsView && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation"><div className="form-dialog" role="dialog" aria-modal="true"><h2>{componentsView.facility.name} 登记部件</h2><p>部件由采集员扫码建档时勾选登记，共 {(componentsView.data.components || []).length} 项；保安现场巡检按此清单逐项确认。</p>{componentsView.data.components?.length ? <div className="table-wrap"><table><thead><tr><th>部件编号</th><th>部件名称</th><th>生产日期</th><th>下次保养</th><th>登记时间</th></tr></thead><tbody>{componentsView.data.components.map(row => <tr key={row.itemCode}><td>{row.itemCode}</td><td><strong>{row.itemName}</strong></td><td>{row.manufactureDate || '—'}</td><td title={row.maintenanceCycleMonths ? `保养周期 ${row.maintenanceCycleMonths} 个月` : '未配置保养周期'}>{row.nextMaintenanceAt || '—'}</td><td>{row.createdAt ? String(row.createdAt).replace('T', ' ').slice(0, 16) : '—'}</td></tr>)}</tbody></table></div> : <Empty>该设施档案还未登记部件，请让采集员扫码补全档案</Empty>}<div className="dialog-actions"><Button appearance="primary" onClick={()=>setComponentsView(null)}>关闭</Button></div></div></div></FluentProvider>, document.body)}
    {photoView && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation" onClick={closeFacilityPhotos}><div className="resolve-dialog photo-dialog" role="dialog" aria-modal="true" aria-labelledby="facility-photo-title" onClick={event => event.stopPropagation()}>
      <h2 id="facility-photo-title">设施初始照片 · {photoView.facility.name}（{photoView.facility.facilityNo}）</h2>
      <p>采集员建档时现场拍摄的留痕照片{photoView.photos.length ? `，共 ${photoView.photos.length} 张` : ''}。</p>
      {photoView.error && <MessageBar intent="warning"><MessageBarBody>{photoView.error}</MessageBarBody></MessageBar>}
      <div className="photo-grid">
        {photoView.photos.map(photo => <img key={photo.photoId} src={photo.url} alt="设施初始照片" className="photo-preview" />)}
      </div>
      <div className="dialog-actions"><Button appearance="secondary" onClick={closeFacilityPhotos}>关闭</Button></div>
    </div></div></FluentProvider>, document.body)}
    {lifecycle && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation"><div className="resolve-dialog" role="dialog" aria-modal="true"><h2>变更设施状态</h2><p>{lifecycle.name}</p><select className="native-select" defaultValue="SUSPENDED" id="life-event"><option value="SUSPENDED">暂停使用/维修</option><option value="RETIRED">停用</option><option value="SCRAPPED">报废</option><option value="RESTORED">恢复使用</option></select><Textarea value={lifecycleNote} onChange={(_,d)=>setLifecycleNote(d.value)} placeholder="请填写变更原因" /><div className="dialog-actions"><Button appearance="secondary" onClick={()=>setLifecycle(null)}>取消</Button><Button appearance="primary" onClick={async()=>{if(!lifecycleNote.trim())return setError('请填写状态变更原因'); await api.changeFacilityLifecycle(lifecycle.id,document.getElementById('life-event').value,lifecycleNote.trim()); setLifecycle(null);setLifecycleNote('');load()}}>确认变更</Button></div></div></div></FluentProvider>, document.body)}
    {dialog && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation"><div className="form-dialog" role="dialog" aria-modal="true" aria-labelledby="facility-dialog-title">
      <div><h2 id="facility-dialog-title">编辑设施 {dialog.facilityNo}</h2><p>设施档案由采集员扫描二维码标签建档（一物一码），管理端仅可修改已有档案；带 * 为必填项，与采集端字段一致。</p></div>
      <div className="form-grid">
        <div className="field"><Label>设施编号（建档时自动生成）</Label><Input value={form.facilityNo} disabled /></div>
        <div className="field"><Label required>设施类型</Label>
          <select className="native-select" value={form.facilityType} onChange={event => setForm({ ...form, facilityType: event.target.value })}>
            {types.map(t => <option key={t.typeCode} value={t.typeCode}>{t.typeName}</option>)}
          </select>
        </div>
        <div className="field span2"><Label>设施名称（按位置自动拼接）</Label><Input value={joinAutoName(form, types.find(type => type.typeCode === form.facilityType)?.typeName)} disabled placeholder="填写下方位置信息后自动生成" /></div>
        <div className="field"><Label required>校区</Label><Input value={form.campus} onChange={(_, d) => setForm({ ...form, campus: d.value })} /></div>
        <div className="field"><Label required>楼栋</Label><Input value={form.building} onChange={(_, d) => setForm({ ...form, building: d.value })} /></div>
        <div className="field"><Label required>楼层</Label><Input value={form.floor} onChange={(_, d) => setForm({ ...form, floor: d.value })} /></div>
        <div className="field"><Label required>区域</Label><Input value={form.area} onChange={(_, d) => setForm({ ...form, area: d.value })} /></div>
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
  async function removeRecord(record) {
    if (!window.confirm(`确定彻底删除该巡检记录吗？\n「${record.name}（${record.facility_no}）」提交于 ${String(record.submitted_at || '').replace('T', ' ').slice(0, 16)}\n将同时删除该次巡检的现场照片、草稿与会话数据，不可恢复。`)) return
    try { await api.deleteInspectionRecord(record.id); load() } catch (cause) { setError(cause.message) }
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
            <td><span className="row-actions">{!item.void_reason && <Button size="small" className="btn-danger" onClick={() => { setVoiding(item); setVoidReason('') }}>作废</Button>}<Button size="small" className="btn-danger" onClick={() => removeRecord(item)}>删除</Button></span></td>
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

function InspectionStatusPage() {
  const [keyword, setKeyword] = useState('')
  const [facilityType, setFacilityType] = useState('')
  const [campus, setCampus] = useState('')
  const [overdueDays, setOverdueDays] = useState(30)
  const [stateFilter, setStateFilter] = useState('')
  const [types, setTypes] = useState([])
  const [campuses, setCampuses] = useState([])
  const [items, setItems] = useState([])
  const [summary, setSummary] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [recordView, setRecordView] = useState(null)
  const [recordBusy, setRecordBusy] = useState(false)
  const [recordPhotos, setRecordPhotos] = useState({})
  async function load(nextType = facilityType, nextCampus = campus, nextDays = overdueDays) {
    setLoading(true); setError('')
    try {
      const result = await api.inspectionStatus({ keyword: keyword.trim(), facilityType: nextType, campus: nextCampus, overdueDays: nextDays })
      setItems(result.items || [])
      setSummary(result.summary || null)
    } catch (cause) { setError(cause.message) } finally { setLoading(false) }
  }
  /** 查看单个消火栓的巡检记录（保安现场提交的正式记录） */
  async function openRecords(item) {
    setError('')
    setRecordView({ facility: item, records: [], loading: true })
    setRecordBusy(true)
    try {
      const records = await api.inspectionRecords(item.facilityNo)
      setRecordView({ facility: item, records: records || [], loading: false })
    } catch (cause) {
      setRecordView({ facility: item, records: [], loading: false })
      setError(cause.message)
    } finally { setRecordBusy(false) }
  }
  /** 在弹窗内查看某条记录的现场照片：照片文件需带登录态拉取 */
  async function toggleRecordPhotos(record) {
    const prev = recordPhotos[record.id]
    if (prev && !prev.loading) {
      setRecordPhotos({ ...recordPhotos, [record.id]: { ...prev, expanded: !prev.expanded } })
      return
    }
    setRecordPhotos(p => ({ ...p, [record.id]: { photos: [], loading: true, error: '', expanded: true } }))
    try {
      const token = localStorage.getItem('campus-fire-token')
      const list = await api.inspectionRecordPhotos(record.id)
      const loaded = []
      for (const photo of list) {
        try {
          const response = await fetch(api.inspectionPhotoFileUrl(photo.photoId), { headers: token ? { Authorization: `Bearer ${token}` } : {} })
          if (!response.ok) continue
          loaded.push({ photoId: photo.photoId, url: URL.createObjectURL(await response.blob()) })
        } catch (_) {}
      }
      setRecordPhotos(p => ({ ...p, [record.id]: { photos: loaded, loading: false, error: loaded.length ? '' : '未找到现场照片文件', expanded: true } }))
    } catch (cause) {
      setRecordPhotos(p => ({ ...p, [record.id]: { photos: [], loading: false, error: cause.message, expanded: true } }))
    }
  }
  function closeRecordView() {
    Object.values(recordPhotos).forEach(p => (p.photos || []).forEach(photo => URL.revokeObjectURL(photo.url)))
    setRecordPhotos({})
    setRecordView(null)
  }
  useEffect(() => {
    load()
    api.facilityTypes().then(list => setTypes(list)).catch(() => {})
    api.facilities('').then(list => {
      setCampuses([...new Set((list || []).map(item => item.campus).filter(Boolean))].sort())
    }).catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  const visibleItems = stateFilter ? items.filter(item => item.inspectionState === stateFilter) : items
  function overdueNote(item) {
    if (item.inspectionState === 'NEVER') return '尚未巡检过'
    return `已超期 ${item.daysSinceInspection - overdueDays} 天`
  }
  return <section className="work-page">
    <div className="work-toolbar">
      <Input size="large" value={keyword} onChange={(_, d) => setKeyword(d.value)} placeholder="输入设施编号、名称或位置" />
      <select value={facilityType} onChange={event => { setFacilityType(event.target.value); load(event.target.value, campus, overdueDays) }} aria-label="设施类型筛选">
        <option value="">全部类型</option>
        {types.map(item => <option key={item.typeCode} value={item.typeCode}>{item.typeName}</option>)}
      </select>
      <select value={campus} onChange={event => { setCampus(event.target.value); load(facilityType, event.target.value, overdueDays) }} aria-label="校区筛选">
        <option value="">全部校区</option>
        {campuses.map(item => <option key={item} value={item}>{item}</option>)}
      </select>
      <select value={overdueDays} onChange={event => { const days = Number(event.target.value); setOverdueDays(days); load(facilityType, campus, days) }} aria-label="超期判定阈值">
        {OVERDUE_DAY_OPTIONS.map(item => <option key={item.value} value={item.value}>超期阈值：{item.label}</option>)}
      </select>
      <Button appearance="primary" onClick={() => load()}>查询</Button>
    </div>
    {error && <MessageBar intent="error"><MessageBarBody>{error}<Button appearance="transparent" onClick={() => load()}>重新加载</Button></MessageBarBody></MessageBar>}
    {summary && <div className="chips" aria-label="巡检状态统计">
      <span className="chip">在用设施<b>{summary.total}</b></span>
      <span className="chip">{overdueDays}天内已巡检<b>{summary.recent}</b></span>
      <span className={`chip ${summary.overdue ? 'chip-danger' : ''}`}>超期未巡检<b>{summary.overdue}</b></span>
      <span className={`chip ${summary.neverInspected ? 'chip-danger' : ''}`}>从未巡检<b>{summary.neverInspected}</b></span>
    </div>}
    <article className="panel data-panel">
      {loading ? <Spinner label="正在读取设施巡检状态" /> : visibleItems.length ? <div className="table-wrap"><table>
        <thead><tr><th>设施</th><th>类型</th><th>位置</th><th>最近巡检</th><th>距今</th><th>巡检状态</th><th>待整改</th><th>操作</th></tr></thead>
        <tbody>{visibleItems.map(item => <tr key={item.id}>
          <td><strong>{item.name}</strong><small>{item.facilityNo}</small></td>
          <td>{item.facilityTypeName || '—'}</td>
          <td>{[item.campus, item.building, item.floor, item.area, item.detailLocation].filter(Boolean).join(' / ')}</td>
          <td>{item.lastInspectedAt ? <span>{String(item.lastInspectedAt).replace('T', ' ').slice(0, 16)}<small>{item.lastInspector ? `巡检人：${item.lastInspector}` : ''}</small></span> : '—'}</td>
          <td className={item.inspectionState === 'OVERDUE' ? 'text-danger' : ''}>{item.daysSinceInspection == null ? '—' : `${item.daysSinceInspection} 天`}</td>
          <td><span className={`badge ${item.inspectionState === 'NORMAL' ? 'ok' : 'warn'}`}>{inspectionStateText[item.inspectionState] || item.inspectionState}</span>{item.inspectionState === 'OVERDUE' && <small className="field-hint">{overdueNote(item)}</small>}</td>
          <td>{item.openRectificationCount > 0 ? <span className="text-danger">{item.openRectificationCount} 单</span> : '—'}</td>
          <td><Button size="small" disabled={recordBusy} onClick={() => openRecords(item)}>巡检记录</Button></td>
        </tr>)}</tbody>
      </table></div> : <Empty>没有符合条件的设施</Empty>}
    </article>
    <p className="muted-note">巡检不再需要提前创建计划：保安在小程序扫码即可对设施巡检，系统按设施统计最近巡检时间；超过阈值（默认 30 天，即 1 个月）未巡检的设施会在这里和看板中标红提醒。</p>
    {recordView && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation">
      <div className="form-dialog" role="dialog" aria-modal="true" aria-labelledby="facility-records-title">
        <div><h2 id="facility-records-title">{recordView.facility.name} · 巡检记录</h2>
          <p>{recordView.facility.facilityNo} · {[recordView.facility.campus, recordView.facility.building, recordView.facility.floor, recordView.facility.area].filter(Boolean).join(' / ')}</p></div>
        {recordView.loading ? <Spinner label="正在读取巡检记录" /> : recordView.records.length ? <div className="records-dialog-list">
          {recordView.records.map(record => {
            let results = Array.isArray(record.results) ? record.results : []
            if (!results.length) {
              try {
                const raw = typeof record.results_json === 'string' ? JSON.parse(record.results_json) : record.results_json
                if (raw && !Array.isArray(raw)) results = Object.entries(raw).map(([code, status]) => ({ code, name: code, status }))
              } catch (_) {}
            }
            const abnormal = results.filter(result => String(result.status).toUpperCase() === 'FAIL')
            return <div key={record.id} className={`record-dialog-item ${record.void_reason ? 'text-danger' : ''}`}>
              <div className="record-dialog-head">
                <span className={`badge ${record.void_reason ? 'warn' : abnormal.length ? 'warn' : 'ok'}`}>{record.void_reason ? '已作废' : abnormal.length ? `异常 ${abnormal.length} 项` : '全部正常'}</span>
                <span>{String(record.submitted_at || '').replace('T', ' ').slice(0, 16)} · {record.inspector || '—'}</span>
                {Number(record.photo_count) > 0 && <span className="photo-link" onClick={() => toggleRecordPhotos(record)}>{recordPhotos[record.id]?.expanded && !recordPhotos[record.id]?.loading ? '收起照片' : `查看照片 ${record.photo_count} 张`}</span>}
              </div>
              {results.length > 0 && <div className="result-chips records-dialog-chips">{results.map(result => <span key={result.code} className={`result-chip ${String(result.status).toUpperCase() === 'FAIL' ? 'fail' : ''}`}>{String(result.status).toUpperCase() === 'FAIL' ? '✗' : '✓'} {result.name}</span>)}</div>}
              {(record.void_reason || record.note) && <p className="record-dialog-note">{record.void_reason ? `作废原因：${record.void_reason}` : record.note}</p>}
              {recordPhotos[record.id]?.expanded && <div>
                {recordPhotos[record.id].loading && <Spinner size="tiny" label="正在加载照片" />}
                {recordPhotos[record.id].error && <MessageBar intent="warning"><MessageBarBody>{recordPhotos[record.id].error}</MessageBarBody></MessageBar>}
                {!!recordPhotos[record.id].photos.length && <div className="photo-grid">{recordPhotos[record.id].photos.map(photo => <img key={photo.photoId} src={photo.url} alt="巡检现场照片" className="photo-preview" />)}</div>}
              </div>}
            </div>
          })}
        </div> : <Empty>该设施还没有巡检记录</Empty>}
        <div className="dialog-actions"><Button appearance="primary" onClick={closeRecordView}>关闭</Button></div>
      </div>
    </div></FluentProvider>, document.body)}
  </section>
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
  async function resetPassword(item) {
    if (!window.confirm(`确定将账号「${item.username}（${item.display_name}）」的密码重置为默认密码 123456 吗？`)) return
    try {
      await api.resetUserPassword(item.id)
      setSuccess(`账号 ${item.username} 的密码已重置为 123456，请提醒首次登录后修改`)
      load()
    } catch (cause) { setError(cause.message) }
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
          <td><span className="row-actions"><Button size="small" onClick={() => openEdit(item)}>编辑</Button><Button size="small" onClick={() => resetPassword(item)}>重置密码</Button><Button size="small" className="btn-danger" onClick={() => removeUser(item)}>删除</Button></span></td>
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
  const [error, setError] = useState('')
  async function load(id = facilityId) {
    setLoading(true)
    try {
      if (!facilities.length) setFacilities(await api.facilities(''))
      setItems(await api.maintenanceRecords(id || ''))
    } catch (e) { setError(e.message) } finally { setLoading(false) }
  }
  useEffect(() => { load('') }, [])
  const typeText = { MAINTAIN: '保养', REPLACE: '更换', ROUTINE: '日常保养', INSPECTION: '专业检测', REPAIR: '维修更换' }
  return <section className="work-page">
    <div className="work-toolbar">
      <select className="native-select" value={facilityId} onChange={e => { setFacilityId(e.target.value); load(e.target.value) }}>
        <option value="">全部设施</option>
        {facilities.map(f => <option key={f.id} value={f.id}>{f.facilityNo} {f.name}</option>)}
      </select>
      <Button appearance="secondary" onClick={() => load()}>刷新</Button>
    </div>
    {error && <MessageBar intent="error"><MessageBarBody>{error}</MessageBarBody></MessageBar>}
    <article className="panel data-panel">
      {loading ? <Spinner label="正在读取维护记录" /> : items.length ? <div className="table-wrap"><table>
        <thead><tr><th>设施</th><th>部件</th><th>类型</th><th>维护人</th><th>维护时间</th><th>结果</th></tr></thead>
        <tbody>{items.map(i => <tr key={i.id}>
          <td><strong>{i.name}</strong><small>{i.facility_no}</small></td>
          <td>{i.component_name || '—'}</td>
          <td>{typeText[i.maintenance_type] || i.maintenance_type}</td>
          <td>{i.maintainer}</td>
          <td>{String(i.maintenance_at).replace('T', ' ').slice(0, 16)}</td>
          <td className="issue-cell">{i.result_note}</td>
        </tr>)}</tbody>
      </table></div> : <Empty>暂无维护保养记录</Empty>}
      <p className="table-note">维护保养记录由保安在小程序端对到期部件执行保养或更换时自动生成，管理端仅可查看。</p>
    </article>
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

function formatMaintenanceCycle(months) {
  if (!months) return '—'
  return months % 12 === 0 ? `${months / 12} 年` : `${months} 个月`
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
  const [dragIndex, setDragIndex] = useState(null)
  const [dragOver, setDragOver] = useState(null)
  const emptyForm = {
    itemName: '', inspectionStandard: '', maintenanceMode: 'FIXED',
    maintenanceCycleValue: '', maintenanceCycleUnit: 'YEAR', maintenanceYearThreshold: '',
    maintenanceCycleBeforeValue: '', maintenanceCycleBeforeUnit: 'YEAR',
    maintenanceCycleAfterValue: '', maintenanceCycleAfterUnit: 'YEAR',
    requiredFlag: true, enabled: true,
  }
  const emptyTypeForm = { typeCode: '', typeName: '' }
  const [form, setForm] = useState(emptyForm)
  const [typeForm, setTypeForm] = useState(emptyTypeForm)
  async function load(code = typeCode) {
    if (!code) return
    setLoading(true); setError(''); setDragIndex(null); setDragOver(null)
    try { setItems(await api.inspectionItems(code)) } catch (cause) { setError(cause.message) } finally { setLoading(false) }
  }
  async function refreshTypes(preferredCode = typeCode) {
    const list = await api.managedFacilityTypes()
    setTypes(list)
    const nextCode = list.some(type => type.typeCode === preferredCode) ? preferredCode : defaultTypeCode(list)
    setTypeCode(nextCode)
    if (nextCode) await load(nextCode)
    else setItems([])
  }
  useEffect(() => { refreshTypes('').catch(cause => setError(cause.message)) }, [])
  function openCreate() { setForm(emptyForm); setDialog('new') }
  function openEdit(item) {
    const savedMonths = item.maintenance_cycle_months
    const beforeMonths = item.maintenance_cycle_before_months
    const afterMonths = item.maintenance_cycle_after_months
    const cycleFields = (months, prefix) => {
      const useYears = months && months % 12 === 0
      return { [`${prefix}Value`]: months == null ? '' : (useYears ? months / 12 : months), [`${prefix}Unit`]: useYears ? 'YEAR' : 'MONTH' }
    }
    setForm({
      itemCode: item.item_code || '', itemName: item.item_name || '', inspectionStandard: item.inspection_standard || '',
      maintenanceMode: item.maintenance_year_threshold == null ? 'FIXED' : 'YEAR_RULE',
      maintenanceYearThreshold: item.maintenance_year_threshold ?? '',
      ...cycleFields(savedMonths, 'maintenanceCycle'),
      ...cycleFields(beforeMonths, 'maintenanceCycleBefore'),
      ...cycleFields(afterMonths, 'maintenanceCycleAfter'),
      requiredFlag: item.required_flag === 1,
      enabled: item.enabled === 1,
    })
    setDialog(item)
  }
  async function submit() {
    if (!form.itemName.trim() || !form.inspectionStandard.trim()) return setError('请填写部件名称和检查标准')
    const asPositiveInteger = value => value === '' ? null : Number(value)
    const toMonths = (value, unit) => value === null ? null : (unit === 'YEAR' ? value * 12 : value)
    const cycleValue = asPositiveInteger(form.maintenanceCycleValue)
    const beforeValue = asPositiveInteger(form.maintenanceCycleBeforeValue)
    const afterValue = asPositiveInteger(form.maintenanceCycleAfterValue)
    const yearThreshold = form.maintenanceYearThreshold === '' ? null : Number(form.maintenanceYearThreshold)
    const validCycle = value => Number.isInteger(value) && value >= 1
    if (form.maintenanceMode === 'FIXED' && cycleValue !== null && !validCycle(cycleValue)) return setError('保养周期需为不小于 1 的整数')
    if (form.maintenanceMode === 'YEAR_RULE') {
      if (!Number.isInteger(yearThreshold) || yearThreshold < 1900 || yearThreshold > 9999) return setError('请输入四位分界年份，例如 2025')
      if (!validCycle(beforeValue) || !validCycle(afterValue)) return setError('请完整填写分界年份前后的保养周期，周期需为正整数')
    }
    setSubmitting(true); setError('')
    const payload = {
      facilityType: typeCode, itemName: form.itemName.trim(), inspectionStandard: form.inspectionStandard.trim(),
      // 后端统一按月计算；年份规则以部件生产年份为准，分界年份当年归入“及以后”。
      maintenanceCycleMonths: form.maintenanceMode === 'FIXED' ? toMonths(cycleValue, form.maintenanceCycleUnit) : null,
      maintenanceYearThreshold: form.maintenanceMode === 'YEAR_RULE' ? yearThreshold : null,
      maintenanceCycleBeforeMonths: form.maintenanceMode === 'YEAR_RULE' ? toMonths(beforeValue, form.maintenanceCycleBeforeUnit) : null,
      maintenanceCycleAfterMonths: form.maintenanceMode === 'YEAR_RULE' ? toMonths(afterValue, form.maintenanceCycleAfterUnit) : null,
      requiredFlag: form.requiredFlag,
    }
    try {
      if (dialog === 'new') await api.createInspectionItem(payload)
      else await api.updateInspectionItem(dialog.id, { ...payload, enabled: form.enabled })
      setDialog(null); load()
    } catch (cause) { setError(cause.message) } finally { setSubmitting(false) }
  }
  /** 拖拽排序：松手后把新顺序整体提交，后端按 10、20、30… 重新编号；保存失败时回退到服务端顺序 */
  async function reorderItems(from, target, after) {
    if (from === null || from === target) return
    const next = [...items]
    const [moved] = next.splice(from, 1)
    next.splice(from < target ? target - 1 + (after ? 1 : 0) : target + (after ? 1 : 0), 0, moved)
    setItems(next)
    try { await api.updateInspectionItemOrder(next.map(row => row.id)) } catch (cause) { setError(cause.message); load() }
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
    <p className="drag-hint">拖动行首 ⠿ 可调整巡检顺序，松手自动保存并同步到小程序</p>
    {error && <MessageBar intent="error"><MessageBarBody>{error}</MessageBarBody></MessageBar>}
    <article className="panel data-panel">
      {loading ? <Spinner label="正在读取巡检检查项" /> : items.length ? <div className="table-wrap"><table>
        <thead><tr><th className="drag-cell"></th><th>检查项编号</th><th>部件名称</th><th>检查标准</th><th>保养周期</th><th>是否必检</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>{items.map((item, index) => <tr key={item.id} draggable
          onDragStart={() => setDragIndex(index)}
          onDragEnd={() => { setDragIndex(null); setDragOver(null) }}
          onDragOver={event => { event.preventDefault(); if (dragIndex !== null) setDragOver(index) }}
          onDrop={event => {
            event.preventDefault()
            const rect = event.currentTarget.getBoundingClientRect()
            reorderItems(dragIndex, index, event.clientY > rect.top + rect.height / 2)
            setDragIndex(null); setDragOver(null)
          }}
          className={`${dragIndex === index ? 'dragging' : ''} ${dragOver === index && dragIndex !== null && dragIndex !== index ? 'drag-over' : ''}`.trim()}>
          <td className="drag-cell"><span className="drag-handle" title="拖动调整巡检顺序">⠿</span></td>
          <td><strong>{item.item_code}</strong></td>
          <td>{item.item_name}</td>
          <td>{item.inspection_standard || '—'}</td>
          <td>{item.maintenance_year_threshold
            ? `${item.maintenance_year_threshold} 年前：${formatMaintenanceCycle(item.maintenance_cycle_before_months)}；${item.maintenance_year_threshold} 年及以后：${formatMaintenanceCycle(item.maintenance_cycle_after_months)}`
            : formatMaintenanceCycle(item.maintenance_cycle_months)}</td>
          <td>{item.required_flag ? '必检' : '选检'}</td>
          <td><span className={`badge ${item.enabled ? 'ok' : ''}`}>{item.enabled ? '启用' : '停用'}</span></td>
          <td><span className="row-actions"><Button size="small" onClick={() => openEdit(item)}>编辑</Button><Button size="small" className="btn-danger" onClick={() => remove(item)}>删除</Button></span></td>
        </tr>)}</tbody>
      </table></div> : <Empty>当前类型暂无检查项</Empty>}
    </article>
    {dialog && createPortal(<FluentProvider theme={webLightTheme}><div className="dialog-backdrop" role="presentation"><div className="form-dialog" role="dialog" aria-modal="true" aria-labelledby="item-title">
      <div><h2 id="item-title">{dialog === 'new' ? '新增巡检检查项' : '编辑巡检检查项'}</h2><p>可设置固定周期，也可按部件生产年份分段设置。分界年份当年归入“及以后”，系统按生产日期计算下次保养。</p></div>
      <div className="form-grid">
        <div className="field"><Label htmlFor="item-code">检查项编号</Label><Input id="item-code" disabled value={dialog === 'new' ? '保存后由系统自动生成' : form.itemCode} aria-readonly="true" /></div>
        <div className="field"><Label htmlFor="item-name" required>部件名称</Label><Input id="item-name" value={form.itemName} onChange={(_, d) => setForm({ ...form, itemName: d.value })} placeholder="例如 箱门、水带、枪头" /></div>
        <div className="field span2"><Label htmlFor="item-standard" required>检查标准</Label><Textarea id="item-standard" value={form.inspectionStandard} onChange={(_, d) => setForm({ ...form, inspectionStandard: d.value })} placeholder="例如 箱门完好、开启正常，玻璃和标识清晰" /></div>
        <div className="field span2"><Label htmlFor="item-maintenance-mode">保养周期规则</Label><select id="item-maintenance-mode" className="native-select" value={form.maintenanceMode} onChange={event => setForm({ ...form, maintenanceMode: event.target.value })}><option value="FIXED">固定周期</option><option value="YEAR_RULE">按生产年份分段</option></select></div>
        {form.maintenanceMode === 'FIXED' ? <>
          <div className="field"><Label htmlFor="item-cycle">保养周期</Label><Input id="item-cycle" type="number" min="1" step="1" value={form.maintenanceCycleValue} onChange={(_, d) => setForm({ ...form, maintenanceCycleValue: d.value })} placeholder={form.maintenanceCycleUnit === 'YEAR' ? '如 1，表示每年保养' : '如 6，表示每6个月保养'} /></div>
          <div className="field"><Label htmlFor="item-cycle-unit">周期单位</Label><select id="item-cycle-unit" className="native-select" value={form.maintenanceCycleUnit} onChange={event => setForm({ ...form, maintenanceCycleUnit: event.target.value })}><option value="YEAR">年</option><option value="MONTH">月</option></select></div>
        </> : <>
          <div className="field span2"><Label htmlFor="item-year-threshold" required>分界年份</Label><Input id="item-year-threshold" type="number" min="1900" max="9999" step="1" value={form.maintenanceYearThreshold} onChange={(_, d) => setForm({ ...form, maintenanceYearThreshold: d.value })} placeholder="例如 2025" /></div>
          <div className="field"><Label htmlFor="item-cycle-before" required>{form.maintenanceYearThreshold || '分界年份'} 年前的保养周期</Label><Input id="item-cycle-before" type="number" min="1" step="1" value={form.maintenanceCycleBeforeValue} onChange={(_, d) => setForm({ ...form, maintenanceCycleBeforeValue: d.value })} placeholder="请输入正整数" /></div>
          <div className="field"><Label htmlFor="item-cycle-before-unit">周期单位</Label><select id="item-cycle-before-unit" className="native-select" value={form.maintenanceCycleBeforeUnit} onChange={event => setForm({ ...form, maintenanceCycleBeforeUnit: event.target.value })}><option value="YEAR">年</option><option value="MONTH">月</option></select></div>
          <div className="field"><Label htmlFor="item-cycle-after" required>{form.maintenanceYearThreshold || '分界年份'} 年及以后的保养周期</Label><Input id="item-cycle-after" type="number" min="1" step="1" value={form.maintenanceCycleAfterValue} onChange={(_, d) => setForm({ ...form, maintenanceCycleAfterValue: d.value })} placeholder="请输入正整数" /></div>
          <div className="field"><Label htmlFor="item-cycle-after-unit">周期单位</Label><select id="item-cycle-after-unit" className="native-select" value={form.maintenanceCycleAfterUnit} onChange={event => setForm({ ...form, maintenanceCycleAfterUnit: event.target.value })}><option value="YEAR">年</option><option value="MONTH">月</option></select></div>
        </>}
        <div className="field"><Label htmlFor="item-required">是否必检</Label>
          <select id="item-required" className="native-select" value={form.requiredFlag ? '1' : '0'} onChange={event => setForm({ ...form, requiredFlag: event.target.value === '1' })}>
            <option value="1">必检</option><option value="0">选检</option>
          </select>
        </div>
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
