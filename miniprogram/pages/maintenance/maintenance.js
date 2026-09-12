const app = getApp()
const { request } = require('../../utils/api')
const { scanQrToken, resolveToken, locationText } = require('../../utils/inspection-flow')

Page({
  data: {
    largeText: false,
    mode: 'due',
    facility: null,
    components: [],
    overdueCount: 0,
    loading: true,
    error: '',
    form: null,
    submitting: false,
  },

  onLoad(query) {
    if (query.facilityId) {
      this.enterFacility({ id: Number(query.facilityId), name: decodeURIComponent(query.name || ''), facilityNo: decodeURIComponent(query.no || ''), location: decodeURIComponent(query.location || '') })
    }
  },

  onShow() {
    if (!app.isLoggedIn()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    const user = app.globalData.user
    if (user && user.roleCode !== 'GUARD') {
      wx.reLaunch({ url: app.homePath(user.roleCode) })
      return
    }
    this.setData({ largeText: app.globalData.largeText })
    if (this.data.mode === 'due' && !this.data.facility) this.loadDue()
  },

  /** 展示字段预格式化：位置拼接、日期截断（WXML 不支持 join/slice） */
  decorate(rows) {
    return (rows || []).map(item => Object.assign({}, item, {
      locationText: [item.campus, item.building, item.floor, item.area].filter(Boolean).join(' / '),
      next_due_at: item.next_due_at ? String(item.next_due_at).slice(0, 10) : '',
      last_maintained_at: item.last_maintained_at ? String(item.last_maintained_at).slice(0, 10) : '',
    }))
  },

  onPullDownRefresh() {
    this.refresh().finally(() => wx.stopPullDownRefresh())
  },

  refresh() {
    if (this.data.facility) return this.loadFacility(this.data.facility.id)
    return this.loadDue()
  },

  async loadDue() {
    this.setData({ loading: true, error: '' })
    try {
      const rows = await request('/maintenance/components/due')
      const decorated = this.decorate(rows)
      const overdueCount = decorated.filter(item => item.dueStatus === 'OVERDUE').length
      this.setData({ components: decorated, overdueCount, mode: 'due' })
    } catch (cause) {
      this.setData({ error: cause.message || '读取保养提醒失败' })
    } finally {
      this.setData({ loading: false })
    }
  },

  async loadFacility(facilityId) {
    this.setData({ loading: true, error: '' })
    try {
      const rows = await request(`/maintenance/components/facilities/${facilityId}`)
      this.setData({ components: this.decorate(rows) })
    } catch (cause) {
      this.setData({ error: cause.message || '读取部件保养状态失败' })
    } finally {
      this.setData({ loading: false })
    }
  },

  enterFacility(facility) {
    this.setData({ facility })
    this.loadFacility(facility.id)
  },

  /** 扫码进入部件保养：识别设施码后展示该设施部件的到期状态 */
  async scanToMaintain() {
    let token
    try {
      token = await scanQrToken()
    } catch (error) {
      if (error && error.message) wx.showModal({ title: '扫码失败', content: error.message, showCancel: false })
      return
    }
    wx.showLoading({ title: '正在识别二维码', mask: true })
    let result
    try {
      result = await resolveToken(token)
    } catch (cause) {
      wx.hideLoading()
      wx.showModal({ title: '识别失败', content: cause.message || '二维码无效', showCancel: false })
      return
    } finally {
      wx.hideLoading()
    }
    if (result.type === 'BLANK') {
      wx.showModal({ title: '无法保养', content: '该二维码还未绑定设施（尚未建档）', showCancel: false })
      return
    }
    if (result.type !== 'FACILITY' || !result.facility) {
      wx.showModal({ title: '无法保养', content: result.message || '二维码无效', showCancel: false })
      return
    }
    const facility = result.facility
    this.setData({ facility: { id: facility.id, name: facility.name, facilityNo: facility.facilityNo, location: locationText(facility) } })
    this.loadFacility(facility.id)
  },

  backToDue() {
    this.setData({ facility: null })
    this.loadDue()
  },

  openForm(event) {
    const component = this.data.components.find(item => item.component_id === Number(event.currentTarget.dataset.id))
    if (!component) return
    if (component.dueStatus === 'NONE') {
      wx.showToast({ title: '该部件未设置保养周期', icon: 'none' })
      return
    }
    if (component.dueStatus === 'NORMAL') {
      wx.showToast({ title: '尚未到保养时间', icon: 'none' })
      return
    }
    this.setData({ form: { component, maintenanceType: 'MAINTAIN', resultNote: '', newManufactureDate: '' } })
  },

  closeForm() {
    if (this.data.submitting) return
    this.setData({ form: null })
  },

  // 阻止表单区域点击冒泡到遮罩层，避免选择类型或提交时误触发关闭
  stop() {},

  onTypeChange(event) {
    this.setData({ 'form.maintenanceType': event.currentTarget.dataset.type })
  },

  onNoteInput(event) {
    this.setData({ 'form.resultNote': event.detail.value })
  },

  onDateInput(event) {
    this.setData({ 'form.newManufactureDate': event.detail.value })
  },

  async submit() {
    const form = this.data.form
    if (!form) return
    if (!form.resultNote.trim()) {
      wx.showToast({ title: '请填写保养说明', icon: 'none' })
      return
    }
    if (form.maintenanceType === 'REPLACE' && !form.newManufactureDate) {
      wx.showToast({ title: '更换部件请填写新部件生产日期', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    try {
      await request('/maintenance/components', {
        method: 'POST',
        data: {
          componentId: form.component.component_id,
          maintenanceType: form.maintenanceType,
          resultNote: form.resultNote.trim(),
          newManufactureDate: form.maintenanceType === 'REPLACE' && form.newManufactureDate ? form.newManufactureDate : null,
        },
      })
      wx.showToast({ title: '保养已完成', icon: 'success' })
      this.setData({ form: null })
      this.refresh()
    } catch (cause) {
      wx.showModal({ title: '保养提交失败', content: cause.message || '请稍后重试', showCancel: false })
    } finally {
      this.setData({ submitting: false })
    }
  },
})
