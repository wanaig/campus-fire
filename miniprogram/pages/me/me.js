const app = getApp()
const { BASE_URL } = require('../../utils/api')

Page({
  data: {
    user: null,
    largeText: false,
    demoMode: false,
    baseUrl: BASE_URL,
  },
  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().syncState()
      this.getTabBar().setData({ current: 'me' })
    }
    if (!app.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    const user = app.globalData.user
    this.setData({
      user,
      largeText: app.globalData.largeText,
      demoMode: app.globalData.demoMode,
    })
  },
  toggleLargeText(e) {
    const value = e.detail.value
    app.globalData.largeText = value
    wx.setStorageSync('largeText', String(value))
    this.setData({ largeText: value })
  },
  toggleDemoMode(e) {
    const value = e.detail.value
    app.globalData.demoMode = value
    wx.setStorageSync('demoMode', String(value))
    this.setData({ demoMode: value })
  },
  logout() {
    wx.showModal({
      title: '退出登录',
      content: '确定要退出当前账号吗？未提交的巡检草稿已保存在服务器，重新登录后可继续。',
      success: res => {
        if (!res.confirm) return
        app.clearUser()
        wx.reLaunch({ url: '/pages/login/login' })
      },
    })
  },
})
