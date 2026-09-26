# GIDROGO — BACKEND API O'ZGARTIRISHLAR VA TALABLAR HUJJATI
**Hujjat turi**: Backend Engineering & API Integration Specification  
**Loyiha**: GidroGo Kuryer Mobil Ilovasi (`com.gidrogo.gidrogo_driver`)  
**Backend**: Spring Boot Multi-farm Marketplace Backend API (`http://169.58.215.170:8080`)  
**Holat**: Rasmiy talabnoma (Hand-off Specification)  

---

## MUNDARIJA
1. [Swagger'da mavjud, ammo o'zgartirish / kengaytirish kerak bo'lgan API'lar](#1-swaggerda-bor-ammo-ozgartirish--kengaytirish-kerak-bolgan-apilar)
   - 1.1 `GET /api/courier/profile` (Avtomobil, guvohnoma va haydovchi reytingi)
   - 1.2 `GET /api/courier/dashboard` (Kunlik maqsadli reja ko'rsatkichlari)
   - 1.3 `GET /api/courier/orders` (Pagination va sana oraliqlari filtrlari)
2. [Mobil ilovaga kerak, ammo Swagger'da mavjud bo'lmagan yangi API'lar](#2-mobil-ilovaga-kerak-ammo-swaggerda-mavjud-bolmagan-apilar)
   - 2.1 Modul: Autentifikatsiya va Sessiyani Yakunlash (`POST /api/auth/logout`)
   - 2.2 Modul: Kuryer Bildirishnomalari Qutisi (Inbox)
     - 2.2.1 `GET /api/courier/notifications` (Xabarlar ro'yxati)
     - 2.2.2 `PATCH /api/courier/notifications/{id}/read` (Bitta xabarni o'qilgan qilish)
     - 2.2.3 `POST /api/courier/notifications/read-all` (Barcha xabarlarni o'qilgan qilish)
     - 2.2.4 `GET /api/courier/notifications/unread-count` (O'qilmaganlar hisoblagichi)
   - 2.3 Modul: Mashinaga Yuklashlar Tarixi Logi (`GET /api/courier/stock/history`)
   - 2.4 Modul: Kuryer Shaxsiy Ma'lumotlarini Yangilash (`PUT /api/courier/profile`)
   - 2.5 Modul: Mijoz Bosh Sahifasi Slayder va Bannerlari (`GET /api/client/banners`)
3. [Xulosa va Prioritetlar Jadvali](#3-xulosa-va-prioritetlar-jadvali)

---

# 1. SWAGGER'DA BOR, AMMO O'ZGARTIRISH / KENGAYTIRISH KERAK BO'LGAN API'LAR

Ushbu endpointlar backend Swagger/OpenAPI hujjatlarida mavjud, biroq ularning hozirgi javobi (response) mobil ilovaning UI/UX talablarini to'liq qoplamaydi. Yangi endpoint yaratish talab qilinmaydi — **mavjud endpoint response sxemasiga yangi fieldlarni qo'shish (Schema Extension)** kifoya qiladi.

---

### 1.1 `GET /api/courier/profile` — Kuryer Profili va Avtomobil Ma'lumotlari

#### Tegishli mobil ekran:
- `ProfilePage` (`lib/features/profile/presentation/pages/profile_page.dart`)
- `VehicleStockPage` (`lib/features/stock/presentation/pages/vehicle_stock_page.dart`)

#### Hozirgi Swagger holati:
Endpoint kuryer F.I.O, telefon raqami, joriy statusi va bugungi tushumlarini qaytaradi (`CourierProfileResponse`).

#### Hozirgi Response (Swagger):
```json
{
  "id": 7,
  "fullName": "Jamshid Qodirov",
  "phone": "+998901234567",
  "status": "ONLINE",
  "isOnline": true,
  "farmId": 1,
  "farmName": "GidroGo Tashkent",
  "latitude": 41.2856,
  "longitude": 69.2034,
  "lastSeenAt": "2026-09-19T10:50:00Z",
  "todayCompleted": 12,
  "todayCash": 450000.0,
  "todayOnline": 280000.0,
  "todayTotal": 730000.0,
  "vehicleStock": 38.0
}
```

#### Qaysi datalarni o'zgartirish / qo'shish kerak:
Mobil ilovadagi haydovchi profili kartasi va "Mashina ma'lumotlari" bloki uchun quyidagi ma'lumotlar yetishmayapti:
1. `vehicleModel` (string) — Avtomobil rusumi (masalan: `"Chevrolet Damas"` yoki `"Labo"`).
2. `vehiclePlateNumber` (string) — Avtomobil davlat raqami (masalan: `"01 A 777 BA"`).
3. `maxCapacity` (int) — Mashinaning maksimal suv sig'imi (masalan: `50` yoki `40 dona 19L`).
4. `driverLicenseNumber` (string) — Haydovchilik guvohnomasi raqami (masalan: `"AA 1234567"`).
5. `passportSerial` (string) — Pasport seriyasi va raqami (masalan: `"AB 7654321"`).
6. `rating` (double) — Mijozlar tomonidan berilgan haydovchi reytingi (masalan: `4.95`).
7. `avatarUrl` (string, nullable) — Haydovchi profil rasmining manzili.

#### Tavsiya etiladigan Kengaytirilgan Response (200 OK):
```json
{
  "success": true,
  "message": "Profil ma'lumotlari",
  "data": {
    "id": 7,
    "fullName": "Jamshid Qodirov",
    "phone": "+998901234567",
    "avatarUrl": "/api/files/view/2026/09/19/courier_7.jpg",
    "status": "ONLINE",
    "isOnline": true,
    "farmId": 1,
    "farmName": "GidroGo Tashkent",
    "rating": 4.9,
    "latitude": 41.2856,
    "longitude": 69.2034,
    "lastSeenAt": "2026-09-19T10:50:00Z",
    "todayCompleted": 12,
    "todayCash": 450000.0,
    "todayOnline": 280000.0,
    "todayTotal": 730000.0,
    "vehicleStock": 38.0,
    "vehicle": {
      "model": "Chevrolet Damas",
      "plateNumber": "01 A 777 BA",
      "maxCapacity": 50,
      "licenseNumber": "AA 1234567",
      "passportSerial": "AB 7654321"
    }
  },
  "timestamp": "2026-09-19T11:00:00Z"
}
```
*Eslatma: Agar `vehicle` ob'ektini alohida qo'yish noqulay bo'lsa, to'g'ridan-to'g'ri ildiz darajasida `vehicleModel`, `vehiclePlateNumber`, `maxCapacity`, `licenseNumber` sifatida bersa ham mobil ilova qabul qiladi.*

---

### 1.2 `GET /api/courier/dashboard` — Kunlik Maqsadli Reja Ko'rsatkichi

#### Tegishli mobil ekran:
- `DashboardPage` (`lib/features/orders/presentation/pages/dashboard_page.dart`)
- `ProfilePage` (`lib/features/profile/presentation/pages/profile_page.dart` -> "Bugungi reja: 12 / 15 ta" kartasi)

#### Hozirgi Swagger holati:
`CourierDashboardResponse` hozirda faqat kuryerga biriktirilgan va yetkazilgan buyurtmalarni qaytaradi.

#### Hozirgi Response (Swagger):
```json
{
  "date": "2026-09-19",
  "assignedOrders": 15,
  "completedOrders": 12,
  "problemOrders": 0,
  "totalRevenue": 730000.0,
  "cashRevenue": 450000.0,
  "onlineRevenue": 280000.0,
  "loadedBottles": 40.0,
  "soldBottles": 24.0,
  "remainingBottles": 16.0,
  "recentOrders": []
}
```

#### Qaysi datalarni o'zgartirish / qo'shish kerak:
1. `targetOrdersCount` (int) — Kuryer uchun belgilangan kunlik maqsadli buyurtmalar soni (masalan: `15`).
2. `targetRevenue` (double, ixtiyoriy) — Kunlik daromad rejasi (masalan: `1000000.0`).

#### Tavsiya etiladigan Kengaytirilgan Response (200 OK):
```json
{
  "success": true,
  "data": {
    "date": "2026-09-19",
    "targetOrdersCount": 15,
    "assignedOrders": 15,
    "completedOrders": 12,
    "problemOrders": 0,
    "targetRevenue": 1000000.0,
    "totalRevenue": 730000.0,
    "cashRevenue": 450000.0,
    "onlineRevenue": 280000.0,
    "loadedBottles": 40.0,
    "soldBottles": 24.0,
    "remainingBottles": 16.0,
    "recentOrders": []
  },
  "timestamp": "2026-09-19T11:00:00Z"
}
```

---

### 1.3 `GET /api/courier/orders` — Pagination va Sana Filtrlari

#### Tegishli mobil ekran:
- `OrdersPage` (`lib/features/orders/presentation/pages/orders_page.dart` -> "Barchasi" va "Yetkazilgan" tablari)

#### Hozirgi Swagger holati:
Faqat bitta query parametri mavjud: `status` (`ASSIGNED`, `ON_THE_WAY`, `DELIVERED`, `COMPLETED`, `PROBLEM`).  
Natija: Cheklovsiz butun ro'yxat (`List<OrderResponse>`).

#### Nima uchun o'zgartirish kerak:
Kuryer bir necha oy ishlaganidan so'ng yetkazilgan buyurtmalari soni mingdan oshadi. Cheklovsiz barcha yozuvlarni bir vaqtda qaytarish tarmoq trafigini va telefon xotirasini band qilib, ilovaning qotishiga (lag) olib keladi.

#### Qaysi Query parametrlarni qo'shish kerak:
1. `page` (int, default: 0) — Sahifa raqami.
2. `size` (int, default: 20) — Bitta sahifadagi elementlar soni.
3. `startDate` (string, ISO-8601 yoki `YYYY-MM-DD`, ixtiyoriy) — Boshlanish sanasi.
4. `endDate` (string, ISO-8601 yoki `YYYY-MM-DD`, ixtiyoriy) — Tugash sanasi.

#### Tavsiya etiladigan Response (Sahifalangan / Paginated):
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 10284,
        "orderNumber": "ORD-10284",
        "clientName": "Ali Valiyev",
        "clientPhone": "+998901112233",
        "status": "DELIVERED",
        "totalSum": 120000.0,
        "deliveryAddress": "Yunusobod 4-mavze, 12-uy",
        "deliveredAt": "2026-09-19T09:40:00Z"
      }
    ],
    "totalElements": 142,
    "totalPages": 8,
    "currentPage": 0,
    "pageSize": 20
  }
}
```

---

# 2. MOBIL ILOVAGA KERAK, AMMO SWAGGER'DA MAVJUD BO'LMAGAN APILAR

Quyidagi modullar va endpointlar mobil ilovada foydalanuvchi interfeysi (UI) va biznes-mantiq sifatida mavjud, biroq backend Swagger/OpenAPI spetsifikatsiyasida **umuman mavjud emas**. Ular noldan yaratilishi shart.

---

## 2.1 MODUL: AUTENTIFIKATSIYA VA SESSIYANI YAKUNLASH (Auth & Session)

### 🔴 Endpoint: `POST /api/auth/logout`

- **Tegishli Ekran**: `ProfilePage` (`lib/features/profile/presentation/pages/profile_page.dart` -> "Tizimdan chiqish" tugmasi)
- **Maqsadi**: Kuryer smenani yakunlab tizimdan chiqqanida, uning qurilmasiga boshqa yangi buyurtmalar haqida push-bildirishnomalar bormasligini ta'minlash va serverdagi JWT Refresh tokenni bekor (revoke/blacklist) qilish.
- **Xavfsizlik darajasi**: 🔴 **CRITICAL** (Agar bu endpoint bo'lmasa, ishdan chiqqan haydovchining shaxsiy telefoniga boshqa navbatchi kuryerlarning buyurtmalari, mijozlar telefon raqamlari va manzillari kelaveradi).
- **HTTP Method**: `POST`
- **Endpoint URL**: `/api/auth/logout`
- **Authentication**: `Bearer <JWT_ACCESS_TOKEN>`
- **Request Body**:
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "fcmToken": "eK3_Z9x..."
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Sessiya muvaffaqiyatli yakunlandi va qurilma tokeni o'chirildi",
  "data": true,
  "timestamp": "2026-09-19T11:00:00Z"
}
```
- **HTTP Status Codes**: `200 OK`, `400 Bad Request`, `401 Unauthorized`

---

## 2.2 MODUL: KURRIER BILDIRISHNOMALARI QUTISI (In-App Notifications Inbox)

Backendda `POST /api/courier/device-token` (FCM token saqlash) mavjud, ammo ilova ichida bildirishnomalar tarixini ko'rsatuvchi va boshqaruvchi API mavjud emas.

### 🔴 Endpoint 2.2.1: `GET /api/courier/notifications` — Bildirishnomalar Ro'yxati

- **Tegishli Ekran**: `NotificationsPage` (`lib/features/notifications/presentation/pages/notifications_page.dart`)
- **Maqsadi**: Haydovchiga yuborilgan shaxsiy va tizimli xabarlarni (yangi buyurtmalar, ombordan tasdiqlangan yuklashlar, dispetcher xabarlari) vaqt bo'yicha saralangan ro'yxatini olish.
- **HTTP Method**: `GET`
- **Endpoint URL**: `/api/courier/notifications`
- **Authentication**: `Bearer <JWT_ACCESS_TOKEN>`
- **Query Parameters**:
  - `page` (int, default: 0) — Sahifa indeksi
  - `size` (int, default: 20) — Sahifadagi xabarlar soni
  - `unreadOnly` (bool, default: false) — Faqat o'qilmagan xabarlar filtri
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Bildirishnomalar ro'yxati",
  "data": {
    "content": [
      {
        "id": 101,
        "title": "Yangi buyurtma tayinlandi: #ORD-10285",
        "message": "1.8 km masofada yangi buyurtma kelib tushdi. Qabul qilish uchun 30 soniya vaqtingiz bor.",
        "type": "ORDER_ASSIGNED",
        "referenceId": 10285,
        "isRead": false,
        "createdAt": "2026-09-19T10:45:00Z"
      },
      {
        "id": 100,
        "title": "Ombor zaxirasi yangilandi: +12 dona 19L",
        "message": "Bugun 11:30 da Markaziy bazada yuklash amali muvaffaqiyatli tasdiqlandi.",
        "type": "STOCK_RESTOCKED",
        "referenceId": null,
        "isRead": true,
        "createdAt": "2026-09-19T06:30:00Z"
      },
      {
        "id": 99,
        "title": "Dispetcher xabari: Yo'l ta'miri",
        "message": "Mustaqillik shoh ko'chasida ta'mirlash ishlari bormoqda, aylanma yo'ldan foydalaning.",
        "type": "BROADCAST",
        "referenceId": null,
        "isRead": true,
        "createdAt": "2026-09-18T16:00:00Z"
      }
    ],
    "totalElements": 3,
    "totalPages": 1,
    "currentPage": 0,
    "unreadCount": 1
  },
  "timestamp": "2026-09-19T11:00:00Z"
}
```
- **Prioritet**: 🟠 **HIGH**

---

### 🔴 Endpoint 2.2.2: `PATCH /api/courier/notifications/{id}/read` — Bitta Xabarni O'qilgan Qilish

- **Tegishli Ekran**: `NotificationsPage` (Bildirishnoma kartasi ustiga bosilganda)
- **Maqsadi**: Tanlangan bildirishnoma ochilganda uning `isRead` holatini `true` ga o'tkazish.
- **HTTP Method**: `PATCH`
- **Endpoint URL**: `/api/courier/notifications/{id}/read`
- **Path Parameters**: `id` (int64) — Bildirishnoma ID raqami
- **Authentication**: `Bearer <JWT_ACCESS_TOKEN>`
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Bildirishnoma o'qildi deb belgilandi",
  "data": {
    "id": 101,
    "isRead": true
  }
}
```
- **Prioritet**: 🟡 **MEDIUM**

---

### 🔴 Endpoint 2.2.3: `POST /api/courier/notifications/read-all` — Barcha Xabarlarni O'qilgan Qilish

- **Tegishli Ekran**: `NotificationsPage` (AppBar'dagi `done_all_rounded` tugmasi)
- **Maqsadi**: Kuryer barcha xabarlarni birdaniga o'qilgan deb belgilaganda ishlaydi.
- **HTTP Method**: `POST`
- **Endpoint URL**: `/api/courier/notifications/read-all`
- **Authentication**: `Bearer <JWT_ACCESS_TOKEN>`
- **Request Body**: Kerak emas
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Barcha bildirishnomalar o'qildi deb belgilandi",
  "data": true
}
```
- **Prioritet**: 🟡 **MEDIUM**

---

### 🔴 Endpoint 2.2.4: `GET /api/courier/notifications/unread-count` — O'qilmagan Xabarlar Soni

- **Tegishli Ekran**: `MainShellPage`, `DashboardPage`, `VehicleStockPage` (AppBar qo'ng'iroqcha ustidagi qizil belgi/badge: `"2"`)
- **Maqsadi**: Har bir sahifa ochilganda butun ro'yxatni tortmasdan, faqatgina o'qilmagan xabarlar sonini tezkor olish.
- **HTTP Method**: `GET`
- **Endpoint URL**: `/api/courier/notifications/unread-count`
- **Authentication**: `Bearer <JWT_ACCESS_TOKEN>`
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "unreadCount": 2
  }
}
```
- **Prioritet**: 🟡 **MEDIUM**

---

## 2.3 MODUL: MASHINAGA YUKLASHLAR TARIXI (Vehicle Stock History)

Hozirgi Swagger'da faqat `POST /api/courier/stock/restock` (bir martalik yuklash) va `GET /api/courier/stock` (joriy qoldiq) mavjud. Ammo yuklashlar jurnali yo'q.

### 🔴 Endpoint: `GET /api/courier/stock/history`

- **Tegishli Ekran**: `VehicleStockPage` (`lib/features/stock/presentation/pages/vehicle_stock_page.dart` -> "So'nggi yuklashlar tarixi" bo'limi)
- **Maqsadi**: Haydovchi bazadan kun davomida yoki oldingi kunlarda qancha to'la suv yuklaganini, ombor joylashuvini va vaqtini tekshirishi uchun jurnal.
- **HTTP Method**: `GET`
- **Endpoint URL**: `/api/courier/stock/history`
- **Authentication**: `Bearer <JWT_ACCESS_TOKEN>`
- **Query Parameters**:
  - `page` (int, default: 0)
  - `size` (int, default: 20)
  - `date` (string, `YYYY-MM-DD`, ixtiyoriy — masalan: `2026-09-19`)
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Yuklashlar tarixi",
  "data": {
    "content": [
      {
        "id": 54,
        "productId": 1,
        "productName": "19L Suv Katta",
        "quantity": 12,
        "location": "Markaziy baza",
        "warehouseManagerName": "Akmal Rahimov",
        "restockedAt": "2026-09-19T11:30:00Z"
      },
      {
        "id": 51,
        "productId": 3,
        "productName": "5L Suv Standart",
        "quantity": 20,
        "location": "Markaziy baza",
        "warehouseManagerName": "Akmal Rahimov",
        "restockedAt": "2026-09-19T08:15:00Z"
      }
    ],
    "totalElements": 2,
    "totalPages": 1,
    "currentPage": 0
  },
  "timestamp": "2026-09-19T11:00:00Z"
}
```
- **Prioritet**: 🟠 **HIGH**

---

## 2.4 MODUL: KURRIER SHAXSIY MA'LUMOTLARINI TAHRIRLASH (Profile Update)

### 🔴 Endpoint: `PUT /api/courier/profile`

- **Tegishli Ekran**: `ProfilePage` (`lib/features/profile/presentation/pages/profile_page.dart` -> "Shaxsiy ma'lumotlar" kartasidagi "O'zgartirish" tugmasi)
- **Maqsadi**: Kuryer o'zining telefon raqami, paroli yoki avtomobil ma'lumotlari o'zgarganda ularni yangilay olishi.
- **HTTP Method**: `PUT`
- **Endpoint URL**: `/api/courier/profile`
- **Authentication**: `Bearer <JWT_ACCESS_TOKEN>`
- **Request Body**:
```json
{
  "phone": "+998901234567",
  "currentPassword": "oldPassword123",
  "newPassword": "newPassword456",
  "vehicleModel": "Chevrolet Damas",
  "vehiclePlateNumber": "01 A 777 BA"
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Profil ma'lumotlari muvaffaqiyatli yangilandi",
  "data": {
    "id": 7,
    "fullName": "Jamshid Qodirov",
    "phone": "+998901234567",
    "vehicleModel": "Chevrolet Damas",
    "vehiclePlateNumber": "01 A 777 BA"
  }
}
```
- **Prioritet**: 🟡 **MEDIUM**

---

## 2.5 MODUL: MIJOZ BOSH SAHIFASI SLAYDER VA BANNERLARI (Client Promo Banners)

Hozirgi kunda mijoz ilovasi bosh sahifasidagi `"Toza va mineralli suv bir zumda eshigingizda!"` (badge: `"BEPUL YETKAZIB BERISH"`) bloki to'liq statik (hardcoded). Ushbu blogni dinamik boshqariladigan karuselga aylantirish uchun quyidagi API zarur. Batafsil alohida hujjat: `BACKEND_BANNER_API_REQUIREMENTS.md`.

### 🔴 Endpoint: `GET /api/client/banners`

- **Tegishli Ekran**: `CustomerHomePage` (`lib/features/customer/presentation/pages/customer_home_page.dart` -> Promo Banner bloki)
- **Maqsadi**: Mijoz bosh sahifani ochganda faol reklama, aksiya va bepul yetkazib berish bannerlarini yuklash.
- **HTTP Method**: `GET`
- **Endpoint URL**: `/api/client/banners`
- **Authentication**: **Ochiq (Public / PermitAll)** — avtorizatsiyadan o'tmagan mijozlar uchun ham ochiq.
- **Query Parameters**:
  - `farmId` (int, ixtiyoriy) — Ma'lum bir fermaga tegishli aksiyalar filtri
  - `type` (string, default: `HOME_SLIDER`) — Banner turi
- **Response (200 OK)**:
```json
{
  "success": true,
  "message": "Faol bannerlar muvaffaqiyatli yuklandi",
  "data": [
    {
      "id": 1,
      "badgeText": "BEPUL YETKAZIB BERISH",
      "title": "Toza va mineralli suv bir zumda eshigingizda!",
      "subtitle": "Eng yaqin fermalardan 20 daqiqada",
      "imageUrl": null,
      "gradientStart": "#0284C7",
      "gradientEnd": "#1D61F2",
      "iconName": "water_drop",
      "actionType": "NONE",
      "actionValue": null,
      "sortOrder": 1,
      "isActive": true
    }
  ]
}
```
- **Prioritet**: 🟠 **HIGH**

---

# 3. XULOSA VA PRIORITETLAR JADVALI

### Umumiy Jamlama:

| № | Turi | Endpoint | Method | Maqsadi | Prioritet |
|---|---|---|---|---|---|
| 1 | **Mavjud (Kengaytirish)** | `/api/courier/profile` | `GET` | Avtomobil rusumi, raqami, guvohnoma va reytingni qo'shish | 🟠 HIGH |
| 2 | **Mavjud (Kengaytirish)** | `/api/courier/dashboard` | `GET` | Kunlik maqsadli reja (`targetOrdersCount`) qo'shish | 🟡 MEDIUM |
| 3 | **Mavjud (Kengaytirish)** | `/api/courier/orders` | `GET` | `page`, `size`, `startDate`, `endDate` filtrlarini qo'shish | 🟠 HIGH |
| 4 | **Yangi Endpoint** | `/api/auth/logout` | `POST` | Chiqishda sessiya va FCM push-tokenni bekor qilish | 🔴 CRITICAL |
| 5 | **Yangi Endpoint** | `/api/courier/notifications` | `GET` | Bildirishnomalar qutisi ro'yxatini sahifalab berish | 🟠 HIGH |
| 6 | **Yangi Endpoint** | `/api/courier/notifications/{id}/read` | `PATCH` | Bitta bildirishnomani o'qildi deb belgilash | 🟡 MEDIUM |
| 7 | **Yangi Endpoint** | `/api/courier/notifications/read-all` | `POST` | Barcha bildirishnomalarni birdan o'qildi qilish | 🟡 MEDIUM |
| 8 | **Yangi Endpoint** | `/api/courier/notifications/unread-count`| `GET` | AppBar qo'ng'iroqcha soni uchun o'qilmaganlar hisoblagichi | 🟡 MEDIUM |
| 9 | **Yangi Endpoint** | `/api/courier/stock/history` | `GET` | Mashinaga suv yuklashlar tarixi jurnali | 🟠 HIGH |
| 10 | **Yangi Endpoint** | `/api/courier/profile` | `PUT` | Kuryer shaxsiy va avtomobil ma'lumotlarini tahrirlash | 🟡 MEDIUM |
| 11 | **Yangi Endpoint** | `/api/client/banners` | `GET` | Mijoz bosh sahifasi promo-aksiyalar slayderi | 🟠 HIGH |

---
*Ushbu hujjat GidroGo Flutter mobil ilovasi arxitekturasi va Swagger/OpenAPI v2.0.0 ma'lumotlari asosida tayyorlandi hamda to'g'ridan-to'g'ri Backend muhandislari uchun texnik topshiriq (Tech Spec) bo'lib xizmat qiladi.*
