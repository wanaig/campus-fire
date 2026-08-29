const flow = require('../utils/inspection-flow')

Component({
  data: {
    role: 'GUARD',
    current: 'tasks',
    scanning: false,
  },
  lifetimes: {
    attached() {
      this.syncState()
    },
  },
  methods: {
    syncState() {
      const app = getApp()
      if (!app) return
      const user = app.globalData && app.globalData.user
      this.setData({
        role: (user && user.roleCode) || 'GUARD',
      })
    },
    switchTab(e) {
      const path = e.currentTarget.dataset.path
      wx.switchTab({ url: path })
    },
    async onScan() {
      const app = getApp()
      if (this.data.scanning) return
      if (!app.isLoggedIn()) {
        wx.reLaunch({ url: '/pages/login/login' })
        return
      }
      this.setData({ scanning: true })
      try {
        if (this.data.role === 'COLLECTOR') {
          await flow.startCollectByScan()
        } else {
          await flow.startByScan()
        }
      } catch (error) {
        if (error && error.message) {
          wx.showModal({ title: this.data.role === 'COLLECTOR' ? '扫码采集失败' : '无法开始巡检', content: error.message, showCancel: false })
        }
      } finally {
        this.setData({ scanning: false })
      }
    },
  },
})
