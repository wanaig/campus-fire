const app = getApp()
const { request, uploadPhoto, getLocation } = require('../../utils/api')

Page({
  data: {
    largeText: false,
    sessionId: '',
    facilityName: '',
    facilityNo: '',
    locationText: '',
    inspectorName: '',
    inspectionTitle: '消防设施巡检记录',
    items: [],
    results: {},
    completedCount: 0,
    note: '',
    prompts: [],
    promptText: '',
    photos: [],
    maxPhotos: 6,
    loading: true,
    error: '',
    submitting: false,
    savingDraft: false,
    uploadingDraft: false,
    offlineDraft: false,
  },
  onLoad(query) {
    // 巡检执行页仅保安可用：其他角色直接送回各自首页
    const user = app.globalData.user
    if (!user || user.roleCode !== 'GUARD') {
      wx.reLaunch({ url: user ? app.homePath(user.roleCode) : '/pages/login/login' })
      return
    }
    this.setData({
      sessionId: query.sessionId || '',
      facilityName: decodeURIComponent(query.name || '消防设施'),
      facilityNo: decodeURIComponent(query.no || ''),
      locationText: decodeURIComponent(query.location || ''),
      largeText: app.globalData.largeText,
      facilityType: query.facilityType || 'FIRE_HYDRANT',
      inspectionTitle: (query.facilityType || 'FIRE_HYDRANT') === 'FIRE_HYDRANT' ? '室内消火栓巡检记录' : '消防设施巡检记录',
      inspectorName: (app.globalData.user && app.globalData.user.displayName) || '',
    })
    this.load()
  },
  async load() {
    this.setData({ loading: true, error: '' })
    try {
      // 部件清单来自该设施档案登记的部件（与采集员建档勾选一致），由后端保证
      const [items, prompts, draft] = await Promise.all([
        request(`/inspection/sessions/${this.data.sessionId}/components`),
        request(`/inspection/sessions/${this.data.sessionId}/photo-prompts`).catch(() => ({ prompts: [] })),
        request(`/inspection/sessions/${this.data.sessionId}/draft`).catch(() => null),
      ])
      // 后端返回的部件已是「档案部件 ∪ 类型启用检查项」且全部必检（required_flag），
      // 接口没有 enabled 字段，这里不能再按 enabled 过滤，否则清单会被清空
      const promptList = (prompts && prompts.prompts) || []
      const update = {
        items: items || [],
        prompts: promptList,
        // WXML 绑定不支持方法调用，提示文案在 JS 里先拼好
        promptText: promptList.join('、'),
        // 无历史草稿时也必须是空对象，否则下方按部件统计进度会读到 undefined
        results: {},
        note: '',
        loading: false,
      }
      if (draft && draft.results) {
        update.results = draft.results
        update.note = draft.note || ''
      }
      const local = wx.getStorageSync('offlineDraft:' + this.data.sessionId)
      if (local) { update.results = local.results || update.results; update.note = local.note || update.note; update.offlineDraft = true }
      update.completedCount = (update.items || []).filter(item => update.results[item.item_code]).length
      this.setData(update)
    } catch (error) {
      this.setData({ error: error.message, loading: false })
    }
  },
  setResult(e) {
    const { code, value } = e.currentTarget.dataset
    const item = this.data.items.find(i => i.item_code === code)
    const name = item ? item.item_name : '该部件'
    wx.showModal({
      title: '请再次确认',
      content: value === 'FAIL'
        ? `请再次确认「${name}」是否存在异常?确认后将登记为“异常”。`
        : `请再次确认「${name}」现场是否正常?确认后将登记为“正常”。`,
      confirmText: '确认',
      cancelText: '取消',
      // 确认按钮配色与部件选中态一致：异常红色警示、正常绿色
      confirmColor: value === 'FAIL' ? '#b42318' : '#246b45',
      success: (res) => {
        if (!res.confirm) return
        const results = Object.assign({}, this.data.results, { [code]: value })
        const completedCount = this.data.items.filter(i => results[i.item_code]).length
        this.setData({ results, completedCount })
        wx.vibrateShort({ type: value === 'FAIL' ? 'heavy' : 'light' })
      },
    })
  },
  onNote(e) {
    this.setData({ note: e.detail.value })
  },
  async takePhoto() {
    if (this.data.photos.length >= this.data.maxPhotos) {
      wx.showToast({ title: `最多拍摄${this.data.maxPhotos}张现场照片`, icon: 'none' })
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
      wx.showLoading({ title: '正在上传照片', mask: true })
      const location = await getLocation()
      const uploaded = []
      for (const file of choice.tempFiles) {
        const result = await uploadPhoto(this.data.sessionId, file.tempFilePath,
          location.latitude, location.longitude, location.accuracy)
        uploaded.push({ path: file.tempFilePath, photoId: result.photoId })
      }
      this.setData({ photos: this.data.photos.concat(uploaded) })
      wx.vibrateShort({ type: 'light' })
      wx.hideLoading()
      wx.showToast({ title: '照片已上传', icon: 'success' })
    } catch (error) {
      wx.hideLoading()
      if (error && error.message) {
        wx.showModal({ title: '照片上传失败', content: error.message, showCancel: false })
      }
    }
  },
  removePhoto(e) {
    const photos = this.data.photos.slice()
    photos.splice(e.currentTarget.dataset.index, 1)
    this.setData({ photos })
  },
  validate() {
    // 档案登记的每个部件都必须给出 正常/异常 结论，不能遗漏
    for (const item of this.data.items) {
      if (!this.data.results[item.item_code]) {
        return `请完成部件「${item.item_name}」的检查确认`
      }
    }
    const hasAbnormal = Object.values(this.data.results).some(value => value === 'FAIL')
    if (hasAbnormal && !this.data.note.trim()) {
      return '存在异常部件，请在备注中说明异常情况'
    }
    if (!this.data.photos.length) {
      return '请至少拍摄1张现场照片留痕'
    }
    return ''
  },
  async saveDraft(silent) {
    const data = {
      results: this.data.results,
      note: this.data.note,
    }
    try {
      await request(`/inspection/sessions/${this.data.sessionId}/draft`, { method: 'POST', data })
      wx.removeStorageSync('offlineDraft:' + this.data.sessionId)
      this.setData({ offlineDraft: false })
    } catch (error) {
      wx.setStorageSync('offlineDraft:' + this.data.sessionId, data)
      this.setData({ offlineDraft: true })
      if (!silent) wx.showToast({ title: '网络异常，已保存本地草稿', icon: 'none' })
      if (silent) throw error
      return false
    }
    if (!silent) wx.showToast({ title: '草稿已保存', icon: 'success' })
    return true
  },
  async onSaveDraft() {
    const unanswered = this.data.items.filter(item => !this.data.results[item.item_code]).length
    if (unanswered === this.data.items.length) {
      wx.showToast({ title: '请先填写检查项', icon: 'none' })
      return
    }
    this.setData({ savingDraft: true })
    try {
      await this.saveDraft(false)
    } catch (error) {
      wx.showModal({ title: '保存失败', content: error.message, showCancel: false })
    } finally {
      this.setData({ savingDraft: false })
    }
  },
  async retryDraft() {
    if (!this.data.offlineDraft) return
    this.setData({ uploadingDraft: true })
    try { await this.saveDraft(false) } catch (error) { wx.showToast({ title: error.message, icon: 'none' }) } finally { this.setData({ uploadingDraft: false }) }
  },
  async onSubmit() {
    if (this.data.submitting) return
    const invalid = this.validate()
    if (invalid) {
      wx.showModal({ title: '还不能提交', content: invalid, showCancel: false })
      return
    }
    const confirm = await new Promise(resolve => {
      wx.showModal({
        title: '确认提交巡检结果',
        content: '提交后将形成正式巡检记录，记录巡检人和各部件检查结论，不能自行修改或删除。异常部件会自动生成整改单。',
        success: res => resolve(res.confirm),
        fail: () => resolve(false),
      })
    })
    if (!confirm) return
    this.setData({ submitting: true })
    try {
      wx.showLoading({ title: '正在提交', mask: true })
      await this.saveDraft(true)
      const record = await request(`/inspection/sessions/${this.data.sessionId}/submit`, { method: 'POST' })
      wx.hideLoading()
      wx.showModal({
        title: '提交成功',
        content: `巡检记录已生成${record && record.photoCount ? `，留痕照片 ${record.photoCount} 张` : ''}。管理员数据看板已同步更新。`,
        showCancel: false,
        success: () => wx.reLaunch({ url: '/pages/tasks/tasks' }),
      })
    } catch (error) {
      wx.hideLoading()
      wx.showModal({ title: '提交失败', content: error.message, showCancel: false })
    } finally {
      this.setData({ submitting: false })
    }
  },
})
