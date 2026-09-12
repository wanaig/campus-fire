App({
  globalData: {
    user: null,
    largeText: false,
    editFacility: null,
    viewFacility: null,
    claimToken: null,
    claimSerial: null,
    claimLocation: null,
  },
  onLaunch() {
    this.globalData.user = wx.getStorageSync('user') || null
    this.globalData.largeText = wx.getStorageSync('largeText') === 'true'
  },
  isLoggedIn() {
    return Boolean(wx.getStorageSync('token') && this.globalData.user)
  },
  // 访客模式：无需登录，只读查看设施巡检状态和历史记录
  isVisitor() {
    return !this.isLoggedIn() && wx.getStorageSync('visitorMode') === 'true'
  },
  setVisitor() {
    wx.setStorageSync('visitorMode', 'true')
    this.globalData.visitorMode = true
  },
  clearVisitor() {
    wx.removeStorageSync('visitorMode')
    this.globalData.visitorMode = false
  },
  setUser(user, token) {
    this.globalData.user = user
    wx.setStorageSync('token', token)
    wx.setStorageSync('user', user)
  },
  clearUser() {
    this.globalData.user = null
    wx.removeStorageSync('token')
    wx.removeStorageSync('user')
  },
  homePath(roleCode) {
    if (roleCode === 'COLLECTOR') return '/pages/collect/collect'
    if (roleCode === 'ADMIN') return '/pages/dashboard/dashboard'
    return '/pages/tasks/tasks'
  },
})
