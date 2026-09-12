const { request, BASE_URL } = require('./api')

/** 照片文件接口带登录态下载为本地临时文件，供 wx.previewImage 预览 */
function downloadPhotoFile(path, photoId) {
  return new Promise(resolve => {
    const token = wx.getStorageSync('token')
    wx.request({
      url: `${BASE_URL}${path}`,
      header: token ? { Authorization: 'Bearer ' + token } : {},
      responseType: 'arraybuffer',
      success(res) {
        if (res.statusCode !== 200 || !res.data) return resolve('')
        // 照片实际可能是 JPEG 或 PNG：扩展名与内容不符会导致真机预览打不开
        const contentType = String((res.header && (res.header['Content-Type'] || res.header['content-type'])) || '').toLowerCase()
        const filePath = `${wx.env.USER_DATA_PATH}/photo_${photoId}${contentType.indexOf('png') >= 0 ? '.png' : '.jpg'}`
        wx.getFileSystemManager().writeFile({
          filePath,
          data: res.data,
          encoding: 'binary',
          success: () => resolve(filePath),
          fail: () => resolve(''),
        })
      },
      fail: () => resolve(''),
    })
  })
}

/** 巡检留痕照片文件下载 */
function downloadPhoto(photoId) {
  return downloadPhotoFile(`/inspection/photos/${photoId}/file`, photoId)
}

/** 采集员设施初始照片文件下载 */
function downloadFacilityPhoto(photoId) {
  return downloadPhotoFile(`/facility-photos/${photoId}/file`, photoId)
}

/** 拉取一条巡检记录的照片清单并预览（照片清单与文件接口只读公开，访客可查看） */
async function previewRecordPhotos(record) {
  if (!record || !record.photoCount) return
  wx.showLoading({ title: '正在加载照片', mask: true })
  try {
    const list = await request(`/inspection/records/${record.id}/photos`)
    const paths = []
    for (const photo of (list || [])) {
      const path = await downloadPhoto(photo.photoId)
      if (path) paths.push(path)
    }
    wx.hideLoading()
    if (!paths.length) {
      wx.showToast({ title: '未找到现场照片文件', icon: 'none' })
      return
    }
    wx.previewImage({
      urls: paths,
      fail: () => wx.showModal({ title: '无法查看照片', content: '照片预览打开失败，请重试', showCancel: false }),
    })
  } catch (error) {
    wx.hideLoading()
    wx.showModal({ title: '无法查看照片', content: error.message, showCancel: false })
  }
}

/** 拉取一个设施的初始照片清单并预览（采集员建档留痕，接口只读公开） */
async function previewFacilityPhotos(facilityId) {
  if (!facilityId) return
  wx.showLoading({ title: '正在加载照片', mask: true })
  try {
    const list = await request(`/facilities/${facilityId}/photos`)
    const paths = []
    for (const photo of (list || [])) {
      const path = await downloadFacilityPhoto(photo.photoId)
      if (path) paths.push(path)
    }
    wx.hideLoading()
    if (!paths.length) {
      wx.showToast({ title: '该设施还没有初始照片', icon: 'none' })
      return
    }
    wx.previewImage({
      urls: paths,
      fail: () => wx.showModal({ title: '无法查看照片', content: '照片预览打开失败，请重试', showCancel: false }),
    })
  } catch (error) {
    wx.hideLoading()
    wx.showModal({ title: '无法查看照片', content: error.message, showCancel: false })
  }
}

module.exports = { downloadPhoto, downloadFacilityPhoto, previewRecordPhotos, previewFacilityPhotos }
