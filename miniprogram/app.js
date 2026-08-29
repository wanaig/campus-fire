App({
  globalData: {
    user: null,
    largeText: false,
    demoMode: false,
    editFacility: null,
    claimToken: null,
    claimSerial: null,
    claimLocation: null,
  },
  onLaunch() {
    this.globalData.user = wx.getStorageSync('user') || null
    this.globalData.largeText = wx.getStorageSync('largeText') === 'true'
    this.globalData.demoMode = wx.getStorageSync('demoMode') === 'true'
  },
  isLoggedIn() {
    return Boolean(wx.getStorageSync('token') && this.globalData.user)
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
  allowedDistanceMeters() {
    return this.globalData.demoMode ? 10000 : 100
  },
  homePath(roleCode) {
    if (roleCode === 'COLLECTOR') return '/pages/collect/collect'
    if (roleCode === 'ADMIN') return '/pages/dashboard/dashboard'
    return '/pages/tasks/tasks'
  },
})
