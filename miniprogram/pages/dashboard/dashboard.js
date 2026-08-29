const app = getApp()
const { request } = require('../../utils/api')

const taskStatusText = { PENDING: '待巡检', IN_PROGRESS: '巡检中', COMPLETED: '已完成' }
const rectStatusText = { OPEN: '待整改', RESOLVED: '已整改' }
const trendFilters = [
  { key: 7, label: '近7天' },
  { key: 30, label: '近30天' },
  { key: 90, label: '近90天' },
]

Page({
  data: {
    largeText: false,
    trendFilters,
    trendDays: 30,
    stats: null,
    taskRows: [],
    rectRows: [],
    trendBars: [],
    facilityRows: [],
    maintenanceDue: [],
    generatedAt: '',
    loading: true,
    error: '',
  },
  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().syncState()
      this.getTabBar().setData({ current: 'dashboard' })
    }
    if (!app.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    const user = app.globalData.user
    if (user && user.roleCode !== 'ADMIN') {
      wx.reLaunch({ url: app.homePath(user.roleCode) })
      return
    }
    this.setData({ largeText: app.globalData.largeText })
    this.load()
  },
  onPullDownRefresh() {
    this.load().finally(() => wx.stopPullDownRefresh())
  },
  async load() {
    this.setData({ loading: true, error: '' })
    try {
      const data = await request('/dashboard/overview', {
        data: { trendDays: this.data.trendDays },
      })
      const taskMap = {}
      ;(data.taskSummary || []).forEach(r => { taskMap[r.status] = r.count })
      const taskRows = ['PENDING', 'IN_PROGRESS', 'COMPLETED']
        .map(s => ({ status: s, label: taskStatusText[s] || s, count: taskMap[s] || 0 }))
      const rectMap = {}
      ;(data.rectificationSummary || []).forEach(r => { rectMap[r.status] = r.count })
      const rectRows = ['OPEN', 'RESOLVED']
        .map(s => ({ status: s, label: rectStatusText[s] || s, count: rectMap[s] || 0 }))
      const trend = data.inspectionTrend || []
      const max = Math.max(1, ...trend.map(t => Number(t.completedCount) || 0))
      const trendBars = trend.map(t => {
        const count = Number(t.completedCount) || 0
        return { date: String(t.date || '').slice(5), count, height: Math.max(4, Math.round(count / max * 100)) }
      })
      this.setData({
        stats: {
          facilityTotal: data.facilityTotal || 0,
          pending: taskMap.PENDING || 0,
          overdue: data.overdueTaskCount || 0,
          openRect: rectMap.OPEN || 0,
        },
        taskRows,
        rectRows,
        trendBars,
        facilityRows: data.facilitySummary || [],
        maintenanceDue: data.maintenanceDueFacilities || [],
        generatedAt: data.generatedAt || '',
        loading: false,
      })
    } catch (error) {
      this.setData({ error: error.message, loading: false })
    }
  },
  switchTrend(e) {
    this.setData({ trendDays: Number(e.currentTarget.dataset.key) })
    this.load()
  },
  maintenanceDate(item) {
    return String(item.next_maintenance_at || '').slice(0, 10)
  },
})
