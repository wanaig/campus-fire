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
  updateFacility: (id, facility) => request(`/facilities/${id}`, { method: 'PUT', body: JSON.stringify(facility) }),
  deleteFacility: (id) => request(`/facilities/${id}`, { method: 'DELETE' }),
  qrCodes: (status = '', keyword = '') => {
    const params = []
    if (status) params.push(`status=${status}`)
    if (keyword) params.push(`keyword=${encodeURIComponent(keyword)}`)
    const qs = params.length ? `?${params.join('&')}` : ''
    return request(`/qr-codes${qs}`)
  },
  createQrBatch: (payload) => request('/qr-codes/batch', { method: 'POST', body: JSON.stringify(payload) }),
  downloadQrLabelsPdf: (status = '', ids = []) => {
    const params = []
    if (status) params.push(`status=${status}`)
    if (ids.length) params.push(`ids=${ids.join(',')}`)
    const qs = params.length ? `?${params.join('&')}` : ''
    return openPdf(`/qr-codes/labels.pdf${qs}`, 'qr-labels.pdf')
  },
  downloadQrPosterPdf: () => openPdf('/qr-codes/poster.pdf', 'platform-poster.pdf'),
  facilityHistory: (id) => request(`/facility-operations/${id}/history`),
  changeFacilityLifecycle: (id, eventType, note) => request(`/facility-operations/${id}/lifecycle`, { method: 'POST', body: JSON.stringify({ eventType, note }) }),
  maintenanceRecords: (facilityId) => request(`/maintenance-records${facilityId ? `?facilityId=${facilityId}` : ''}`),
  createMaintenanceRecord: (record) => request('/maintenance-records', { method: 'POST', body: JSON.stringify(record) }),
  updateMaintenanceRecord: (id, record) => request(`/maintenance-records/${id}`, { method: 'PUT', body: JSON.stringify(record) }),
  deleteMaintenanceRecord: (id) => request(`/maintenance-records/${id}`, { method: 'DELETE' }),
  facilityImportTemplateUrl: () => `${API_BASE}/facility-operations/template`,
  facilityTypes: () => request('/facilities/types'),
  facilityUpdateRules: () => request('/facility-update-rules'),
  createFacilityUpdateRule: (rule) => request('/facility-update-rules', { method: 'POST', body: JSON.stringify(rule) }),
  updateFacilityUpdateRule: (id, rule) => request(`/facility-update-rules/${id}`, { method: 'PUT', body: JSON.stringify(rule) }),
  deleteFacilityUpdateRule: (id) => request(`/facility-update-rules/${id}`, { method: 'DELETE' }),
  users: () => request('/users'),
  createUser: (user) => request('/users', { method: 'POST', body: JSON.stringify(user) }),
  updateUser: (id, user) => request(`/users/${id}`, { method: 'PUT', body: JSON.stringify(user) }),
  deleteUser: (id) => request(`/users/${id}`, { method: 'DELETE' }),
  inspectionItems: (facilityType) => request(`/inspection-items?facilityType=${encodeURIComponent(facilityType)}`),
  createInspectionItem: (item) => request('/inspection-items', { method: 'POST', body: JSON.stringify(item) }),
  updateInspectionItem: (id, item) => request(`/inspection-items/${id}`, { method: 'PUT', body: JSON.stringify(item) }),
  deleteInspectionItem: (id) => request(`/inspection-items/${id}`, { method: 'DELETE' }),
  inspectionTasks: (status = '', keyword = '') => {
    const params = []
    if (status) params.push(`status=${status}`)
    if (keyword) params.push(`keyword=${encodeURIComponent(keyword)}`)
    const qs = params.length ? `?${params.join('&')}` : ''
    return request(`/inspection-tasks${qs}`)
  },
  inspectionRecords: (facilityNo = '') => request(`/inspection/records${facilityNo ? `?facilityNo=${encodeURIComponent(facilityNo)}` : ''}`),
  inspectionPlans: () => request('/inspection-plans'),
  createPlan: (plan) => request('/inspection-plans', { method: 'POST', body: JSON.stringify(plan) }),
  updatePlan: (id, plan) => request(`/inspection-plans/${id}`, { method: 'PUT', body: JSON.stringify(plan) }),
  deletePlan: (id) => request(`/inspection-plans/${id}`, { method: 'DELETE' }),
  updateInspectionTask: (id, task) => request(`/inspection-tasks/${id}`, { method: 'PUT', body: JSON.stringify(task) }),
  deleteInspectionTask: (id) => request(`/inspection-tasks/${id}`, { method: 'DELETE' }),
  inspectionRecords: (facilityNo = '') => request(`/inspection/records${facilityNo ? `?facilityNo=${encodeURIComponent(facilityNo)}` : ''}`),
  voidInspectionRecord: (id, reason) => request(`/inspection/records/${id}/void`, { method: 'POST', body: JSON.stringify({ reason }) }),
  generateTasks: (id, dueDate) => request(`/inspection-plans/${id}/generate-tasks?dueDate=${dueDate}`, { method: 'POST' }),
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
