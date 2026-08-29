const app = getApp()
const { request } = require('../../utils/api')
const { scanQrToken, startSession } = require('../../utils/inspection-flow')

const filters = [
  { key: '', label: '全部' },
  { key: 'PENDING', label: '待巡检' },
  { key: 'IN_PROGRESS', label: '巡检中' },
  { key: 'COMPLETED', label: '已完成' },
]

Page({
  data: {
    largeText: false,
    filters,
    active: '',
    items: [],
    loading: true,
    error: '',
    today: '',
  },
  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().syncState()
      this.getTabBar().setData({ current: 'tasks' })
    }
    if (!app.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    const now = new Date()
    const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
    this.setData({ largeText: app.globalData.largeText, today })
    this.load()
  },
  onPullDownRefresh() {
    this.load().finally(() => wx.stopPullDownRefresh())
  },
  async load() {
    this.setData({ loading: true, error: '' })
    try {
      const [tasks, rectifications] = await Promise.all([
        request('/inspection/tasks', {
          data: this.data.active ? { status: this.data.active } : {},
        }),
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
      const items = (tasks || []).map(t => {
        const list = byFacility[t.facility_id] || []
        const openCount = list.filter(r => r.status !== 'RESOLVED').length
        return Object.assign({}, t, {
          rectifications: list,
          rectCount: list.length,
          rectOpenCount: openCount,
          rectExpanded: Boolean(prev[t.id]),
        })
      })
      this.setData({ items })
    } catch (error) {
      this.setData({ error: error.message, items: [] })
    } finally {
      this.setData({ loading: false })
    }
  },
  switchFilter(e) {
    this.setData({ active: e.currentTarget.dataset.key })
    this.load()
  },
  toggleRect(e) {
    const index = e.currentTarget.dataset.index
    const key = `items[${index}].rectExpanded`
    this.setData({ [key]: !this.data.items[index].rectExpanded })
  },
  async startInspection(e) {
    wx.vibrateShort({ type: 'light' })
    const task = this.data.items[e.currentTarget.dataset.index]
    if (!task) return
    try {
      let qrToken = 'RESUME'
      if (task.status !== 'IN_PROGRESS') {
        qrToken = await scanQrToken()
      }
      await startSession(task, qrToken)
    } catch (error) {
      if (error && error.message) {
        wx.showModal({ title: '无法开始巡检', content: error.message, showCancel: false })
      }
    }
  },
})
