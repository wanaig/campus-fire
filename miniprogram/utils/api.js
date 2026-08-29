const { BASE_URL } = require('./config')

function authToken() {
  return wx.getStorageSync('token') || ''
}

function request(path, options = {}) {
  const { method = 'GET', data } = options
  return new Promise((resolve, reject) => {
    wx.request({
      url: BASE_URL + path,
      method,
      data,
      header: {
        'Content-Type': 'application/json',
        ...(authToken() ? { Authorization: 'Bearer ' + authToken() } : {}),
      },
      success(res) {
        const payload = res.data
        if (res.statusCode === 401) {
          if (path === '/auth/login') {
            reject(new Error((payload && payload.message) || '账号或密码错误'))
            return
          }
          wx.removeStorageSync('token')
          wx.removeStorageSync('user')
          wx.reLaunch({ url: '/pages/login/login' })
          reject(new Error('登录已过期，请重新登录'))
          return
        }
        if (res.statusCode >= 200 && res.statusCode < 300 && payload && payload.code === 'OK') {
          resolve(payload.data)
        } else {
          reject(new Error((payload && payload.message) || '系统暂时无法连接，请稍后重试'))
        }
      },
      fail() {
        reject(new Error('无法连接服务器，请检查网络或服务地址配置'))
      },
    })
  })
}

function deviceId() {
  let id = wx.getStorageSync('deviceId')
  if (!id) {
    id = 'dev-' + Math.random().toString(36).slice(2, 10) + Date.now().toString(36)
    wx.setStorageSync('deviceId', id)
  }
  return id
}

function getLocation() {
  return new Promise((resolve, reject) => {
    wx.getLocation({
      type: 'gcj02',
      isHighAccuracy: true,
      success: resolve,
      fail() {
        wx.getSetting({
          success(res) {
            if (res.authSetting['scope.userLocation'] === false) {
              wx.showModal({
                title: '需要定位权限',
                content: '巡检必须校验现场位置，请在设置中开启位置权限。',
                confirmText: '去设置',
                success(r) { if (r.confirm) wx.openSetting() },
              })
            } else {
              wx.showToast({ title: '定位失败，请重试', icon: 'none' })
            }
          },
        })
        reject(new Error('定位失败'))
      },
    })
  })
}

function uploadPhoto(sessionId, filePath, latitude, longitude) {
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${BASE_URL}/inspection/sessions/${sessionId}/photos`,
      filePath,
      name: 'file',
      header: { Authorization: 'Bearer ' + authToken() },
      formData: {
        captureSource: 'CAMERA',
        clientCapturedAt: new Date().toISOString(),
        deviceId: deviceId(),
        latitude: String(latitude),
        longitude: String(longitude),
      },
      success(res) {
        let payload = null
        try { payload = JSON.parse(res.data) } catch (e) { payload = null }
        if (res.statusCode >= 200 && res.statusCode < 300 && payload && payload.code === 'OK') {
          resolve(payload.data)
        } else {
          reject(new Error((payload && payload.message) || '照片上传失败'))
        }
      },
      fail() { reject(new Error('照片上传失败，请检查网络')) },
    })
  })
}

module.exports = { request, uploadPhoto, getLocation, deviceId, BASE_URL }
