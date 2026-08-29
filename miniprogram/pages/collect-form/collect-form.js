const app = getApp()
const { request, getLocation } = require('../../utils/api')

const blankForm = {
  facilityNo: '',
  name: '',
  campus: '主校区',
  building: '',
  floor: '',
  area: '',
  detailLocation: '',
  brand: '',
  model: '',
  specification: '',
  latitude: '',
  longitude: '',
  manufactureDate: '',
  commissionedDate: '',
}

Page({
  data: {
    largeText: false,
    types: [],
    typeIndex: -1,
    ruleHint: '',
    editingId: null,
    editingNo: '',
    claimToken: '',
    claimSerial: '',
    lifecycleStatus: 'IN_USE',
    form: Object.assign({}, blankForm),
    submitting: false,
  },
  onLoad() {
    this.setData({ largeText: app.globalData.largeText })
    const pending = app.globalData.editFacility
    const claimToken = app.globalData.claimToken
    const claimSerial = app.globalData.claimSerial
    app.globalData.editFacility = null
    app.globalData.claimToken = null
    app.globalData.claimSerial = null
    if (pending) {
      wx.setNavigationBarTitle({ title: '编辑设施档案' })
      this.setData({ claimToken: '', claimSerial: '' })
    } else {
      wx.setNavigationBarTitle({ title: '扫码新建设施档案' })
      this.setData({ claimToken: claimToken || '', claimSerial: claimSerial || '' })
    }
    this.loadTypes().then(() => {
      if (pending) this.applyFacility(pending)
    })
  },
  async loadTypes() {
    try {
      const types = await request('/facilities/types')
      this.setData({ types: types || [] })
    } catch (error) {
      wx.showToast({ title: error.message, icon: 'none' })
    }
  },
  applyFacility(f) {
    const typeIndex = this.data.types.findIndex(t => t.typeCode === f.facilityType)
    this.setData({
      editingId: f.id,
      editingNo: f.facilityNo || '',
      typeIndex: typeIndex >= 0 ? typeIndex : -1,
      lifecycleStatus: f.lifecycleStatus || 'IN_USE',
      ruleHint: '已载入该设施现有档案，请现场核对并补全信息后保存',
      form: {
        facilityNo: f.facilityNo || '',
        name: f.name || '',
        campus: f.campus || '',
        building: f.building || '',
        floor: f.floor || '',
        area: f.area || '',
        detailLocation: f.detailLocation || '',
        brand: f.brand || '',
        model: f.model || '',
        specification: f.specification || '',
        latitude: f.latitude != null ? String(f.latitude) : '',
        longitude: f.longitude != null ? String(f.longitude) : '',
        manufactureDate: f.manufactureDate || '',
        commissionedDate: f.commissionedDate || '',
      },
    })
  },
  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`form.${field}`]: e.detail.value })
  },
  onTypeChange(e) {
    const typeIndex = Number(e.detail.value)
    this.setData({ typeIndex, ruleHint: '保存后系统将按该设施类型匹配技术更新年限和保养周期' })
  },
  onManufactureDateChange(e) { this.setData({ 'form.manufactureDate': e.detail.value }) },
  onCommissionedDateChange(e) { this.setData({ 'form.commissionedDate': e.detail.value }) },
  async captureLocation() {
    try {
      const location = await getLocation()
      this.setData({
        'form.latitude': String(location.latitude),
        'form.longitude': String(location.longitude),
      })
      wx.showToast({ title: '已采集当前位置', icon: 'success' })
    } catch (error) { /* 已在 getLocation 中提示 */ }
  },
  validate() {
    const form = this.data.form
    if (this.data.typeIndex < 0) return '请选择设施类型'
    if (!this.data.editingId) {
      if (!this.data.claimToken) return '请通过扫描设备上的空白码进入建档'
      if (!String(form.facilityNo || '').trim()) return '请输入设施编号'
    }
    for (const key of ['name', 'campus', 'building', 'floor', 'area', 'detailLocation']) {
      if (!String(form[key] || '').trim()) {
        return '请完整填写带 * 的信息'
      }
    }
    if (!form.latitude || !form.longitude) {
      return '请先采集设施位置'
    }
    return ''
  },
  buildPayload() {
    const form = this.data.form
    const payload = {
      facilityNo: (form.facilityNo || '').trim(),
      facilityType: this.data.types[this.data.typeIndex].typeCode,
      name: form.name.trim(),
      campus: form.campus.trim(),
      building: form.building.trim(),
      floor: form.floor.trim(),
      area: form.area.trim(),
      detailLocation: form.detailLocation.trim(),
      latitude: Number(form.latitude),
      longitude: Number(form.longitude),
      brand: form.brand.trim() || null,
      model: form.model.trim() || null,
      specification: form.specification.trim() || null,
      lifecycleStatus: this.data.lifecycleStatus || 'IN_USE',
      manufactureDate: form.manufactureDate || null,
      commissionedDate: form.commissionedDate || null,
    }
    if (!this.data.editingId && this.data.claimToken) {
      payload.qrToken = this.data.claimToken
    }
    return payload
  },
  async submit() {
    const message = this.validate()
    if (message) return wx.showToast({ title: message, icon: 'none' })
    this.setData({ submitting: true })
    try {
      if (this.data.editingId) {
        await request(`/facilities/${this.data.editingId}`, {
          method: 'PUT',
          data: this.buildPayload(),
        })
        wx.showModal({
          title: '保存成功',
          content: `设施「${this.data.form.name}」的档案已更新。`,
          showCancel: false,
          success() { wx.navigateBack() },
        })
      } else {
        const created = await request('/facilities', {
          method: 'POST',
          data: this.buildPayload(),
        })
        wx.showModal({
          title: '建档成功',
          content: `设施「${created.name || this.data.form.name}」已建档，二维码令牌：${created.qrToken}。请在管理后台打印二维码并张贴到设施上。`,
          showCancel: false,
          success() { wx.switchTab({ url: '/pages/collect/collect' }) },
        })
      }
    } catch (error) {
      wx.showModal({ title: this.data.editingId ? '保存失败' : '建档失败', content: error.message, showCancel: false })
    } finally {
      this.setData({ submitting: false })
    }
  },
})
