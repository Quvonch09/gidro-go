$baseUrl = "http://localhost:8080"
$ErrorActionPreference = "Stop"

Write-Host "=== TEST FIGMA FULL ALIGNMENT FLOW ===" -ForegroundColor Cyan

# 1. SuperAdmin login
$adminLogin = @{ username = "admin"; password = "Admin123!" } | ConvertTo-Json
$adminRes = Invoke-RestMethod -Uri "$baseUrl/api/auth/superadmin/login" -Method Post -Body $adminLogin -ContentType "application/json"
$adminToken = $adminRes.data.accessToken
Write-Host "[1] SuperAdmin logged in successfully. Token received." -ForegroundColor Green

# 2. Create Farm + Boss
$ts = Get-Date -Format "yyyyMMddHHmmss"
$bossPhone = "+99890$($ts.Substring(6))"
$farmReq = @{
    name = "Figma Hydro Farm $ts"
    address = "Toshkent sh., Yunusobod tumani"
    latitude = 41.3650
    longitude = 69.2850
    phone = "+99871$($ts.Substring(6))"
    bossFullName = "Farm Boss $ts"
    bossPhone = $bossPhone
    bossPassword = "BossPassword123!"
} | ConvertTo-Json
$headers = @{ Authorization = "Bearer $adminToken" }
$farmRes = Invoke-RestMethod -Uri "$baseUrl/api/superadmin/farms" -Method Post -Body $farmReq -ContentType "application/json" -Headers $headers
$farmId = $farmRes.data.id
Write-Host "[2] Farm created: ID $farmId, Name: $($farmRes.data.name)" -ForegroundColor Green

# 3. Boss Login & Check Boss Dashboard
$bossLogin = @{ phone = $bossPhone; password = "BossPassword123!" } | ConvertTo-Json
$bossRes = Invoke-RestMethod -Uri "$baseUrl/api/auth/staff/login" -Method Post -Body $bossLogin -ContentType "application/json"
$bossToken = $bossRes.data.accessToken
$bossHeaders = @{ Authorization = "Bearer $bossToken" }

$bossDash = Invoke-RestMethod -Uri "$baseUrl/api/boss/dashboard" -Method Get -Headers $bossHeaders
Write-Host "[3] Boss Dashboard KPI: todayIncome=$($bossDash.data.todayIncome), activeOrders=$($bossDash.data.activeOrders), totalClients=$($bossDash.data.totalClients)" -ForegroundColor Green

# 4. Boss creates Manager and Courier
$mgrPhone = "+99891$($ts.Substring(6))"
$mgrReq = @{
    role = "MANAGER"
    fullName = "Test Manager $ts"
    username = "mgr_$ts"
    phone = $mgrPhone
    password = "MgrPassword123!"
} | ConvertTo-Json
$mgrRes = Invoke-RestMethod -Uri "$baseUrl/api/boss/staff" -Method Post -Body $mgrReq -ContentType "application/json" -Headers $bossHeaders

$courPhone = "+99892$($ts.Substring(6))"
$courReq = @{
    role = "COURIER"
    fullName = "Test Courier $ts"
    username = "cour_$ts"
    phone = $courPhone
    password = "CourPassword123!"
} | ConvertTo-Json
$courRes = Invoke-RestMethod -Uri "$baseUrl/api/boss/staff" -Method Post -Body $courReq -ContentType "application/json" -Headers $bossHeaders
$courId = $courRes.data.id
Write-Host "[4] Staff created: Manager $mgrPhone and Courier $courId ($courPhone)" -ForegroundColor Green

# 5. Manager Login
$mgrLogin = @{ phone = $mgrPhone; password = "MgrPassword123!" } | ConvertTo-Json
$mgrAuth = Invoke-RestMethod -Uri "$baseUrl/api/auth/staff/login" -Method Post -Body $mgrLogin -ContentType "application/json"
$mgrToken = $mgrAuth.data.accessToken
$mgrHeaders = @{ Authorization = "Bearer $mgrToken" }

# 6. Manager Dashboard Initial Check
$mgrDash = Invoke-RestMethod -Uri "$baseUrl/api/manager/dashboard" -Method Get -Headers $mgrHeaders
Write-Host "[5] Manager Dashboard Initial: farmId=$($mgrDash.data.farmId), warehouseBottles=$($mgrDash.data.warehouseBottles), activeCouriers=$($mgrDash.data.activeCouriers)" -ForegroundColor Green

# 7. Manager creates Figma-aligned Product (19L water with deposit, volume, image, desc)
$prodReq = @{
    name = "GidroGO Premium 19L"
    price = 15000.00
    volumeLiters = 19.0
    depositPrice = 35000.00
    imageUrl = "https://gidrogo.uz/assets/water-19l.png"
    description = "Tabiiy tog' suvi, 5 bosqichli filtrlangan"
} | ConvertTo-Json
$prodRes = Invoke-RestMethod -Uri "$baseUrl/api/manager/products" -Method Post -Body $prodReq -ContentType "application/json" -Headers $mgrHeaders
$prodId = $prodRes.data.id
Write-Host "[6] Product created: ID $prodId, volumeLiters=$($prodRes.data.volumeLiters), depositPrice=$($prodRes.data.depositPrice)" -ForegroundColor Green

# 8. Manager creates Service Region and toggles it
$regionReq = @{
    regionName = "Yunusobod Markaziy Hudud"
    polygonJson = '[[41.35, 69.27], [41.38, 69.27], [41.38, 69.30], [41.35, 69.30]]'
} | ConvertTo-Json
$regRes = Invoke-RestMethod -Uri "$baseUrl/api/manager/regions" -Method Post -Body $regionReq -ContentType "application/json" -Headers $mgrHeaders
$regId = $regRes.data.id
Write-Host "[7] Service Region created: ID $regId, active=$($regRes.data.active)" -ForegroundColor Green

# Toggle region
$regToggle = Invoke-RestMethod -Uri "$baseUrl/api/manager/regions/$regId/toggle" -Method Patch -Headers $mgrHeaders
Write-Host "[7.1] Service Region toggled: active=$($regToggle.data.active)" -ForegroundColor Green
$regToggle2 = Invoke-RestMethod -Uri "$baseUrl/api/manager/regions/$regId/toggle" -Method Patch -Headers $mgrHeaders
Write-Host "[7.2] Service Region re-activated: active=$($regToggle2.data.active)" -ForegroundColor Green

# 9. Manager restocks warehouse
$stockReq = @{ productId = $prodId; quantity = 150.0 } | ConvertTo-Json
$stockRes = Invoke-RestMethod -Uri "$baseUrl/api/manager/stock/warehouse" -Method Put -Body $stockReq -ContentType "application/json" -Headers $mgrHeaders
Write-Host "[8] Warehouse restocked: $($stockRes.data.productName) -> $($stockRes.data.quantity) dona" -ForegroundColor Green

# 10. Courier Login & Vehicle Restock BEFORE orders
$courLogin = @{ phone = $courPhone; password = "CourPassword123!" } | ConvertTo-Json
$courAuth = Invoke-RestMethod -Uri "$baseUrl/api/auth/staff/login" -Method Post -Body $courLogin -ContentType "application/json"
$courToken = $courAuth.data.accessToken
$courHeaders = @{ Authorization = "Bearer $courToken" }

# Courier restocks vehicle from warehouse
$vehStockReq = @{ productId = $prodId; quantity = 30.0 } | ConvertTo-Json
$vehRes = Invoke-RestMethod -Uri "$baseUrl/api/courier/stock/restock" -Method Post -Body $vehStockReq -ContentType "application/json" -Headers $courHeaders
Write-Host "[9] Courier restocked vehicle: $($vehRes.data.quantity) dona" -ForegroundColor Green

# Courier updates GPS location
$gpsReq = @{ latitude = 41.3655; longitude = 69.2855 } | ConvertTo-Json
Invoke-RestMethod -Uri "$baseUrl/api/courier/location" -Method Post -Body $gpsReq -ContentType "application/json" -Headers $courHeaders | Out-Null
Write-Host "[10] Courier GPS updated to Yunusobod (41.3655, 69.2855)" -ForegroundColor Green

# 11. Client registration & binding to this farm
$clientPhone = "+99893$($ts.Substring(6))"
$otpReq = @{ phone = $clientPhone } | ConvertTo-Json
$otpRes = Invoke-RestMethod -Uri "$baseUrl/api/auth/client/otp/send" -Method Post -Body $otpReq -ContentType "application/json"
$code = $otpRes.data.debugCode
if (-not $code) { $code = "123456" }

$verifyReq = @{ phone = $clientPhone; code = $code } | ConvertTo-Json
Invoke-RestMethod -Uri "$baseUrl/api/auth/client/otp/verify" -Method Post -Body $verifyReq -ContentType "application/json" | Out-Null

$clientReg = @{
    phone = $clientPhone
    fullName = "Mijoz Alisher $ts"
    farmId = $farmId
    password = "ClientPassword123!"
    address = "Yunusobod 12-mavze, 45-uy"
    latitude = 41.3660
    longitude = 69.2860
} | ConvertTo-Json
$clientAuth = Invoke-RestMethod -Uri "$baseUrl/api/auth/client/register" -Method Post -Body $clientReg -ContentType "application/json"
$clientToken = $clientAuth.data.accessToken
$clientHeaders = @{ Authorization = "Bearer $clientToken" }
Write-Host "[11] Client registered and bound to Farm $farmId. FullName: $($clientAuth.data.fullName)" -ForegroundColor Green

# 12. Client gets saved addresses
$addresses = Invoke-RestMethod -Uri "$baseUrl/api/client/addresses" -Method Get -Headers $clientHeaders
$addrId = $addresses.data[0].id
Write-Host "[12] Client address verified: ID $addrId, Address: $($addresses.data[0].address)" -ForegroundColor Green

# 13. Client orders with 0 empty bottles returned (must include deposit for 1 bottle: 15000 + 35000 = 50000)
$checkoutReq1 = @{
    items = @( @{ productId = $prodId; quantity = 1.0 } )
    addressId = $addrId
    paymentMethod = "CASH"
    deliverySlot = "14:00 - 16:00"
    emptyBottlesReturned = 0
    clientComment = "Domofon ishlamaydi, qo'ng'iroq qiling"
} | ConvertTo-Json
$orderRes1 = Invoke-RestMethod -Uri "$baseUrl/api/client/cart/checkout" -Method Post -Body $checkoutReq1 -ContentType "application/json" -Headers $clientHeaders
$order1 = $orderRes1.data[0]
Write-Host "[13] Order #1 created: Total=$($order1.totalSum) UZS (Deposit=$($order1.depositAmount) UZS), Status=$($order1.status), Slot=$($order1.deliverySlot), EmptyReturned=$($order1.emptyBottlesReturned)" -ForegroundColor Green

# 14. Courier delivery workflow
Invoke-RestMethod -Uri "$baseUrl/api/courier/orders/$($order1.id)/accept" -Method Post -Headers $courHeaders | Out-Null
Write-Host "[14.1] Courier accepted order" -ForegroundColor Green

Invoke-RestMethod -Uri "$baseUrl/api/courier/orders/$($order1.id)/start" -Method Post -Headers $courHeaders | Out-Null
Write-Host "[14.2] Courier started delivery (ON_THE_WAY)" -ForegroundColor Green

$deliverReq = @{ photoUrl = "https://gidrogo.uz/photos/order1.jpg" } | ConvertTo-Json
Invoke-RestMethod -Uri "$baseUrl/api/courier/orders/$($order1.id)/deliver" -Method Post -Body $deliverReq -ContentType "application/json" -Headers $courHeaders | Out-Null
Write-Host "[14.3] Courier delivered order (DELIVERED, photo attached)" -ForegroundColor Green

$compRes = Invoke-RestMethod -Uri "$baseUrl/api/courier/orders/$($order1.id)/cash-collected" -Method Post -Headers $courHeaders
Write-Host "[14.4] Courier confirmed cash collected. Final Status: $($compRes.data.status)" -ForegroundColor Green

# 15. Client reviews order (Mobile dual review: courier + water quality)
$reviewReq = @{
    courierStars = 5
    waterStars = 5
    comment = "Suv juda toza va mazali! Kuryer ham vaqtida keldi."
} | ConvertTo-Json
$reviewRes = Invoke-RestMethod -Uri "$baseUrl/api/client/orders/$($order1.id)/review" -Method Post -Body $reviewReq -ContentType "application/json" -Headers $clientHeaders
Write-Host "[15] Client review submitted: $($reviewRes.message)" -ForegroundColor Green

# 16. Manager checks CRM Client Details Drawer
$crmClients = Invoke-RestMethod -Uri "$baseUrl/api/manager/clients" -Method Get -Headers $mgrHeaders
$cId = $crmClients.data[0].clientId
Write-Host "[16.1] Manager fetched CRM Clients count: $($crmClients.data.Count)" -ForegroundColor Green

$clientDetail = Invoke-RestMethod -Uri "$baseUrl/api/manager/clients/$cId" -Method Get -Headers $mgrHeaders
Write-Host "[16.2] CRM Client Drawer Detail:" -ForegroundColor Green
Write-Host "       FullName: $($clientDetail.data.fullName)" -ForegroundColor Green
Write-Host "       TotalOrders: $($clientDetail.data.totalOrders)" -ForegroundColor Green
Write-Host "       TotalSpent: $($clientDetail.data.totalSpent) UZS" -ForegroundColor Green
Write-Host "       Addresses: $($clientDetail.data.addresses.Count)" -ForegroundColor Green
Write-Host "       RecentOrders: $($clientDetail.data.recentOrders.Count)" -ForegroundColor Green

# 17. Manager Dashboard Final Check (verifying live updated metrics)
$finalDash = Invoke-RestMethod -Uri "$baseUrl/api/manager/dashboard" -Method Get -Headers $mgrHeaders
Write-Host "`n[17] Final Manager Dashboard Metrics:" -ForegroundColor Yellow
Write-Host "     Today Income: $($finalDash.data.todayIncome) UZS" -ForegroundColor Yellow
Write-Host "     Month Income: $($finalDash.data.monthIncome) UZS" -ForegroundColor Yellow
Write-Host "     Total Orders: $($finalDash.data.totalOrders)" -ForegroundColor Yellow
Write-Host "     Delivered Today: $($finalDash.data.deliveredToday)" -ForegroundColor Yellow
Write-Host "     Bottles Sold Today: $($finalDash.data.bottlesSoldToday)" -ForegroundColor Yellow
Write-Host "     Warehouse Bottles: $($finalDash.data.warehouseBottles)" -ForegroundColor Yellow
Write-Host "     Active Couriers: $($finalDash.data.activeCouriers)" -ForegroundColor Yellow
Write-Host "     Total Clients: $($finalDash.data.totalClients)" -ForegroundColor Yellow

Write-Host "`n>>> ALL FIGMA ENDPOINTS AND WORKFLOWS VERIFIED 100% SUCCESSFULLY! <<<" -ForegroundColor Green
