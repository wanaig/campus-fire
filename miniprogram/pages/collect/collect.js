const app = getApp()
const { request } = require('../../utils/api')

const lifecycleText = { IN_USE: '在用', SUSPENDED: '停用', RETIRED: '退役', SCRAPPED: '报废' }
const lifecycleBadge = { IN_USE: 'ok', SUSPENDED: 'warn', RETIRED: 'info', SCRAPPED: 'danger' }

Page({
  data: {
    largeText: false,
    items: [],
    inUseCount: 0,
    loading: true,
    error: '',
  },
  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().syncState()
      this.getTabBar().setData({ current: 'collect' })
    }
    if (!app.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    // 设施采集是采集员职能：其他角色进入时送回各自首页
    const user = app.globalData.user
    if (user && user.roleCode !== 'COLLECTOR') {
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
      const [facilities, types] = await Promise.all([
        request('/facilities', { data: {} }),
        request('/facilities/types').catch(() => []),
      ])
      const typeNameMap = {}
      ;(types || []).forEach(t => { typeNameMap[t.typeCode] = t.typeName })
      const items = (facilities || []).map(f => Object.assign({}, f, {
        typeName: typeNameMap[f.facilityType] || f.facilityType,
        lifecycleLabel: lifecycleText[f.lifecycleStatus] || f.lifecycleStatus,
        lifecycleBadge: lifecycleBadge[f.lifecycleStatus] || 'info',
        locationText: [f.campus, f.building, f.floor, f.area].filter(Boolean).join(' / '),
        maintenanceText: f.nextMaintenanceAt ? String(f.nextMaintenanceAt).slice(0, 10) : '',
      }))
      this.setData({
        items,
        inUseCount: items.filter(i => i.lifecycleStatus === 'IN_USE').length,
        loading: false,
      })
    } catch (error) {
      this.setData({ error: error.message, items: [], loading: false })
    }
  },
  onEditCard(e) {
    const item = this.data.items[e.currentTarget.dataset.index]
    if (!item) return
    app.globalData.editFacility = item
    wx.navigateTo({ url: '/pages/collect-form/collect-form' })
  },
})
