const app = getApp()
const { request } = require('../../utils/api')
const { previewRecordPhotos, previewFacilityPhotos } = require('../../utils/photos')

// 与 tasks 页保持一致的提醒口径：超过 30 天（1 个月）未巡检即标记超期
const OVERDUE_DAYS = 30
const lifecycleText = { IN_USE: '在用', SUSPENDED: '停用', RETIRED: '退役', SCRAPPED: '报废' }
const lifecycleBadge = { IN_USE: 'ok', SUSPENDED: 'warn', RETIRED: 'info', SCRAPPED: 'danger' }

Page({
  data: {
    largeText: false,
    visitor: false,
    facility: null,
    locationText: '',
    lifecycleLabel: '',
    lifecycleBadge: '',
    state: 'NEVER',
    stateLabel: '从未巡检',
    stateBadge: 'warn',
    lastInspectedText: '',
    records: [],
    facilityPhotoCount: 0,
    loading: true,
    error: '',
  },
  onShow() {
    const facility = app.globalData.viewFacility
    if (!facility) {
      wx.navigateBack()
      return
    }
    this.setData({
      largeText: app.globalData.largeText,
      visitor: !app.isLoggedIn(),
      facility,
      locationText: [facility.campus, facility.building, facility.floor, facility.area].filter(Boolean).join(' / '),
      lifecycleLabel: lifecycleText[facility.lifecycleStatus] || facility.lifecycleStatus || '',
      lifecycleBadge: lifecycleBadge[facility.lifecycleStatus] || 'info',
    })
    this.load()
  },
  onPullDownRefresh() {
    this.load().finally(() => wx.stopPullDownRefresh())
  },
  async load() {
    this.setData({ loading: true, error: '' })
    try {
      // 记录接口免登录开放；巡检状态按最近一次提交时间与 30 天规则在本地推导
      const records = await request('/inspection/records', { data: { facilityNo: this.data.facility.facilityNo } })
      // 采集员建档初始照片计数（接口只读公开）：有 id 才拉取，兼容不带 id 的旧入口
      if (this.data.facility.id != null) {
        request(`/facilities/${this.data.facility.id}/photos`)
          .then(list => this.setData({ facilityPhotoCount: (list || []).length }))
          .catch(() => {})
      }
      const shaped = (records || []).slice(0, 20).map(record => {
        let chips = Array.isArray(record.results) ? record.results : []
        if (!chips.length) {
          try {
            const raw = typeof record.results_json === 'string' ? JSON.parse(record.results_json) : record.results_json
            if (raw && !Array.isArray(raw)) {
              chips = Object.entries(raw).map(([code, status]) => ({ code, name: code, status }))
            }
          } catch (_) {}
        }
        const abnormal = chips.filter(chip => String(chip.status).toUpperCase() === 'FAIL')
        return {
          id: record.id,
          timeText: String(record.submitted_at || '').replace('T', ' ').slice(0, 16),
          inspector: record.inspector || '—',
          photoCount: Number(record.photo_count) || 0,
          voidReason: record.void_reason || '',
          note: record.void_reason ? `作废原因：${record.void_reason}` : (record.note || (abnormal.length ? `异常部件：${abnormal.map(chip => chip.name).join('、')}` : '')),
          abnormalCount: abnormal.length,
          chips,
        }
      })
      const latest = (records || [])[0]
      const lastInspectedText = latest && latest.submitted_at ? String(latest.submitted_at).replace('T', ' ').slice(0, 16) : ''
      const daysSince = latest && latest.submitted_at
        ? Math.floor((Date.now() - new Date(latest.submitted_at).getTime()) / 86400000)
        : null
      let state = 'NEVER'
      if (daysSince !== null) state = daysSince > OVERDUE_DAYS ? 'OVERDUE' : 'NORMAL'
      this.setData({
        records: shaped,
        state,
        stateLabel: state === 'NORMAL' ? `${OVERDUE_DAYS}天内已巡检` : state === 'OVERDUE' ? '已超期' : '从未巡检',
        stateBadge: state === 'NORMAL' ? 'ok' : state === 'OVERDUE' ? 'danger' : 'warn',
        lastInspectedText,
        loading: false,
      })
    } catch (error) {
      this.setData({ error: error.message, records: [], loading: false })
    }
  },
  /** 预览该条记录的现场照片（照片接口只读公开，访客也可查看） */
  viewRecordPhotos(e) {
    const { index } = e.currentTarget.dataset
    previewRecordPhotos(this.data.records[index])
  },
  /** 预览采集员建档时拍摄的初始照片（接口只读公开，访客也可查看） */
  viewFacilityPhotos() {
    if (!this.data.facility || this.data.facility.id == null) return
    previewFacilityPhotos(this.data.facility.id)
  },
})
