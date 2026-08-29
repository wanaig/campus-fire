const app = getApp()
const { request, getLocation } = require('../../utils/api')

// 设计约束：已勾选部件的生产日期缺失时，用弹窗提示该固定文案
const COMPONENT_DATE_MESSAGE = '已勾选部件请补全生产日期'
// 参与设施名称自动拼接的位置字段
const LOCATION_FIELDS = ['campus', 'building', 'floor', 'area', 'detailLocation']

/** 设施名称不再单独填写：由【校区-楼栋-楼层-区域-详细位置】拼接并追加设施类型名 */
function joinAutoName(form, typeName) {
  const location = LOCATION_FIELDS
    .map(field => String(form[field] || '').trim())
    .filter(Boolean)
    .join('-')
  return location ? (typeName ? `${location} ${typeName}` : location) : ''
}

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
    claimLocation: null,
    claimLabel: '',
    labelLocked: false,
    componentList: [],
    lifecycleStatus: 'IN_USE',
    form: Object.assign({}, blankForm),
    submitting: false,
  },
  onLoad() {
    this.setData({ largeText: app.globalData.largeText })
    const pending = app.globalData.editFacility
    const claimToken = app.globalData.claimToken
    const claimSerial = app.globalData.claimSerial
    const claimLocation = app.globalData.claimLocation
    app.globalData.editFacility = null
    app.globalData.claimToken = null
    app.globalData.claimSerial = null
    app.globalData.claimLocation = null
    if (pending) {
      wx.setNavigationBarTitle({ title: '编辑设施档案' })
      this.setData({ claimToken: '', claimSerial: '', claimLocation: null, claimLabel: '', labelLocked: false })
    } else {
      wx.setNavigationBarTitle({ title: '扫码新建设施档案' })
      const labelLocked = Boolean(claimLocation && claimLocation.building && claimLocation.floor)
      const update = {
        claimToken: claimToken || '',
        claimSerial: claimSerial || '',
        claimLocation: claimLocation || null,
        claimLabel: (claimLocation && claimLocation.labelCode) || (claimSerial ? `NO.${claimSerial}` : ''),
        labelLocked,
      }
      if (labelLocked) {
        // 校区、楼栋和楼层来自二维码标签，自定义名称作为区域初始值带入。
        update.form = Object.assign({}, blankForm, {
          campus: claimLocation.campus || '',
          building: claimLocation.building,
          floor: claimLocation.floor,
          area: claimLocation.locationLabel || '',
        })
      }
      this.setData(update)
    }
    this.loadTypes().then(async () => {
      if (pending) await this.applyFacility(pending)
      this.refreshAutoName()
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
  /** 按设施类型拉取检查项作为部件清单；新建档时全部不默认勾选，由采集员对照现场勾选，编辑时按档案回显 */
  async loadComponents(typeCode, saved) {
    if (!typeCode) {
      this.setData({ componentList: [] })
      return
    }
    try {
      const items = await request('/inspection-items', { data: { facilityType: typeCode } })
      const savedMap = {}
      ;(saved || []).forEach(component => { savedMap[component.itemCode] = component })
      const hasSaved = Array.isArray(saved) && saved.length > 0
      const componentList = (items || []).filter(item => item.enabled).map(item => ({
        itemCode: item.item_code,
        itemName: item.item_name,
        checked: hasSaved ? Boolean(savedMap[item.item_code]) : false,
        manufactureDate: (savedMap[item.item_code] && savedMap[item.item_code].manufactureDate) || '',
      }))
      this.setData({ componentList })
    } catch (error) {
      this.setData({ componentList: [] })
    }
  },
  async applyFacility(f) {
    const typeIndex = this.data.types.findIndex(t => t.typeCode === f.facilityType)
    const typeName = typeIndex >= 0 && this.data.types[typeIndex] ? this.data.types[typeIndex].typeName : ''
    this.setData({
      editingId: f.id,
      editingNo: f.facilityNo || '',
      typeIndex: typeIndex >= 0 ? typeIndex : -1,
      lifecycleStatus: f.lifecycleStatus || 'IN_USE',
      ruleHint: '已载入该设施现有档案，请现场核对并补全信息后保存',
      form: {
        facilityNo: f.facilityNo || '',
        name: joinAutoName(f, typeName),
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
    await this.loadComponents(f.facilityType, f.components)
  },
  refreshAutoName() {
    const type = this.data.types[this.data.typeIndex]
    this.setData({ 'form.name': joinAutoName(this.data.form, type ? type.typeName : '') })
  },
  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`form.${field}`]: e.detail.value })
    if (LOCATION_FIELDS.indexOf(field) >= 0) this.refreshAutoName()
  },
  async onTypeChange(e) {
    const typeIndex = Number(e.detail.value)
    this.setData({ typeIndex, ruleHint: '保存后系统将按该设施类型匹配技术更新年限和保养周期' })
    await this.loadComponents(this.data.types[typeIndex] ? this.data.types[typeIndex].typeCode : '')
    this.refreshAutoName()
  },
  onComponentToggle(e) {
    const code = e.currentTarget.dataset.code
    const componentList = this.data.componentList.map(item =>
      item.itemCode === code ? Object.assign({}, item, { checked: !item.checked }) : item)
    this.setData({ componentList })
  },
  onComponentDateChange(e) {
    const code = e.currentTarget.dataset.code
    const componentList = this.data.componentList.map(item => {
      if (item.itemCode !== code || !item.checked) return item // 未勾选时日期控件置灰，兜底忽略选择
      return Object.assign({}, item, { manufactureDate: e.detail.value })
    })
    this.setData({ componentList })
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
    if (!this.data.editingId && !this.data.claimToken) return '请通过扫描设备上的空白码进入建档'
    for (const key of LOCATION_FIELDS) {
      if (!String(form[key] || '').trim()) {
        return '请完整填写带 * 的信息'
      }
    }
    if (!form.latitude || !form.longitude) {
      return '请先采集设施位置'
    }
    if (!this.data.componentList.some(component => component.checked)) {
      return '请至少勾选一个该位置包含的部件'
    }
    if (this.data.componentList.some(component => component.checked && !component.manufactureDate)) {
      return COMPONENT_DATE_MESSAGE
    }
    return ''
  },
  buildPayload() {
    const form = this.data.form
    const payload = {
      // 设施编号由后端生成：编辑时原样回传，新建时传空值由后端自动生成
      facilityNo: this.data.editingId ? (form.facilityNo || '').trim() : '',
      facilityType: this.data.types[this.data.typeIndex].typeCode,
      name: (form.name || '').trim(),
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
      components: this.data.componentList
        .filter(component => component.checked)
        .map(component => ({ itemCode: component.itemCode, manufactureDate: component.manufactureDate })),
    }
    if (!this.data.editingId && this.data.claimToken) {
      payload.qrToken = this.data.claimToken
    }
    return payload
  },
  async submit() {
    const message = this.validate()
    if (message) {
      if (message === COMPONENT_DATE_MESSAGE) {
        return wx.showModal({ title: '提示', content: message, showCancel: false })
      }
      return wx.showToast({ title: message, icon: 'none' })
    }
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
          content: this.data.claimToken
            ? `设施「${created.name || this.data.form.name}」已建档，系统编号 ${created.facilityNo}，设备上的二维码已绑定生效。`
            : `设施「${created.name || this.data.form.name}」已建档，系统编号：${created.facilityNo}。请在管理后台打印二维码并张贴到设施上。`,
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
