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
      // 访客模式（未登录）：只读浏览巡检状态与记录
      const role = (user && user.roleCode) || (app.isVisitor && app.isVisitor() ? 'VISITOR' : 'GUARD')
      this.setData({ role })
    },
    switchTab(e) {
      const path = e.currentTarget.dataset.path
      wx.switchTab({ url: path })
    },
    async onScan() {
      const app = getApp()
      if (this.data.scanning) return
      // 访客扫码是只读查询：免登录识别设施码后查看巡检状态和历史记录
      if (this.data.role === 'VISITOR') {
        this.setData({ scanning: true })
        try {
          await flow.previewByScan()
        } catch (error) {
          if (error && error.message) {
            wx.showModal({ title: '扫码查询失败', content: error.message, showCancel: false })
          }
        } finally {
          this.setData({ scanning: false })
        }
        return
      }
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
          if (this.data.role === 'GUARD' && error.canRetryLocation) {
            this.showLocationRetry(error)
          } else {
            wx.showModal({ title: this.data.role === 'COLLECTOR' ? '扫码采集失败' : '无法开始巡检', content: error.message, showCancel: false })
          }
        }
      } finally {
        this.setData({ scanning: false })
      }
    },
    showLocationRetry(error) {
      const title = error.code === 'LOCATION_TOO_FAR'
        ? '定位距离过远'
        : error.code === 'LOCATION_UNAVAILABLE' ? '无法获取定位' : '定位不稳定'
      wx.showModal({
        title,
        content: error.message,
        showCancel: true,
        cancelText: '取消',
        confirmText: '重新定位',
        success: async result => {
          if (!result.confirm || this.data.scanning) return
          this.setData({ scanning: true })
          try {
            await flow.retryLocation(error)
          } catch (nextError) {
            if (nextError && nextError.canRetryLocation) {
              this.showLocationRetry(nextError)
            } else if (nextError && nextError.message) {
              wx.showModal({ title: '无法开始巡检', content: nextError.message, showCancel: false })
            }
          } finally {
            this.setData({ scanning: false })
          }
        },
      })
    },
  },
})
