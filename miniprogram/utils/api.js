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

function getLocation(options = {}) {
  const { silent = false, highAccuracy = false } = options
  return new Promise((resolve, reject) => {
    wx.getLocation({
      type: 'gcj02',
      // 高精度模式会显著拉长单次定位耗时（最长等 5 秒），默认走系统快速定位；
      // 仅在采集员采集设施坐标、稳定定位首轮采样时显式开启
      isHighAccuracy: highAccuracy,
      highAccuracyExpireTime: 3000,
      success: resolve,
      fail() {
        if (silent) {
          reject(new Error('定位失败'))
          return
        }
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

const STABLE_LOCATION_SAMPLES = 4
const STABLE_LOCATION_INTERVAL_MS = 800
const STABLE_LOCATION_RADIUS_METERS = 300
const STABLE_LOCATION_GOOD_ACCURACY_METERS = 65

function locationDistanceMeters(a, b) {
  const latitude = (Number(a.latitude) + Number(b.latitude)) / 2
  const dLat = Number(a.latitude) - Number(b.latitude)
  const dLon = (Number(a.longitude) - Number(b.longitude)) * Math.cos(latitude * Math.PI / 180)
  return Math.sqrt(dLat * dLat + dLon * dLon) * 111000
}

function accuracyAsc(a, b) {
  const accuracyA = Number.isFinite(Number(a.accuracy)) ? Number(a.accuracy) : Number.MAX_SAFE_INTEGER
  const accuracyB = Number.isFinite(Number(b.accuracy)) ? Number(b.accuracy) : Number.MAX_SAFE_INTEGER
  return accuracyA - accuracyB
}

function retryableLocationError(message, code) {
  const error = new Error(message)
  error.code = code
  error.canRetryLocation = true
  return error
}

/** 从连续定位结果中保留稳定点群，容忍一个偶发坏点，并选取精度最好的一点。 */
function selectStableLocation(samples) {
  if (!Array.isArray(samples) || samples.length < 3) {
    throw retryableLocationError('有效定位次数不足，请检查定位权限和网络后重新定位', 'LOCATION_UNAVAILABLE')
  }
  let stable = []
  samples.forEach(candidate => {
    const nearby = samples.filter(sample => locationDistanceMeters(candidate, sample) <= STABLE_LOCATION_RADIUS_METERS)
    if (nearby.length > stable.length) stable = nearby
  })
  const required = Math.ceil(samples.length * 0.75)
  if (stable.length < required) {
    throw retryableLocationError(`连续定位结果相差超过${STABLE_LOCATION_RADIUS_METERS}米，当前定位不稳定，请保持在设施附近后重新定位`, 'LOCATION_UNSTABLE')
  }
  return stable.slice().sort(accuracyAsc)[0]
}

/** 前 3 个有效样本相互吻合且最好精度合格时即可提前采信，不必等满 4 轮。 */
function stableEnough(samples) {
  const best = samples.slice().sort(accuracyAsc)[0]
  if (Number.isFinite(Number(best.accuracy)) && Number(best.accuracy) > STABLE_LOCATION_GOOD_ACCURACY_METERS) {
    return false
  }
  return samples.every(sample => locationDistanceMeters(best, sample) <= STABLE_LOCATION_RADIUS_METERS)
}

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

/** 扫码巡检稳定定位：仅首轮走高精度，其余走系统快速定位，多数 3～5 秒完成（原先约 10～20 秒）。 */
async function getStableLocation(onProgress) {
  const samples = []
  for (let i = 0; i < STABLE_LOCATION_SAMPLES; i += 1) {
    if (typeof onProgress === 'function') onProgress(i + 1, STABLE_LOCATION_SAMPLES)
    try {
      const location = await getLocation({ silent: true, highAccuracy: i === 0 })
      if (Number.isFinite(Number(location.latitude)) && Number.isFinite(Number(location.longitude))) {
        samples.push(location)
      }
    } catch (_) { /* 单次定位失败时继续采样，最终按有效次数统一提示 */ }
    if (samples.length >= 3 && stableEnough(samples)) return selectStableLocation(samples)
    if (i < STABLE_LOCATION_SAMPLES - 1) await delay(STABLE_LOCATION_INTERVAL_MS)
  }
  return selectStableLocation(samples)
}

// 相机原图通常 2-6MB：上传前压缩到最长边约 1600px、质量 70，压缩失败时回退原图直传
function compressPhoto(filePath) {
  return new Promise(resolve => {
    wx.getImageInfo({
      src: filePath,
      success(info) {
        const maxSide = 1600
        const options = { src: filePath, quality: 70 }
        if (info.width > maxSide || info.height > maxSide) {
          if (info.width >= info.height) options.compressedWidth = maxSide
          else options.compressedHeight = maxSide
        }
        wx.compressImage(Object.assign(options, {
          success: res => resolve(res.tempFilePath || filePath),
          fail: () => resolve(filePath),
        }))
      },
      fail: () => resolve(filePath),
    })
  })
}

// 上传照片通用逻辑：压缩后 multipart 上传，返回 { photoId, sha256, serverReceivedAt }
function uploadPhotoTo(url, filePath, latitude, longitude, accuracyMeters) {
  return compressPhoto(filePath).then(compressedPath => new Promise((resolve, reject) => {
    const formData = {
      captureSource: 'CAMERA',
      clientCapturedAt: new Date().toISOString(),
      deviceId: deviceId(),
    }
    // 管理端关闭定位校验时允许无定位上传：坐标为空就整体省略，避免传 "null" 被后端拒收
    if (latitude != null && longitude != null) {
      formData.latitude = String(latitude)
      formData.longitude = String(longitude)
    }
    if (accuracyMeters != null && Number.isFinite(Number(accuracyMeters))) formData.accuracyMeters = String(accuracyMeters)
    wx.uploadFile({
      url,
      filePath: compressedPath,
      name: 'file',
      header: { Authorization: 'Bearer ' + authToken() },
      formData,
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
  }))
}

async function uploadPhoto(sessionId, filePath, latitude, longitude, accuracyMeters) {
  return uploadPhotoTo(`${BASE_URL}/inspection/sessions/${sessionId}/photos`, filePath, latitude, longitude, accuracyMeters)
}

// 采集员设施初始照片：随建档/编辑表单一起上传，锚点是设施 id 而非巡检会话
async function uploadFacilityPhoto(facilityId, filePath, latitude, longitude, accuracyMeters) {
  return uploadPhotoTo(`${BASE_URL}/facilities/${facilityId}/photos`, filePath, latitude, longitude, accuracyMeters)
}

module.exports = {
  request, uploadPhoto, uploadFacilityPhoto, getLocation, getStableLocation,
  selectStableLocation, locationDistanceMeters, retryableLocationError, deviceId, BASE_URL,
}
