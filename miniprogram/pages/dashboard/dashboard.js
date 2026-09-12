const app = getApp()
const { request } = require('../../utils/api')

const rectStatusText = { OPEN: '待整改', RESOLVED: '已整改' }
const inspectionStateText = { NORMAL: '30天内已巡检', OVERDUE: '超期未巡检', NEVER: '从未巡检' }
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
    inspectionRows: [],
    rectRows: [],
    trendBars: [],
    facilityRows: [],
    componentIssues: [],
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
      const inspection = data.inspectionSummary || {}
      const inspectionRows = ['NORMAL', 'OVERDUE', 'NEVER']
        .map(s => ({ status: s, label: inspectionStateText[s] || s, count: inspection[s.toLowerCase()] || 0 }))
      const rectMap = {}
      ;(data.rectificationSummary || []).forEach(r => { rectMap[r.status] = r.count })
      const rectRows = ['OPEN', 'RESOLVED']
        .map(s => ({ status: s, label: rectStatusText[s] || s, count: rectMap[s] || 0 }))
      // 后端只返回有提交记录的日期：补零成连续 trendDays 天，避免数据点少时单根柱子拉满整个图表
      const trend = data.inspectionTrend || []
      const countByDate = {}
      trend.forEach(t => { countByDate[String(t.date || '').slice(0, 10)] = Number(t.completedCount) || 0 })
      const trendMax = Math.max(1, ...Object.values(countByDate))
      const trendBars = []
      for (let i = this.data.trendDays - 1; i >= 0; i--) {
        const d = new Date(Date.now() - i * 86400000)
        const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
        const count = countByDate[key] || 0
        trendBars.push({
          date: key.slice(5),
          count,
          empty: count === 0,
          height: count === 0 ? 4 : Math.max(6, Math.round(count / trendMax * 100)),
        })
      }
      this.setData({
        stats: {
          facilityTotal: data.facilityTotal || 0,
          recent: inspection.recent || 0,
          overdue: inspection.overdue || 0,
          openRect: rectMap.OPEN || 0,
        },
        inspectionRows,
        rectRows,
        trendBars,
        facilityRows: data.facilitySummary || [],
        componentIssues: data.componentIssueSummary || [],
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
  /** 点保养到期设施进入只读查看页：该设施巡检状态、历史记录与现场照片 */
  openFacility(e) {
    const item = this.data.maintenanceDue[e.currentTarget.dataset.index]
    if (!item) return
    app.globalData.viewFacility = {
      id: item.id,
      facilityNo: item.facility_no,
      name: item.name,
      campus: item.campus,
      building: item.building,
      floor: item.floor,
      area: item.area,
    }
    wx.navigateTo({ url: '/pages/facility-view/facility-view' })
  },
  maintenanceDate(item) {
    return String(item.next_maintenance_at || '').slice(0, 10)
  },
})
