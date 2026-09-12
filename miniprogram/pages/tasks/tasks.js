const app = getApp()
const { request } = require('../../utils/api')
const { previewRecordPhotos } = require('../../utils/photos')

// 巡检以设施为主体：超过 30 天（1 个月）未巡检即标记超期提醒
const OVERDUE_DAYS = 30

const filters = [
  { key: '', label: '全部' },
  { key: 'OVERDUE', label: '已超期' },
  { key: 'NEVER', label: '从未巡检' },
  { key: 'NORMAL', label: `${OVERDUE_DAYS}天内已巡检` },
]

Page({
  data: {
    largeText: false,
    filters,
    active: '',
    keyword: '',
    items: [],
    visible: [],
    summary: null,
    overdueDays: OVERDUE_DAYS,
    visitor: false,
    loading: true,
    error: '',
    maintenanceDue: 0,
    maintenanceOverdue: 0,
  },
  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().syncState()
      this.getTabBar().setData({ current: 'tasks' })
    }
    if (!app.isLoggedIn() && !app.isVisitor()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    // 扫码巡检是保安职能：管理员/采集员进入时送回各自首页（访客只读不受限）
    const user = app.globalData.user
    if (user && user.roleCode !== 'GUARD') {
      wx.reLaunch({ url: app.homePath(user.roleCode) })
      return
    }
    this.setData({ largeText: app.globalData.largeText, visitor: app.isVisitor() })
    this.load()
    this.loadMaintenanceDue()
  },
  /** 保养到期提醒：打开小程序时拉取到期部件数，仅保安 */
  async loadMaintenanceDue() {
    const user = app.globalData.user
    if (!app.isLoggedIn() || !user || user.roleCode !== 'GUARD') return
    try {
      const rows = await request('/maintenance/components/due')
      const list = Array.isArray(rows) ? rows : []
      this.setData({
        maintenanceDue: list.length,
        maintenanceOverdue: list.filter(item => item.dueStatus === 'OVERDUE').length,
      })
    } catch (_) {
      this.setData({ maintenanceDue: 0, maintenanceOverdue: 0 })
    }
  },
  goMaintenance() {
    wx.navigateTo({ url: '/pages/maintenance/maintenance' })
  },
  onPullDownRefresh() {
    this.load().finally(() => wx.stopPullDownRefresh())
  },
  async load() {
    this.setData({ loading: true, error: '' })
    try {
      const [status, rectifications] = await Promise.all([
        request('/inspection/status', { data: { overdueDays: OVERDUE_DAYS } }),
        request('/rectifications', { data: {} }).catch(() => []),
      ])
      const byFacility = {}
      ;(rectifications || []).forEach(r => {
        const fid = r.facility_id
        if (fid === null || fid === undefined) return
        if (!byFacility[fid]) byFacility[fid] = []
        byFacility[fid].push(r)
      })
      Object.keys(byFacility).forEach(key => {
        byFacility[key].sort((a, b) => (a.status === 'RESOLVED' ? 1 : 0) - (b.status === 'RESOLVED' ? 1 : 0))
      })
      const prev = {}
      ;(this.data.items || []).forEach(t => { prev[t.id] = t.rectExpanded })
      const items = (status.items || []).map(item => {
        const list = byFacility[item.id] || []
        return Object.assign({}, item, {
          rectifications: list,
          rectCount: list.length,
          rectOpenCount: list.filter(r => r.status !== 'RESOLVED').length,
          rectExpanded: Boolean(prev[item.id]),
          lastInspectedText: item.lastInspectedAt ? String(item.lastInspectedAt).replace('T', ' ').slice(0, 16) : '',
          overdueBy: item.daysSinceInspection == null ? 0 : Math.max(0, item.daysSinceInspection - OVERDUE_DAYS),
          maintenanceDueText: item.nextMaintenanceDue ? String(item.nextMaintenanceDue).slice(0, 10) : '',
          maintenanceCountdownText: this.maintenanceCountdown(item.nextMaintenanceDue),
        })
      })
      this.setData({ items, summary: status.summary || null })
      this.applyFilter()
    } catch (error) {
      this.setData({ error: error.message, items: [], visible: [] })
    } finally {
      this.setData({ loading: false })
    }
  },
  maintenanceCountdown(value) {
    if (!value) return ''
    const due = new Date(`${String(value).slice(0, 10)}T00:00:00`)
    if (Number.isNaN(due.getTime())) return ''
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    const days = Math.round((due.getTime() - today.getTime()) / 86400000)
    if (days < 0) return `已超期 ${Math.abs(days)} 天`
    if (days === 0) return '今天到期'
    return `还有 ${days} 天`
  },
  applyFilter() {
    const { items, active, keyword } = this.data
    const kw = (keyword || '').trim()
    const visible = (items || []).filter(item => {
      if (active && item.inspectionState !== active) return false
      if (!kw) return true
      return [item.name, item.facilityNo, item.campus, item.building, item.floor, item.area, item.detailLocation]
        .some(value => String(value || '').includes(kw))
    })
    this.setData({ visible })
  },
  switchFilter(e) {
    this.setData({ active: e.currentTarget.dataset.key })
    this.applyFilter()
  },
  onKeyword(e) {
    this.setData({ keyword: e.detail.value })
    this.applyFilter()
  },
  toggleRect(e) {
    const index = e.currentTarget.dataset.index
    const key = `visible[${index}].rectExpanded`
    this.setData({ [key]: !this.data.visible[index].rectExpanded })
  },
  /** 展开某消火栓的历史巡检记录：首次展开时按设施编号拉取正式巡检记录 */
  async toggleRecords(e) {
    const index = e.currentTarget.dataset.index
    const item = this.data.visible[index]
    if (!item) return
    if (item.recordsExpanded) {
      this.setData({ [`visible[${index}].recordsExpanded`]: false })
      return
    }
    if (item.recordsLoaded) {
      this.setData({ [`visible[${index}].recordsExpanded`]: true })
      return
    }
    this.setData({ [`visible[${index}].recordsLoading`]: true })
    try {
      const records = await request('/inspection/records', { data: { facilityNo: item.facilityNo } })
      const shaped = (records || []).slice(0, 20).map(record => {
        let chips = Array.isArray(record.results) ? record.results : []
        if (!chips.length) {
          try {
            const raw = typeof record.results_json === 'string' ? JSON.parse(record.results_json) : record.results_json
            if (raw && !Array.isArray(raw)) {
              chips = Object.entries(raw).map(([code, status]) => ({ code, name: code, status }))
            }
          } catch (_) {}
        }
        const abnormal = chips.filter(chip => String(chip.status).toUpperCase() === 'FAIL')
        return {
          id: record.id,
          timeText: String(record.submitted_at || '').replace('T', ' ').slice(0, 16),
          inspector: record.inspector || '—',
          photoCount: Number(record.photo_count) || 0,
          voidReason: record.void_reason || '',
          note: record.void_reason ? `作废原因：${record.void_reason}` : (record.note || (abnormal.length ? `异常部件：${abnormal.map(chip => chip.name).join('、')}` : '')),
          abnormalCount: abnormal.length,
          chips,
        }
      })
      this.setData({
        [`visible[${index}].records`]: shaped,
        [`visible[${index}].recordsLoaded`]: true,
        [`visible[${index}].recordsExpanded`]: true,
        [`visible[${index}].recordsLoading`]: false,
      })
    } catch (error) {
      this.setData({ [`visible[${index}].recordsLoading`]: false })
      wx.showModal({ title: '无法读取巡检记录', content: error.message, showCancel: false })
    }
  },
  /** 下载一条巡检记录的现场照片到本地后预览（照片接口只读公开，访客也可查看） */
  viewRecordPhotos(e) {
    const { index, rindex } = e.currentTarget.dataset
    const item = this.data.visible[index]
    const record = item && item.records && item.records[rindex]
    previewRecordPhotos(record)
  },
  /** 设施卡片「部件保养」入口：该设施有到期部件才可进入保养页（底部扫一扫保留巡检职能） */
  goMaintenance(e) {
    const index = e.currentTarget.dataset.index
    const item = this.data.visible[index]
    if (!item) return
    if (!item.maintenanceComponentCount) {
      wx.showToast({ title: '该设施没有设置保养周期', icon: 'none' })
      return
    }
    if (!item.dueComponentCount) {
      wx.showToast({ title: `尚未到保养时间，${item.maintenanceCountdownText || '到期前7天'}可处理`, icon: 'none' })
      return
    }
    const query = [
      `facilityId=${item.id}`,
      `name=${encodeURIComponent(item.name)}`,
      `no=${encodeURIComponent(item.facilityNo)}`,
      `location=${encodeURIComponent([item.campus, item.building, item.floor, item.area].filter(Boolean).join(' / '))}`,
    ].join('&')
    wx.navigateTo({ url: `/pages/maintenance/maintenance?${query}` })
  },
})
