# GidroGo SuperAdmin — Backend vazifalari bo'yicha hisobot

Sana: 2026-10-16. Manba: `GidroGo_SuperAdmin_Backend_Vazifalar.md` (BE-01…BE-25).
Kontrakt: [`docs/openapi.json`](openapi.json) — 149 operatsiya (yangilash: `./mvnw test -Dtest=OpenApiExportIT`).

## 1. Holat jadvali

| BE | Holat | Asosiy o'zgargan fayllar | Testlar (`src/test/java/uz/gidrogo/it`) | OpenAPI o'zgarishi |
|---|---|---|---|---|
| BE-01 | Bajarildi | `finance/RevenueService` (yangi, yagona), `SuperAdminService.getDailyDynamics`, `FinanceService.getSummary/getDashboard`, `BossService.getFinanceChart/getProductsSalesSummary` | `RevenueAndFarmDetailIT` (ASSIGNED→0, COMPLETED→+50 000, CANCELLED yo'q, 23:30 chegarasi, 0.1+0.2, Boss = SuperAdmin) | `DailyDynamicsItem.totalAmount` → `orderedAmount`; `revenue`, `taraRevenue` BigDecimal; `FinanceSummaryResponse.taraIncome`, `completedOrders` |
| BE-02 | Bajarildi | `FarmService.getFarmDetail`, `farm/FarmAnalyticsService` (yangi) | `newFarmHasZeroStatistics`, `detailMatchesDailyDynamics` | `StatisticsCards` qayta tuzilgan (pastga qarang) |
| BE-03 | Bajarildi; **prod'da egasi qarori kerak** | `V10__boss_farm_integrity.sql`, `SuperAdminService.getBosses/updateBoss`, `FarmRepository.findByIdForUpdate` | `BossFarmIntegrityIT`, `SafeMigrationIT` | `BossSummaryCards`, `BossListResponse` qayta tuzilgan |
| BE-04 | Bajarildi | `FarmService.decideBossActivation`, `ActivationDecision` (yangi enum) | `BossActivationIT` (422, 409, parallel 200/409, audit) | `ActivationDecisionRequest.status`: faqat `APPROVED│REJECTED` |
| BE-05 | Bajarildi (Q11 tavsiyasi qo'llandi) | `AuthService.login`, `V11__boss_activation_status.sql`, `FarmService.getActivationRequests` | `BossActivationIT` | Login: `403 ACTIVATION_PENDING / ACTIVATION_REJECTED`; `GET /activation-requests` — sahifa + `summary` |
| BE-06 | Bajarildi (serverda `SPRING_PROFILES_ACTIVE=prod` kerak) | `application-prod.yml`, `SecurityConfig` | `SwaggerDisabledIT`, `OpenApiExportIT` | `docs/openapi.json` |
| BE-07 | Bajarildi | `RefreshTokenService` (yangi), `AuthService.refreshToken/logout`, `V13` | `AuthSessionIT.logoutRevokesRefreshToken` | Refresh xatolari endi `401` (avval 400) |
| BE-08 | Bajarildi (Q9 tavsiyasi: 409) | `SuperAdminClientService`, `SuperAdminCrmController` (yangi), `OrderService.checkout` | `ClientsOrdersAndFarmStatusIT` | 7 ta yangi endpoint `/api/superadmin/clients*` |
| BE-09 | Qisman | `SuperAdminOrderService` (yangi), `V13` indekslar, `V12` status tarixi o'zgarmasligi | `ordersFilterMatchesDynamics` | `GET /orders`, `GET /orders/{id}` |
| BE-10 | Bajarildi | `SuperAdminService.getDashboard/getGrowthSeries`, `order/OrderStatsService` (yangi) | `dashboardInvariant`, `noGlobalRevenue` | `DashboardResponse` kengaytirilgan; `GET /statistics/growth` |
| BE-11 | Bajarildi | `FarmAnalyticsService`, `FarmService.getFarmAnalytics` | `analyticsMatchesBossFinance` | `GET /farms/{id}/analytics` |
| BE-12 | Bajarildi | `AuditService` (yangi, `Propagation.MANDATORY`), `AuditAction`, `V12` (ip, user_agent, trigger) | `AuditLogIT` | `GET /audit-log` — sahifa, `AuditLogItem.metadata` JSON obyekt |
| BE-13 | Bajarildi | `V14__relative_file_urls.sql`, `FileController`, `common/FileUrls` | — (migratsiya NOTICE bilan ro'yxat chiqaradi) | Yuklash javobidagi `url` nisbiy: `/api/files/view/...` |
| BE-14 | Qisman — Q6 kutilmoqda | `FarmService.updateFarmStatus`, `OrderService.checkout`, `ClientService.getFarmsByFilter`, `CourierDispatchService` | `blockedFarmRejectsNewOrders` | Checkout: `409 FARM_BLOCKED / FARM_INACTIVE` (avval 400) |
| BE-15 | Bajarildi | `SuperAdminService.getRegionalDistribution/getOrdersByStatus` | `dashboardInvariant` | `RegionItem` qayta tuzilgan; `GET /statistics/orders-by-status` |
| BE-16 | Bajarildi | `User.activationStatus`, `BossItemResponse` | `pendingThenApproved` | `activationStatus` maydoni va filtri |
| BE-17 | Qisman — Q8 kutilmoqda | `FarmDtos.FarmCreateRequest`, `FarmService.createFarm` | `farmCreateValidation` | Majburiy: `address`, `latitude`, `longitude`; `city`, `district` qo'shildi |
| BE-18 | Qisman | `ApiException`, `GlobalExceptionHandler`, `ApiResponse` | `ErrorFormatAndRbacIT` | Xato: `{success, code, message, fieldErrors, timestamp}` |
| BE-19 | Qisman | `SecurityConfig`, `LoginRateLimiter`, `AuthService` | `ErrorFormatAndRbacIT` | Login xatosi `401 INVALID_CREDENTIALS` (avval 400), `429` |
| BE-20 | Bajarilmadi (DevOps) | faqat `TRUST_FORWARDED_HEADERS` sozlamasi | — | — |
| BE-21 | Bajarildi | `common/PasswordPolicy`, `SuperAdminService.changePassword` | `AuthSessionIT.changePassword` | `confirmPassword` majburiy; `/settings/*` — deprecated |
| BE-22 | Bajarildi | `common/PageResponse`; farms, bosses, audit-log, activation-requests | barcha `*IT` | `page,size,sort` + `{content,page,size,totalElements,totalPages}` |
| BE-23 | Qaror kutilmoqda (Q10) | — | — | — |
| BE-24 | Bajarilmadi (FE-29 bilan birga qilinadi) | — | — | — |
| BE-25 | Qaror kutilmoqda (Q1, Q2) | — | — | — |

Test natijasi: **36/36 integratsiya testi o'tdi** (`./mvnw test -Dtest='*IT'`, kerak: PostgreSQL `gidrogo_it` bazasi va Redis).
Eslatma: avvaldan mavjud `GidroGoCoreLogicTest.testOrderStatusEnum` yiqiladi (enum'da 12 ta holat, test 11 kutadi) — bu o'zgarishlarga aloqasi yo'q.

## 2. Deploydan oldin MAJBURIY

1. **V10 prod'da to'xtaydi** (audit: boss #33 → `farmId=10`, ferma #10 → `bossUserId=null`). Bu kutilgan xatti-harakat: migratsiya nomuvofiq juftlar ro'yxatini chiqaradi va standart qiymat qo'ymaydi. Egasi bilan kelishib `docs/sql/boss_farm_fix_template.sql` bo'yicha tuzating, so'ng qayta ishga tushiring. Ungacha ilova ishga tushmaydi — deployni shunga qarab rejalashtiring.
2. `SPRING_PROFILES_ACTIVE=prod` — aks holda Swagger ochiq qoladi.
3. `JWT_SECRET` va `TELEGRAM_BOT_TOKEN` ni muhit o'zgaruvchisi orqali bering va **almashtiring**: ikkalasi ham repoda ochiq turibdi (`application.yml` dagi standart qiymatlar).
4. Barcha foydalanuvchilar bir marta qayta kiradi: refresh tokenlar endi bazada tekshiriladi, eski tokenlar u yerda yo'q.
5. **Login'dagi yashirin parollar olib tashlandi.** Avval har qanday SUPER_ADMIN `Parol123!`, `Admin123!` yoki `admin` bilan kirib, xeshni shu parolga almashtira olardi. SuperAdmin o'zining haqiqiy parolini bilishiga ishonch hosil qiling.
6. Nginx o'rnatilgach `TRUST_FORWARDED_HEADERS=true` qiling (IP rate-limit va audit IP uchun).

## 3. Loyiha egasi qarorini kutayotgan savollar

- **Q6 (BE-14):** ferma bloklanganda SEARCHING/PREPARING buyurtmalar bekor qilinmadi. Dispatch endi bloklangan fermaga taklif bermaydi, shuning uchun bunday buyurtmalar qarorgacha SEARCHING/PREPARING holatida qoladi.
- **Q8 (BE-17):** test yozuvlari (#1–7, #9, #23, #24) va noto'g'ri koordinatalar (#4, #23, #24) o'zgartirilmadi.
- **Q10 (BE-23), Q1/Q2 (BE-25):** bajarilmadi.
- **Q11:** tavsiya qo'llandi — mavjud bosslar `APPROVED`. Rad etilsa, `V11` dagi bitta UPDATE bilan qaytariladi.
- **Q9:** tavsiya qo'llandi — idish balansi > 0 bo'lsa mijozni o'chirish `409 CLIENT_HAS_BOTTLE_BALANCE`.
- **Yangi savol — bekor qilish sababi (BE-09):** `orders` jadvalida sabab saqlanmaydi, shuning uchun `cancelReason` doim `null`. Ustun va bekor qilish oqimlarini o'zgartirish kerakmi?
- **Yangi savol — tushum ta'rifi:** `revenue` = `products_sum` (suv), `taraRevenue` = `tara_sum` alohida, foyda = `revenue − xarajat` (tara garov sifatida foydaga kirmaydi). Agar tara foydaga kirishi kerak bo'lsa, `RevenueService.profit` da bitta qatorni o'zgartirish kifoya.

## 4. Frontend uchun kontrakt o'zgarishlari

**Umumiy**
- Xato formati: `{success:false, code, message, fieldErrors:[{field,message}], timestamp}`. Status kodlari: 400 — tur/format, 422 — biznes validatsiyasi, 409 — ziddiyat, 403, 401, 429.
- Ro'yxatlar: `{content, page, size, totalElements, totalPages}`; parametrlar `page` (0 dan), `size` (1–100), `sort=field,asc|desc`.
- Pul — raqam (so'm), valyuta belgisi va formatlangan matnlar olib tashlandi.
- Sana parametrlari: `from`/`to` (YYYY-MM-DD, Asia/Tashkent). `startDate`/`endDate` dashboard, regions, audit-log va activation-requests'da deprecated sifatida qabul qilinadi.

**Auth**
- `POST /auth/login`: noto'g'ri login/parol → `401 INVALID_CREDENTIALS` (avval 400); `429 TOO_MANY_LOGIN_ATTEMPTS`; `403 USER_BLOCKED│CLIENT_BLOCKED`. Boss: `403 ACTIVATION_PENDING` (`data: {activationStatus, requestId, requestedAt}`) yoki `403 ACTIVATION_REJECTED` (`data: {rejectReason, decidedAt}`). Javobga `activationStatus` qo'shildi.
- `POST /auth/refresh`: refresh bir martalik (rotatsiya) — javobdagi yangi `refreshToken` ni saqlang. Xato → `401 REFRESH_TOKEN_INVALID│REFRESH_TOKEN_REVOKED`.
- `POST /auth/logout`: tana `{refreshToken, fcmToken}` (FE-07).

**SuperAdmin**
- `GET /dashboard?from&to`: yangi maydonlar — `inactiveFarms`, `activeOrders`, `completedOrders`, `cancelledOrders`, `returnedOrders`, `period{...}`, `growth{clientsPercent, ordersPercent, farmsPercent}` (null bo'lishi mumkin).
- Yangi: `GET /statistics/growth?from&to&granularity=DAY│WEEK│MONTH`, `GET /statistics/orders-by-status?from&to&farmId`.
- `GET /statistics/regions?from&to&region`: `RegionItem = {regionName, totalFarms, activeFarms, blockedFarms, inactiveFarms, percentage, ordersInPeriod, ordersPreviousPeriod, growthPercent}`. Olib tashlandi: `title`, `description`, `name`, `value`, `count`, `color`, `formattedText`. Hudud `farms.city` dan olinadi; city bo'sh bo'lsa — "Belgilanmagan".
- `GET /orders/daily-dynamics`: `totalAmount` → `orderedAmount`. `revenue`, `taraRevenue`, `totalRevenue`, `totalTaraRevenue` va `orderedAmount` faqat `farmId` bilan qaytadi. Olib tashlandi: `dynamics` (dublikat), `formattedDate`. `days` 1–366.
- `GET /farms` → sahifa (`search`, `status`, `region`). `FarmResponse` ga `city`, `district` qo'shildi.
- `GET /farms/{id}`: `statistics = {totalOrders, activeOrders, completedOrders, cancelledOrders, returnedOrders, successRatePercentage?, ordersGrowthPercentage?, totalRevenue, taraRevenue, expenses, profit, revenueGrowthPercentage?, monthlyGrowthPercent?, activeManagers, activeCouriers, clients}`. Olib tashlandi: `formattedRevenue`, barcha `*Text`, `jamiBuyurtmalar`, `faolJarayonda`, `tugallangan`, `jamiDaromad`. `ChartDataPoint = {day, dayName, date, endDate, ordersCount, completedOrders, revenue, taraRevenue}` (`buyurtmalar`, `daromad` olib tashlandi).
- Yangi: `GET /farms/{id}/analytics?from&to&granularity`.
- `PATCH /farms/{id}/status`: tanaga ixtiyoriy `reason` qo'shildi.
- `POST /farms`: `address`, `latitude` (37–46), `longitude` (55–74) majburiy; `bossPassword` 8–50 belgi, harf + raqam (422); `409 PHONE_ALREADY_EXISTS│FARM_NAME_EXISTS`.
- `GET /bosses?search&status&farmId&activationStatus&page&size&sort`: `summary = {total, active, blocked, recentlyAdded}` (avval `totalBosses`… va `*Text`); `bosses` (dublikat) olib tashlandi — `content` ishlating. `BossItemResponse`: `activationStatus` qo'shildi; `formattedCreatedAt` olib tashlandi; `avatarUrl` yo'q bo'lsa `null` (ui-avatars generatsiyasi yo'q).
- `PUT /bosses/{id}`: `404 FARM_NOT_FOUND`, `409 FARM_ALREADY_HAS_BOSS`, `409 PHONE_ALREADY_EXISTS`, `422 PASSWORD_POLICY_VIOLATION`; `status` faqat `ACTIVE│BLOCKED`.
- `GET /activation-requests?status&from&to&page&size&sort` → `{summary{pending,approved,rejected}, content, page, size, totalElements, totalPages}`; `decidedBy` qo'shildi.
- `POST /activation-requests/{id}/decision`: `422 REJECT_REASON_REQUIRED` (field `rejectReason`, 10–500), `409 REQUEST_ALREADY_DECIDED`.
- `GET /audit-log?action(ko'p)&entityType&from&to&search&page&size&sort` → sahifa. `AuditLogItem = {id, actorId, actorName, action, entityType, entityId, metadata{old,new,...}, ipAddress, userAgent, createdAt}`.
- Profil: asosiy yo'llar `GET/PUT /profile`, `POST /change-password`; `/settings/*` — deprecated. `confirmPassword` majburiy; xatolar 422 (`CURRENT_PASSWORD_INVALID`, `PASSWORD_CONFIRMATION_MISMATCH`, `PASSWORD_POLICY_VIOLATION`). Parol almashtirilgach barcha refresh tokenlar bekor qilinadi — qayta kirish kerak. `SuperAdminProfileResponse` dan `roleTitle`, `formattedCreatedAt` olib tashlandi; `avatarUrl` null bo'lishi mumkin.
- Yangi: `/clients/summary`, `/clients`, `/clients/{id}`, `PATCH /clients/{id}/status`, `DELETE /clients/{id}`, `/clients/{id}/orders`, `/clients/{id}/payments`, `/orders`, `/orders/{id}`.

**Boss / Manager**
- `/boss/finance/summary`, `/manager/finance/summary`: `totalIncome` endi faqat COMPLETED buyurtmalarning suv qismi (avval `finance_transactions` INCOME, tara bilan). Yangi: `taraIncome`, `completedOrders`. `today` — Toshkent kuni (avval UTC).
- `/boss/finance/chart`: o'ylab topilgan "baza" qiymatlari va `xarajat = 32%` olib tashlandi — endi real xarajatlar.
- `/boss|manager/dashboard`: `incomeChangePercent`, `ordersChangePercent`, `clientsChangePercent` endi real hisob yoki `null` (avval 14.5 / 8.2 / 5.1).
- `/boss/sales/products-summary`: faqat COMPLETED sotuvlar, `stock` — ombordagi haqiqiy qoldiq; soxta mahsulotlar qaytarilmaydi.
- `/api/courier/**` faqat COURIER, `/api/client/**` faqat CLIENT (SUPER_ADMIN endi 403).
- `GET /api/client/farms*` — `status` parametridan qat'i nazar faqat ACTIVE fermalar.

## 5. Qamrovdan tashqarida qolgan topilmalar

- Boss panelida hali ham soxta ma'lumotlar bor (`BossService`): texnik pasport (INN, litsenziya, laboratoriya tahlili), `getBusinessGrowth` egri chiziqlari va `18.4 / 91.2`, `getClientSegments` (1248 mijoz, 83/13% ulush), `getSalesRealizationSummary` (`12.8%`, "18.9L Kapsula" 60%), `deliveryDisciplinePercent = 98.5`, `regionName = "Markaziy hudud"`. Bular SuperAdmin vazifalari ro'yxatida yo'q edi, shuning uchun o'zgartirilmadi.
- Boss xodimni (`StaffService.deleteStaff`) qattiq o'chiradi. Endi ledger va status tarixi o'zgarmas bo'lgani uchun, tarixi bor kuryerni o'chirish `409 DATA_CONFLICT` qaytaradi. Avval bunday o'chirish kassa yozuvlarini kaskad bilan yo'q qilardi. Tavsiya: soft delete.
- `/api/files/view/**` tokensiz ochiq (GG-26) — o'zgartirilmadi.
- Ikkinchi hujjatdagi Manager/Boss filtr so'rovlari (dashboard `period`, transactions/handovers/clients filtrlari, boss `staff/summary`, `clients` segmentlari) bajarilmadi. SuperAdmin qismi (audit-log, dashboard, regions, farms, activation-requests) bajarildi. Audit-log uchun `action` qiymatlari BE-12 bo'yicha (`FARM_BLOCKED`, …); u hujjatdagi `LOGIN/FERMA_TIZIMI` va `status=SUCCESS│FAILED` ro'yxati qo'llanmadi: muvaffaqiyatsiz amallar tranzaksiya bilan birga bekor qilinadi va audit'ga tushmaydi.
- BE-18: boshqa modullardagi eski `BadRequestException` hali ham 400 qaytaradi (biznes validatsiyasi bo'lsa ham). Yangi va o'zgartirilgan kod 422 ishlatadi.
- BE-19 / SEC-07 (minimal maydonlar): faqat yangi va o'zgartirilgan SuperAdmin javoblari qisqartirildi.
