const app = getApp()
const { request, getLocation, uploadFacilityPhoto } = require('../../utils/api')
const { downloadFacilityPhoto } = require('../../utils/photos')

// 参与设施名称自动拼接的位置字段
const LOCATION_FIELDS = ['campus', 'building', 'floor', 'area', 'detailLocation']
// 采集端必填的位置字段：详细位置已从采集表单移除，编辑旧档案时仅在后台保留原值随名称拼接
const REQUIRED_LOCATION_FIELDS = ['campus', 'building', 'floor', 'area']
// 系统当前仅保留室内消火栓一种设施类型，新建档时默认选中它
const DEFAULT_TYPE_CODE = 'FIRE_HYDRANT'

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
  locationAccuracyMeters: '',
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
    photos: [],
    removedPhotoIds: [],
    maxPhotos: 6,
    photoCardTitle: '设施初始照片（至少1张，最多6张）',
    submitting: false,
  },
  onLoad() {
    // 建档表单仅采集员可用：其他角色直接送回各自首页
    const user = app.globalData.user
    if (!user || user.roleCode !== 'COLLECTOR') {
      wx.reLaunch({ url: user ? app.homePath(user.roleCode) : '/pages/login/login' })
      return
    }
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
      this.setData({ claimToken: '', claimSerial: '', claimLocation: null, claimLabel: '', labelLocked: false, photoCardTitle: `设施初始照片（最多${this.data.maxPhotos}张）` })
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
      else await this.applyDefaultType()
      this.refreshAutoName()
    })
  },
  /** 新建档时默认选中室内消火栓（类型列表缺失该编码时回退第一个），并预载其部件清单 */
  async applyDefaultType() {
    const types = this.data.types
    if (!types.length) return
    const preferred = types.findIndex(t => t.typeCode === DEFAULT_TYPE_CODE)
    const typeIndex = preferred >= 0 ? preferred : 0
    this.setData({ typeIndex, ruleHint: '保存后系统将按该设施类型匹配技术更新年限和保养周期' })
    await this.loadComponents(types[typeIndex].typeCode)
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
    // 列表接口不返回部件明细（components 为 null，仅给 componentCount）：编辑时补拉详情，保证部件勾选与生产日期回显
    if (!Array.isArray(f.components) && f.id != null) {
      try { f = await request('/facilities/' + f.id) } catch (error) { /* 详情拉取失败时按列表数据回填表单字段 */ }
    }
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
        locationAccuracyMeters: f.locationAccuracyMeters != null ? String(f.locationAccuracyMeters) : '',
        manufactureDate: f.manufactureDate || '',
        commissionedDate: f.commissionedDate || '',
      },
    })
    await this.loadComponents(f.facilityType, f.components)
    // 已有初始照片异步拉取回显：失败不阻断表单编辑
    this.loadFacilityPhotos(f.id)
  },
  /** 编辑档案时回显服务端已有的初始照片（下载为本地临时文件用于缩略图展示与删除标记） */
  async loadFacilityPhotos(facilityId) {
    if (!facilityId) return
    try {
      const list = await request(`/facilities/${facilityId}/photos`)
      const photos = []
      for (const photo of (list || []).slice(0, this.data.maxPhotos)) {
        const path = await downloadFacilityPhoto(photo.photoId)
        if (path) photos.push({ key: 'server-' + photo.photoId, photoId: photo.photoId, path, existing: true })
      }
      // 拉取期间用户可能已离开页面或已自行拍照：以当前列表为基础合并回显
      const current = this.data.photos.filter(item => !item.existing)
      this.setData({ photos: photos.concat(current) })
    } catch (error) { /* 照片清单拉取失败时按无照片处理，保存档案不受影响 */ }
  },
  /** 现场拍摄设施初始照片：仅允许相机拍摄，拍摄时记录定位，随保存档案一起上传 */
  async takeFacilityPhoto() {
    if (this.data.photos.length >= this.data.maxPhotos) {
      wx.showToast({ title: `最多拍摄${this.data.maxPhotos}张设施照片`, icon: 'none' })
      return
    }
    try {
      const choice = await new Promise((resolve, reject) => {
        wx.chooseMedia({
          count: this.data.maxPhotos - this.data.photos.length,
          mediaType: ['image'],
          sourceType: ['camera'],
          sizeType: ['compressed'],
          success: resolve,
          fail: () => reject(null),
        })
      })
      if (!choice || !choice.tempFiles || !choice.tempFiles.length) return
      // 照片定位在拍摄现场采集：上传时服务端用它和设施坐标做距离校验
      const location = await getLocation()
      const taken = choice.tempFiles.map(file => ({
        key: 'local-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8),
        path: file.tempFilePath,
        photoId: null,
        existing: false,
        latitude: location.latitude,
        longitude: location.longitude,
        accuracy: location.accuracy,
      }))
      this.setData({ photos: this.data.photos.concat(taken) })
      wx.vibrateShort({ type: 'light' })
    } catch (error) { /* 定位失败已在 getLocation 中提示 */ }
  },
  /** 从工作列表移除照片：新拍照片仅本地移除；已有照片记录 id，保存档案时在服务端删除 */
  removePhoto(e) {
    const index = e.currentTarget.dataset.index
    const photo = this.data.photos[index]
    if (!photo) return
    const photos = this.data.photos.slice()
    photos.splice(index, 1)
    const removedPhotoIds = photo.existing && photo.photoId
      ? this.data.removedPhotoIds.concat(photo.photoId)
      : this.data.removedPhotoIds
    this.setData({ photos, removedPhotoIds })
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
  async captureLocation() {
    try {
      const location = await getLocation()
      this.setData({
        'form.latitude': String(location.latitude),
        'form.longitude': String(location.longitude),
        'form.locationAccuracyMeters': location.accuracy != null ? String(location.accuracy) : '',
      })
      wx.showToast({ title: '已采集当前位置', icon: 'success' })
    } catch (error) { /* 已在 getLocation 中提示 */ }
  },
  validate() {
    const form = this.data.form
    if (this.data.typeIndex < 0) return '请选择设施类型'
    if (!this.data.editingId && !this.data.claimToken) return '请通过扫描设备上的空白码进入建档'
    for (const key of REQUIRED_LOCATION_FIELDS) {
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
    // 新建档必须拍摄初始照片留痕；编辑已有档案时可补拍但不强制
    if (!this.data.editingId && !this.data.photos.length) {
      return '请至少拍摄1张设施初始照片留痕'
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
      locationAccuracyMeters: form.locationAccuracyMeters ? Number(form.locationAccuracyMeters) : null,
      brand: form.brand.trim() || null,
      model: form.model.trim() || null,
      specification: form.specification.trim() || null,
      lifecycleStatus: this.data.lifecycleStatus || 'IN_USE',
      manufactureDate: form.manufactureDate || null,
      commissionedDate: form.commissionedDate || null,
      components: this.data.componentList
        .filter(component => component.checked)
        // 生产日期选填：未填写传 null，避免空字符串触发后端日期解析失败
        .map(component => ({ itemCode: component.itemCode, manufactureDate: component.manufactureDate || null })),
    }
    if (!this.data.editingId && this.data.claimToken) {
      payload.qrToken = this.data.claimToken
    }
    return payload
  },
  async submit() {
    const message = this.validate()
    if (message) {
      return wx.showToast({ title: message, icon: 'none' })
    }
    this.setData({ submitting: true })
    try {
      let facilityId = this.data.editingId
      let created = null
      if (this.data.editingId) {
        await request(`/facilities/${facilityId}`, {
          method: 'PUT',
          data: this.buildPayload(),
        })
      } else {
        created = await request('/facilities', {
          method: 'POST',
          data: this.buildPayload(),
        })
        facilityId = created.id
      }
      // 档案保存成功后再处理照片：上传新拍照片、删除已移除的旧照片。
      // 先保存后上传可保证照片定位校验用的是刚更新的设施坐标。
      const photoProblem = await this.syncPhotos(facilityId)
      if (photoProblem) {
        wx.showModal({
          title: this.data.editingId ? '档案已保存，照片未同步' : '建档成功，照片未上传',
          content: `${photoProblem}。档案本身已保存成功，可在列表中重新编辑该档案补拍照片。`,
          showCancel: false,
          success() { wx.navigateBack() },
        })
        return
      }
      if (this.data.editingId) {
        wx.showModal({
          title: '保存成功',
          content: `设施「${this.data.form.name}」的档案已更新。`,
          showCancel: false,
          success() { wx.navigateBack() },
        })
      } else {
        const photoNote = this.data.photos.length ? `已保存初始照片 ${this.data.photos.length} 张。` : ''
        const facilityName = (created && created.name) || this.data.form.name
        const facilityNo = created ? created.facilityNo : ''
        wx.showModal({
          title: '建档成功',
          content: this.data.claimToken
            ? `设施「${facilityName}」已建档，系统编号 ${facilityNo}，设备上的二维码已绑定生效。${photoNote}`
            : `设施「${facilityName}」已建档，系统编号：${facilityNo}。请在管理后台打印二维码并张贴到设施上。${photoNote}`,
          showCancel: false,
          success() { wx.switchTab({ url: '/pages/collect/collect' }) },
        })
      }
    } catch (error) {
      wx.hideLoading()
      wx.showModal({ title: this.data.editingId ? '保存失败' : '建档失败', content: error.message, showCancel: false })
    } finally {
      this.setData({ submitting: false })
    }
  },
  /** 保存档案后的照片同步：上传新拍照片、删除列表中移除的旧照片；返回错误信息（全部成功返回空串） */
  async syncPhotos(facilityId) {
    const pending = this.data.photos.filter(photo => !photo.existing)
    if (pending.length) {
      wx.showLoading({ title: '正在上传照片', mask: true })
      for (const photo of pending) {
        try {
          const result = await uploadFacilityPhoto(facilityId, photo.path, photo.latitude, photo.longitude, photo.accuracy)
          photo.photoId = result.photoId
        } catch (error) {
          wx.hideLoading()
          return error.message
        }
      }
      wx.hideLoading()
    }
    for (const photoId of this.data.removedPhotoIds) {
      try {
        await request(`/facility-photos/${photoId}`, { method: 'DELETE' })
      } catch (error) {
        return `部分旧照片删除失败：${error.message}`
      }
    }
    return ''
  },
})
