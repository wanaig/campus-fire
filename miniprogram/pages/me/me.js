const app = getApp()

Page({
  data: {
    user: null,
    largeText: false,
    visitor: false,
  },
  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().syncState()
      this.getTabBar().setData({ current: 'me' })
    }
    if (!app.isLoggedIn() && !app.isVisitor()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    const user = app.isLoggedIn() ? app.globalData.user : null
    this.setData({
      user,
      visitor: app.isVisitor(),
      largeText: app.globalData.largeText,
    })
  },
  toggleLargeText(e) {
    const value = e.detail.value
    app.globalData.largeText = value
    wx.setStorageSync('largeText', String(value))
    this.setData({ largeText: value })
  },
  logout() {
    if (this.data.visitor) {
      app.clearVisitor()
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    wx.showModal({
      title: '退出登录',
      content: '确定要退出当前账号吗？未提交的巡检草稿已保存在服务器，重新登录后可继续。',
      success: res => {
        if (!res.confirm) return
        app.clearUser()
        app.clearVisitor()
        wx.reLaunch({ url: '/pages/login/login' })
      },
    })
  },
})
