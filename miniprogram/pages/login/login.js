const app = getApp()
const { request } = require('../../utils/api')

Page({
  data: {
    username: 'guard',
    password: '',
    submitting: false,
  },
  onShow() {
    this.setData({ largeText: getApp().globalData.largeText })
  },
  onUsername(e) { this.setData({ username: e.detail.value }) },
  onPassword(e) { this.setData({ password: e.detail.value }) },
  async login() {
    const username = this.data.username.trim()
    const password = this.data.password
    if (!username || !password) {
      wx.showToast({ title: '请输入账号和密码', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    try {
      const data = await request('/auth/login', {
        method: 'POST',
        data: { username, password },
      })
      if (!['ADMIN', 'GUARD', 'COLLECTOR'].includes(data.user.roleCode)) {
        wx.showToast({ title: '当前账号无权使用巡检端', icon: 'none' })
        return
      }
      app.setUser(data.user, data.accessToken)
      wx.reLaunch({ url: app.homePath(data.user.roleCode) })
    } catch (error) {
      wx.showModal({ title: '登录失败', content: error.message, showCancel: false })
    } finally {
      this.setData({ submitting: false })
    }
  },
})
