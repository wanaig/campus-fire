# 校园消防设施巡检管理系统 - 端到端冒烟测试
# 用法：powershell -ExecutionPolicy Bypass -File deploy/e2e-smoke.ps1 [-BaseUrl http://localhost:8080/api]
param(
    [string]$BaseUrl = "http://localhost:8080/api",
    [string]$AdminPassword = "123456",
    [string]$GuardUser = "guard01",
    [string]$GuardPassword = "123456"
)

$ErrorActionPreference = 'Stop'
$script:passed = 0
$script:failed = 0

function Assert-True($condition, $name) {
    if ($condition) { $script:passed++; Write-Host "  [PASS] $name" -ForegroundColor Green }
    else { $script:failed++; Write-Host "  [FAIL] $name" -ForegroundColor Red }
}

function Invoke-Api($Method, $Path, $Token, $Body) {
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $params = @{ Method = $Method; Uri = "$BaseUrl$Path"; Headers = $headers }
    if ($Body -ne $null) {
        $json = $Body | ConvertTo-Json -Depth 10
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
        $params.ContentType = 'application/json; charset=utf-8'
    }
    Invoke-RestMethod @params
}

Write-Host "== 校园消防巡检系统 E2E 冒烟测试 ==" -ForegroundColor Cyan

# 1. 健康检查
$health = Invoke-RestMethod "$BaseUrl/health"
Assert-True ($health.code -eq 'OK') "健康检查"

# 2. 管理员登录
$admin = Invoke-Api POST '/auth/login' $null @{ username = 'admin'; password = $AdminPassword }
$adminToken = $admin.data.accessToken
Assert-True ($admin.data.user.roleCode -eq 'ADMIN') "管理员登录"

# 3. 基础数据接口
$types = Invoke-Api GET '/facilities/types' $adminToken
Assert-True ($types.data.Count -ge 3) "设施类型列表（$($types.data.Count) 种）"
$users = Invoke-Api GET '/users' $adminToken
Assert-True ($users.data.Count -ge 2) "用户列表（$($users.data.Count) 人）"
$tasksAdmin = Invoke-Api GET '/inspection-tasks' $adminToken
Assert-True ($tasksAdmin.data.tasks.Count -ge 1) "管理端任务列表（$($tasksAdmin.data.tasks.Count) 条）"

# 4. 新建账号（含重复账号校验，账号名加随机后缀保证可重复运行）
$dupError = $null
try { Invoke-Api POST '/users' $adminToken @{ username = 'admin'; displayName = 'x'; password = 'Passw0rd!'; roleCode = 'GUARD' } | Out-Null }
catch { $dupError = 'rejected' }
Assert-True ($dupError -eq 'rejected') "重复账号被拒绝"
$e2eUser = "e2e-guard-{0:x}" -f (Get-Random -Maximum 65536)
$newUser = Invoke-Api POST '/users' $adminToken @{ username = $e2eUser; displayName = '联调测试保安'; password = 'E2eGuard@123'; roleCode = 'GUARD' }
$newLogin = Invoke-Api POST '/auth/login' $null @{ username = $e2eUser; password = 'E2eGuard@123' }
Assert-True ($newLogin.data.user.roleCode -eq 'GUARD') "新建账号可登录"

# 5. 保安登录并获取任务
$guard = Invoke-Api POST '/auth/login' $null @{ username = $GuardUser; password = $GuardPassword }
$guardToken = $guard.data.accessToken
Assert-True ($guard.data.user.roleCode -eq 'GUARD') "保安登录"
$pending = Invoke-Api GET '/inspection/tasks?status=PENDING' $guardToken
Assert-True ($pending.data.Count -ge 1) "保安待巡检任务（$($pending.data.Count) 条）"
$task = $pending.data[0]

# 6. 找到任务对应设施的二维码
$facilities = Invoke-Api GET "/facilities?keyword=$([uri]::EscapeDataString($task.facility_no))" $adminToken
$facility = $facilities.data | Where-Object { $_.facilityNo -eq $task.facility_no } | Select-Object -First 1
Assert-True ($null -ne $facility -and $facility.qrToken) "获取设施二维码令牌"

# 7. 错误二维码被拒绝（任务此时仍是待巡检状态）
$lat = [double]$facility.latitude; $lon = [double]$facility.longitude
$badError = $null
try { Invoke-Api POST '/inspection/sessions' $guardToken @{ taskId = $task.id; qrToken = 'WRONG-TOKEN'; latitude = $lat; longitude = $lon } | Out-Null }
catch { $badError = 'rejected' }
Assert-True ($badError -eq 'rejected') "无效二维码被拒绝"

# 8. 扫码 + 定位校验开始巡检
$session = Invoke-Api POST '/inspection/sessions' $guardToken @{
    taskId = $task.id; qrToken = $facility.qrToken
    latitude = $lat; longitude = $lon; allowedDistanceMeters = 200
}
$sessionId = $session.data.sessionId
Assert-True ($sessionId.Length -eq 36) "扫码开始巡检（会话已创建）"

# 9. 检查项与拍照提示
$items = Invoke-Api GET "/inspection-items?facilityType=$($task.facility_type)" $guardToken
$enabledItems = @($items.data | Where-Object { $_.enabled })
Assert-True ($enabledItems.Count -ge 2) "加载启用检查项（$($enabledItems.Count) 项）"
if ($task.facility_type -eq 'FIRE_HYDRANT') {
    Assert-True ($enabledItems.Count -eq 6) "消火栓纸质表六项检查内容"
}
$prompts = Invoke-Api GET "/inspection/sessions/$sessionId/photo-prompts" $guardToken
Assert-True ($prompts.data.prompts.Count -ge 1) "拍照提示"

# 10. 保存草稿（含空草稿，再保存真实结果并回读）
$results = @{}
$first = $true
foreach ($item in $enabledItems) {
    if ($item.required_flag -and $first) { $results[$item.item_code] = 'FAIL'; $first = $false }
    else { $results[$item.item_code] = 'PASS' }
}
Invoke-Api POST "/inspection/sessions/$sessionId/draft" $guardToken @{
    taskId = $task.id; results = $results; note = '压力表指针低于绿色区域，需要更换'
} | Out-Null
$loaded = Invoke-Api GET "/inspection/sessions/$sessionId/draft" $guardToken
$draftCount = @($loaded.data.results.PSObject.Properties).Count
Assert-True ($draftCount -eq $enabledItems.Count) "草稿保存并回读（$draftCount 项）"

# 11. 现场照片上传（仅相机来源、JPEG 校验）
# 构造真实 JPEG：水印服务会解码原图重绘，纯标记字节的假 JPEG 无法通过校验
Add-Type -AssemblyName System.Drawing
$bmp = New-Object System.Drawing.Bitmap 320, 240
$graphic = [System.Drawing.Graphics]::FromImage($bmp)
$graphic.Clear([System.Drawing.Color]::White)
$graphic.Dispose()
$stream = New-Object System.IO.MemoryStream
$bmp.Save($stream, [System.Drawing.Imaging.ImageFormat]::Jpeg)
$bmp.Dispose()
$jpeg = Join-Path $env:TEMP "e2e-photo.jpg"
[IO.File]::WriteAllBytes($jpeg, $stream.ToArray())
$photo = & curl.exe -s -X POST "$BaseUrl/inspection/sessions/$sessionId/photos" `
    -H "Authorization: Bearer $guardToken" `
    -F "file=@$jpeg;type=image/jpeg" `
    -F "captureSource=CAMERA" `
    -F "clientCapturedAt=$([DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ'))" `
    -F "deviceId=e2e-device" -F "latitude=$lat" -F "longitude=$lon" | ConvertFrom-Json
Assert-True ($photo.data.photoId -and $photo.data.sha256.Length -eq 64) "现场照片上传（含 SHA-256 留痕）"

$albumError = $null
try {
    $bad = & curl.exe -s -X POST "$BaseUrl/inspection/sessions/$sessionId/photos" `
        -H "Authorization: Bearer $guardToken" `
        -F "file=@$jpeg;type=image/jpeg" `
        -F "captureSource=ALBUM" `
        -F "clientCapturedAt=$([DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ'))" `
        -F "deviceId=e2e-device" -F "latitude=$lat" -F "longitude=$lon" | ConvertFrom-Json
    if ($bad.code -ne 'OK') { $albumError = 'rejected' }
} catch { $albumError = 'rejected' }
Assert-True ($albumError -eq 'rejected') "相册来源照片被拒绝"

# 12. 采集员设施初始照片（上传/防造假约束/权限/清单/删除）
$e2eCollector = "e2e-collector-{0:x}" -f (Get-Random -Maximum 65536)
Invoke-Api POST '/users' $adminToken @{ username = $e2eCollector; displayName = '联调测试采集员'; password = 'E2eCollector@123'; roleCode = 'COLLECTOR' } | Out-Null
$collector = Invoke-Api POST '/auth/login' $null @{ username = $e2eCollector; password = 'E2eCollector@123' }
$collectorToken = $collector.data.accessToken
Assert-True ($collector.data.user.roleCode -eq 'COLLECTOR') "采集员账号登录"

$facilityPhoto = & curl.exe -s -X POST "$BaseUrl/facilities/$($facility.id)/photos" `
    -H "Authorization: Bearer $collectorToken" `
    -F "file=@$jpeg;type=image/jpeg" `
    -F "captureSource=CAMERA" `
    -F "clientCapturedAt=$([DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ'))" `
    -F "deviceId=e2e-device" -F "latitude=$lat" -F "longitude=$lon" | ConvertFrom-Json
Assert-True ($facilityPhoto.data.photoId -and $facilityPhoto.data.sha256.Length -eq 64) "设施初始照片上传（含 SHA-256 留痕）"

$facilityAlbumError = $null
try {
    $badFacility = & curl.exe -s -X POST "$BaseUrl/facilities/$($facility.id)/photos" `
        -H "Authorization: Bearer $collectorToken" `
        -F "file=@$jpeg;type=image/jpeg" `
        -F "captureSource=ALBUM" `
        -F "clientCapturedAt=$([DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ'))" `
        -F "deviceId=e2e-device" -F "latitude=$lat" -F "longitude=$lon" | ConvertFrom-Json
    if ($badFacility.code -ne 'OK') { $facilityAlbumError = 'rejected' }
} catch { $facilityAlbumError = 'rejected' }
Assert-True ($facilityAlbumError -eq 'rejected') "设施相册来源照片被拒绝"

$stalePhotoError = $null
try {
    $staleFacility = & curl.exe -s -X POST "$BaseUrl/facilities/$($facility.id)/photos" `
        -H "Authorization: Bearer $collectorToken" `
        -F "file=@$jpeg;type=image/jpeg" `
        -F "captureSource=CAMERA" `
        -F "clientCapturedAt=2020-01-01T00:00:00Z" `
        -F "deviceId=e2e-device" -F "latitude=$lat" -F "longitude=$lon" | ConvertFrom-Json
    if ($staleFacility.code -ne 'OK') { $stalePhotoError = 'rejected' }
} catch { $stalePhotoError = 'rejected' }
Assert-True ($stalePhotoError -eq 'rejected') "超时设施照片被拒绝"

$guardUploadError = $null
try {
    $guardUpload = & curl.exe -s -X POST "$BaseUrl/facilities/$($facility.id)/photos" `
        -H "Authorization: Bearer $guardToken" `
        -F "file=@$jpeg;type=image/jpeg" `
        -F "captureSource=CAMERA" `
        -F "clientCapturedAt=$([DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ'))" `
        -F "deviceId=e2e-device" -F "latitude=$lat" -F "longitude=$lon" | ConvertFrom-Json
    if ($guardUpload.code -ne 'OK') { $guardUploadError = 'rejected' }
} catch { $guardUploadError = 'rejected' }
Assert-True ($guardUploadError -eq 'rejected') "保安账号不能上传设施初始照片"

$facilityPhotos = Invoke-Api GET "/facilities/$($facility.id)/photos" $collectorToken
$matchedPhoto = @($facilityPhotos.data) | Where-Object { $_.photoId -eq $facilityPhoto.data.photoId }
Assert-True ($matchedPhoto.Count -eq 1) "设施初始照片清单（$(@($facilityPhotos.data).Count) 张）"

$facilitiesAfterPhoto = Invoke-Api GET "/facilities?keyword=$([uri]::EscapeDataString($task.facility_no))" $adminToken
$facilityAfterPhoto = $facilitiesAfterPhoto.data | Where-Object { $_.facilityNo -eq $task.facility_no } | Select-Object -First 1
Assert-True ($facilityAfterPhoto.photoCount -ge 1) "设施列表返回初始照片数（$($facilityAfterPhoto.photoCount) 张）"

$photoStatus = & curl.exe -s -o (Join-Path $env:TEMP "e2e-facility-photo.jpg") -w "%{http_code}" "$BaseUrl/facility-photos/$($facilityPhoto.data.photoId)/file"
Assert-True ($photoStatus -eq '200') "设施初始照片文件免登录可读（访客查看）"

$removed = Invoke-Api DELETE "/facility-photos/$($facilityPhoto.data.photoId)" $collectorToken
Assert-True ($removed.data.deleted) "采集员删除设施初始照片"

# 13. 提交巡检
$submit = Invoke-Api POST "/inspection/sessions/$sessionId/submit" $guardToken
Assert-True ($submit.data.photoCount -ge 1) "提交巡检记录（照片 $($submit.data.photoCount) 张）"

# 14. 异常自动生成整改单
Start-Sleep -Milliseconds 300
$rects = Invoke-Api GET '/rectifications?status=OPEN' $guardToken
$rect = $rects.data | Where-Object { $_.task_id -eq $task.id } | Select-Object -First 1
Assert-True ($null -ne $rect) "异常自动生成整改单"
Assert-True ($rect.issue_summary -match 'FAIL|异常|不合格') "整改单包含异常内容"

# 15. 管理员关闭整改单
$resolved = Invoke-Api POST "/rectifications/$($rect.id)/resolve" $adminToken @{ resolutionNote = '已更换器材并复检合格' }
Assert-True ($resolved.data.resolved) "整改单闭环"
$taskAfter = Invoke-Api GET '/inspection/tasks?status=COMPLETED' $guardToken
Assert-True (($taskAfter.data | Where-Object { $_.id -eq $task.id }) -ne $null) "任务状态已完成"

# 16. 看板统计
$overview = Invoke-Api GET '/dashboard/overview?trendDays=7' $adminToken
Assert-True ($overview.data.inspectionTrend.Count -ge 1) "看板巡检趋势统计"
Assert-True ($overview.data.facilityTotal -ge 1) "看板设施总数（$($overview.data.facilityTotal)）"

Write-Host ""
Write-Host "结果：$script:passed 通过，$script:failed 失败" -ForegroundColor $(if ($script:failed) { 'Red' } else { 'Green' })
if ($script:failed) { exit 1 }
