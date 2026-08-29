const { request, getLocation } = require('./api')

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

function locationText(task) {
  return [task.campus, task.building, task.floor, task.area, task.detail_location].filter(Boolean).join(' / ')
}

function navigateToInspect(task, session) {
  const query = [
    `sessionId=${session.sessionId}`,
    `taskId=${task.id}`,
    `facilityType=${task.facility_type || 'OTHER'}`,
    `name=${encodeURIComponent(task.name)}`,
    `no=${encodeURIComponent(task.facility_no)}`,
    `location=${encodeURIComponent(locationText(task))}`,
  ].join('&')
  wx.navigateTo({ url: `/pages/inspect/inspect?${query}` })
}

async function startSession(task, qrToken) {
  const app = getApp()
  const location = await getLocation()
  wx.showLoading({ title: '正在开始', mask: true })
  let session
  try {
    session = await request('/inspection/sessions', {
      method: 'POST',
      data: {
        taskId: task.id,
        qrToken,
        latitude: location.latitude,
        longitude: location.longitude,
        allowedDistanceMeters: app.allowedDistanceMeters(),
      },
    })
  } finally {
    wx.hideLoading()
  }
  navigateToInspect(task, session)
}

async function resolveToken(token) {
  return request(`/qr-codes/resolve/${token}`)
}

async function startByScan() {
  const token = await scanQrToken()
  wx.showLoading({ title: '正在匹配任务', mask: true })
  let task
  try {
    const result = await resolveToken(token)
    if (result.type === 'BLANK') {
      throw new Error('该二维码还未绑定设施（尚未建档），请联系数据采集员先完成建档')
    }
    if (result.type !== 'FACILITY' || !result.facility) {
      throw new Error(result.message || '二维码无效，请扫描张贴在设施上的巡检二维码')
    }
    task = await resolveTaskByFacility(result.facility)
  } finally {
    wx.hideLoading()
  }
  await startSession(task, token)
  return true
}

async function resolveTaskByFacility(facility) {
  const tasks = await request('/inspection/tasks', { data: {} })
  const pending = (tasks || [])
    .filter(t => t.facility_id === facility.id && t.status !== 'COMPLETED')
    .sort((a, b) => String(a.due_date || '').localeCompare(String(b.due_date || '')))
  if (!pending.length) {
    throw new Error(`「${facility.name}」当前没有待巡检任务`)
  }
  return pending[0]
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

module.exports = { parseQrToken, scanQrToken, locationText, startSession, startByScan, startCollectByScan }
