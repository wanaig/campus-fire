const { request, getLocation, getStableLocation } = require('./api')

function parseQrToken(raw) {
  const text = (raw || '').trim()
  const match = text.match(/[?&]token=([A-Za-z0-9\-]+)/)
  const token = match ? match[1] : text
  if (!token || token.length > 64 || token === 'RESUME') return null
  return token
}

function scanQrToken() {
  return new Promise((resolve, reject) => {
    wx.scanCode({
      onlyFromCamera: true,
      scanType: ['qrCode'],
      success(res) {
        const token = parseQrToken(res.result)
        if (!token) {
          reject(new Error('二维码内容无效，请扫描设施上的巡检二维码'))
          return
        }
        resolve(token)
      },
      fail() { reject(null) },
    })
  })
}

function locationText(facility) {
  return [facility.campus, facility.building, facility.floor, facility.area, facility.detailLocation].filter(Boolean).join(' / ')
}

function navigateToInspect(facility, session) {
  const query = [
    `sessionId=${session.sessionId}`,
    `facilityType=${facility.facilityType || 'FIRE_HYDRANT'}`,
    `name=${encodeURIComponent(facility.name)}`,
    `no=${encodeURIComponent(facility.facilityNo)}`,
    `location=${encodeURIComponent(locationText(facility))}`,
  ].join('&')
  wx.navigateTo({ url: `/pages/inspect/inspect?${query}` })
}

/** 设施驱动巡检：扫码后直接对该设施开始巡检会话，无需提前生成任务；本人未完成的会话自动续检 */
async function startSession(facility, qrToken) {
  wx.showLoading({ title: '定位中 1/4', mask: true })
  let session
  try {
    const location = await getStableLocation((current, total) => {
      wx.showLoading({ title: `定位中 ${current}/${total}`, mask: true })
    })
    wx.showLoading({ title: '正在校验位置', mask: true })
    session = await request('/inspection/sessions', {
      method: 'POST',
      // 定位校验阈值由后端统一控制（默认100米），客户端不再放宽
      data: {
        qrToken,
        latitude: location.latitude,
        longitude: location.longitude,
        accuracyMeters: location.accuracy,
      },
    })
  } catch (error) {
    if (error && /当前定位距离设施约/.test(error.message || '')) {
      error.code = 'LOCATION_TOO_FAR'
      error.canRetryLocation = true
    }
    throw error
  } finally {
    wx.hideLoading()
  }
  navigateToInspect(facility, session)
}

async function startSessionWithRetryContext(facility, qrToken) {
  try {
    return await startSession(facility, qrToken)
  } catch (error) {
    if (error && error.canRetryLocation) {
      error.retryFacility = facility
      error.retryQrToken = qrToken
    }
    throw error
  }
}

async function resolveToken(token) {
  return request(`/qr-codes/resolve/${token}`)
}

async function startByScan() {
  const token = await scanQrToken()
  wx.showLoading({ title: '正在识别二维码', mask: true })
  let facility
  try {
    const result = await resolveToken(token)
    if (result.type === 'BLANK') {
      throw new Error('该二维码还未绑定设施（尚未建档），请联系数据采集员先完成建档')
    }
    if (result.type !== 'FACILITY' || !result.facility) {
      throw new Error(result.message || '二维码无效，请扫描张贴在设施上的巡检二维码')
    }
    facility = result.facility
  } finally {
    wx.hideLoading()
  }
  await startSessionWithRetryContext(facility, token)
  return true
}

async function retryLocation(error) {
  if (!error || !error.retryFacility || !error.retryQrToken) {
    throw new Error('本次扫码信息已失效，请重新扫描设施二维码')
  }
  return startSessionWithRetryContext(error.retryFacility, error.retryQrToken)
}

function scanRawCode() {
  return new Promise((resolve, reject) => {
    wx.scanCode({
      onlyFromCamera: true,
      scanType: ['qrCode'],
      success(res) { resolve((res.result || '').trim()) },
      fail() { reject(null) },
    })
  })
}

async function startCollectByScan() {
  const app = getApp()
  const raw = await scanRawCode()
  const token = parseQrToken(raw)
  if (!token) {
    throw new Error('二维码内容无效，请扫描管理端预生成的空白设施码')
  }
  wx.showLoading({ title: '正在识别二维码', mask: true })
  let result
  try {
    result = await resolveToken(token)
  } finally {
    wx.hideLoading()
  }
  if (result.type === 'BLANK') {
    app.globalData.editFacility = null
    app.globalData.claimToken = token
    app.globalData.claimSerial = result.serialNo
    app.globalData.claimLocation = {
      school: result.school || '',
      campus: result.campus || '',
      building: result.building || '',
      floor: result.floor || '',
      locationLabel: result.locationLabel || '',
      labelCode: result.labelCode || '',
    }
    wx.navigateTo({ url: '/pages/collect-form/collect-form' })
    return true
  }
  if (result.type === 'FACILITY' && result.facility) {
    app.globalData.editFacility = result.facility
    app.globalData.claimToken = null
    app.globalData.claimSerial = null
    app.globalData.claimLocation = null
    wx.navigateTo({ url: '/pages/collect-form/collect-form' })
    return true
  }
  throw new Error(result.message || '二维码无效，请扫描张贴在设备上的空白设施码')
}

/** 访客只读扫码：识别设施码后进入只读查看页（状态 + 历史记录），不创建巡检会话 */
async function previewByScan() {
  const app = getApp()
  const token = await scanQrToken()
  wx.showLoading({ title: '正在识别二维码', mask: true })
  let result
  try {
    result = await resolveToken(token)
  } finally {
    wx.hideLoading()
  }
  if (result.type === 'FACILITY' && result.facility) {
    app.globalData.viewFacility = result.facility
    wx.navigateTo({ url: '/pages/facility-view/facility-view' })
    return true
  }
  if (result.type === 'BLANK') {
    throw new Error('该二维码还未绑定设施（尚未建档），暂无可查看的信息')
  }
  throw new Error(result.message || '二维码无效，请扫描张贴在设施上的巡检二维码')
}

module.exports = { parseQrToken, scanQrToken, locationText, startSession, startByScan, retryLocation, startCollectByScan, previewByScan, resolveToken }
