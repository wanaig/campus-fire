const API_BASE = import.meta.env.VITE_API_BASE || 'http://127.0.0.1:8080/api'

async function openPdf(path, filename) {
  const token = localStorage.getItem('campus-fire-token')
  const response = await fetch(`${API_BASE}${path}`, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
  if (!response.ok) {
    let message = 'PDF 生成失败'
    try { const payload = await response.json(); message = payload?.message || message } catch (_) {}
    throw new Error(message)
  }
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 60000)
}

async function request(path, options = {}) {
  const token = localStorage.getItem('campus-fire-token')
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok || payload?.code !== 'OK') {
    if (response.status === 401) localStorage.removeItem('campus-fire-token')
    throw new Error(payload?.message || '系统暂时无法连接，请稍后重试')
  }
  return payload.data
}

export const api = {
  login: (username, password) => request('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  }),
  me: () => request('/auth/me'),
  overview: (days = 30) => request(`/dashboard/overview?trendDays=${days}`),
  rectifications: () => request('/dashboard/rectifications'),
  facilities: (keyword = '') => request(`/facilities?keyword=${encodeURIComponent(keyword)}`),
  facilityDetail: (id) => request(`/facilities/${id}`),
  createFacility: (facility) => request('/facilities', { method: 'POST', body: JSON.stringify(facility) }),
  updateFacility: (id, facility) => request(`/facilities/${id}`, { method: 'PUT', body: JSON.stringify(facility) }),
  deleteFacility: (id, force = false) => request(`/facilities/${id}${force ? '?force=true' : ''}`, { method: 'DELETE' }),
  qrCodes: (status = '', keyword = '', page = 1, size = 20) => {
    const params = [`page=${page}`, `size=${size}`]
    if (status) params.push(`status=${status}`)
    if (keyword) params.push(`keyword=${encodeURIComponent(keyword)}`)
    return request(`/qr-codes?${params.join('&')}`)
  },
  createQrBatch: (payload) => request('/qr-codes/batch', { method: 'POST', body: JSON.stringify(payload) }),
  deleteQrCode: (id) => request(`/qr-codes/${id}`, { method: 'DELETE' }),
  batchDeleteQrCodes: (ids) => request('/qr-codes/batch-delete', {
    method: 'POST', body: JSON.stringify({ ids }),
  }),
  restoreQrCode: (id) => request(`/qr-codes/${id}/restore`, { method: 'POST' }),
  updateQrLabel: (id, label) => request(`/qr-codes/${id}/label`, {
    method: 'PUT', body: JSON.stringify({ label }),
  }),
  unbindQrCode: (id) => request(`/qr-codes/${id}/unbind`, { method: 'POST' }),
  downloadQrLabelsPdf: (status = '', ids = [], keyword = '') => {
    const params = []
    if (status) params.push(`status=${status}`)
    if (ids.length) params.push(`ids=${ids.join(',')}`)
    if (keyword) params.push(`keyword=${encodeURIComponent(keyword)}`)
    const qs = params.length ? `?${params.join('&')}` : ''
    return openPdf(`/qr-codes/labels.pdf${qs}`, 'qr-labels.pdf')
  },
  facilityHistory: (id) => request(`/facility-operations/${id}/history`),
  facilityPhotos: (id) => request(`/facilities/${id}/photos`),
  facilityPhotoFileUrl: (photoId) => `${API_BASE}/facility-photos/${photoId}/file`,
  changeFacilityLifecycle: (id, eventType, note) => request(`/facility-operations/${id}/lifecycle`, { method: 'POST', body: JSON.stringify({ eventType, note }) }),
  maintenanceRecords: (facilityId) => request(`/maintenance-records${facilityId ? `?facilityId=${facilityId}` : ''}`),
  facilityImportTemplateUrl: () => `${API_BASE}/facility-operations/template`,
  facilityTypes: () => request('/facilities/types'),
  managedFacilityTypes: () => request('/facility-types'),
  createFacilityType: (facilityType) => request('/facility-types', { method: 'POST', body: JSON.stringify(facilityType) }),
  updateFacilityType: (id, facilityType) => request(`/facility-types/${id}`, { method: 'PUT', body: JSON.stringify(facilityType) }),
  deleteFacilityType: (id) => request(`/facility-types/${id}`, { method: 'DELETE' }),
  users: () => request('/users'),
  createUser: (user) => request('/users', { method: 'POST', body: JSON.stringify(user) }),
  updateUser: (id, user) => request(`/users/${id}`, { method: 'PUT', body: JSON.stringify(user) }),
  deleteUser: (id) => request(`/users/${id}`, { method: 'DELETE' }),
  resetUserPassword: (id) => request(`/users/${id}/reset-password`, { method: 'POST' }),
  inspectionItems: (facilityType) => request(`/inspection-items?facilityType=${encodeURIComponent(facilityType)}`),
  createInspectionItem: (item) => request('/inspection-items', { method: 'POST', body: JSON.stringify(item) }),
  updateInspectionItem: (id, item) => request(`/inspection-items/${id}`, { method: 'PUT', body: JSON.stringify(item) }),
  updateInspectionItemOrder: (ids) => request('/inspection-items/order', { method: 'PUT', body: JSON.stringify({ ids }) }),
  deleteInspectionItem: (id) => request(`/inspection-items/${id}`, { method: 'DELETE' }),
  inspectionRecords: (facilityNo = '', inspector = '') => {
    const params = []
    if (facilityNo) params.push(`facilityNo=${encodeURIComponent(facilityNo)}`)
    if (inspector) params.push(`inspector=${encodeURIComponent(inspector)}`)
    const qs = params.length ? `?${params.join('&')}` : ''
    return request(`/inspection/records${qs}`)
  },
  inspectionStatus: ({ keyword = '', facilityType = '', campus = '', overdueDays = 30 } = {}) => {
    const params = [`overdueDays=${overdueDays}`]
    if (keyword) params.push(`keyword=${encodeURIComponent(keyword)}`)
    if (facilityType) params.push(`facilityType=${encodeURIComponent(facilityType)}`)
    if (campus) params.push(`campus=${encodeURIComponent(campus)}`)
    return request(`/inspection/status?${params.join('&')}`)
  },
  inspectionRecordPhotos: (id) => request(`/inspection/records/${id}/photos`),
  inspectionPhotoFileUrl: (photoId) => `${API_BASE}/inspection/photos/${photoId}/file`,
  voidInspectionRecord: (id, reason) => request(`/inspection/records/${id}/void`, { method: 'POST', body: JSON.stringify({ reason }) }),
  deleteInspectionRecord: (id) => request(`/inspection/records/${id}`, { method: 'DELETE' }),
  rectificationList: (status = '') => request(`/rectifications${status ? `?status=${status}` : ''}`),
  createRectification: (order) => request('/rectifications', { method: 'POST', body: JSON.stringify(order) }),
  updateRectification: (id, order) => request(`/rectifications/${id}`, { method: 'PUT', body: JSON.stringify(order) }),
  deleteRectification: (id) => request(`/rectifications/${id}`, { method: 'DELETE' }),
  resolveRectification: (id, resolutionNote) => request(`/rectifications/${id}/resolve`, {
    method: 'POST', body: JSON.stringify({ resolutionNote }),
  }),
  addRectificationEvidence: (id, urls) => request(`/rectifications/${id}/evidence`, { method: 'POST', body: JSON.stringify({ urls }) }),
  inspectionRecordsExportUrl: () => `${API_BASE}/reports/inspection-records.csv`,
  facilitiesExportUrl: () => `${API_BASE}/reports/facilities.csv`,
  rectificationsExportUrl: () => `${API_BASE}/reports/rectifications.csv`,
  facilitySuggestions: (status = '') => request(`/facility-suggestions${status ? `?status=${status}` : ''}`),
  createFacilitySuggestion: (suggestion) => request('/facility-suggestions', { method: 'POST', body: JSON.stringify(suggestion) }),
  actionFacilitySuggestion: (id, status, note) => request(`/facility-suggestions/${id}/action`, { method: 'POST', body: JSON.stringify({ status, note }) }),
  deleteFacilitySuggestion: (id) => request(`/facility-suggestions/${id}`, { method: 'DELETE' }),
  inspectionRisks: (status = '') => request(`/inspection-risks${status ? `?status=${status}` : ''}`),
  reviewInspectionRisk: (id, status) => request(`/inspection-risks/${id}/review`, { method: 'POST', body: JSON.stringify({ status }) }),
  deleteInspectionRisk: (id) => request(`/inspection-risks/${id}`, { method: 'DELETE' }),
  auditLogs: (actionCode = '') => request(`/audit-logs${actionCode ? `?action=${encodeURIComponent(actionCode)}` : ''}`),
}
