# 18–22-bandlar bo'yicha javob

**Holat:** tuzatishlar **prodga chiqdi** (`https://169.58.215.170`, commit `e22c65c`, 2026-11-02). Yangi spec: `docs/openapi.json`.
FCM prodda ishga tushdi (`Firebase initialized`), lekin haqiqiy push yetib borishi hali sinovdan o'tmagan.

| Band | Holat |
|---|---|
| 18 | Yopildi. Deploydan keyin `curl` bilan tekshirasiz |
| 19 | Taklif qabul qilindi ("ha"), farqlari quyida |
| 20 | Ochiq: nginx log kerak |
| 21 | Koddagi nuqsonlar tuzatildi. Aynan 29-oktabr buyurtmasining sababi uchun prod ma'lumoti kerak |
| 22 | Yopildi, 6-savol javoblari quyida. `4.` bo'limdagi FCM prodda tekshirilmagan |

---

## 18-band — `/ws/client`

- `CLIENT` faqat bir martalik ticket bilan ulanadi. Ticket yo'q, soxta, muddati o'tgan yoki ishlatilgan bo'lsa → `401`.
- Mijoz JWT bilan to'g'ridan-to'g'ri ulana olmaydi → `401`. **Eski mobil mijoz ilovasi JWT bilan ulansa, ishlamay qoladi.**
- Boss, Manager, SuperAdmin va Kuryer `/ws/client` ga JWT bilan ulanadi (ularda ticket endpoint'i yo'q). Akkaunt `ACTIVE` bo'lishi shart.
- Ticket atomik o'qiladi va o'chiriladi. Avvalgi kod `GETDEL` ishlatardi: Redis 6.2 dan eski versiyada har qanday ticket `500` berardi.
- Obuna tekshiruvi: begona buyurtma, mijoz, kuryer yoki ferma kanali, shuningdek ro'yxatda yo'q har qanday kanal → STOMP `ERROR` va uzilish.
- `/ws/courier`: oddiy WebSocket, obuna tushunchasi yo'q. Xabar faqat token egasining sessiyasiga boradi.

Test bilan tasdiqlangan: soxta ticket, ticket'ni ikkinchi marta ishlatish, begona buyurtmaga obuna, bloklangan foydalanuvchi.
Tekshirilmagan: "A xabar oladi, B olmaydi" oqimi (siz o'tkazadigan 4-qabul mezoni).

## 19-band — WebSocket shartnomasi

**Ha**, quyidagi farqlar bilan:

- Server `heart-beat: 10000,10000` ni qo'llab-quvvatlaydi.
- Koordinata har buyurtma uchun 10 soniyada ko'pi bilan bir marta yuboriladi.
- `distanceMeters` — kasr son (`1240.5`). Xabarda qo'shimcha `speedKmh` bor.
- `SEARCHING` va `PREPARING` holatlari ham `/user/queue/orders` ga yuboriladi.
- `/topic/order/{id}/courier-location` dan `courierId`, telefon va ism olib tashlandi.
- Mijozning `OrderResponse` xabarlari (`/topic/order/{id}`, `/topic/client/{id}/orders`) o'zgarmadi: u yerda biriktirilgan kuryer ma'lumoti bor. Mini App `/user/queue/orders` ni ishlatsa, ular unga kelmaydi.

## 20-band — 18:58 dagi 502

Shu vaqtga mos commit yo'q: eng yaqinlari 15:54 va 19:02 (commit vaqti, deploy vaqti emas). Qo'lda qayta ishga tushirilgan bo'lishi mumkin.
Aniq javob uchun serverda: `journalctl -u gidrogo --since "2026-10-29 18:50" --until "2026-10-29 19:05"` va nginx `error.log`.

## 21-band — PREPARING

**3-savol (koddan).**

- (a) GPS'i yo'q kuryer **chetlab o'tilmaydi**: ETA ferma koordinatasidan hisoblanadi. Eski GPS ham shunday qabul qilinadi. TZ'dagi `location_stale_sec = 120` kodda e'lon qilingan, lekin **ishlatilmaydi**. Demak, ilovadagi GPS yetishmasligi taklif kelmaganining sababi **emas**.
- (b) Shu sabab GPS uchun alohida `preparing_reason` yo'q.
- (c) Haqiqiy shartlar: akkaunt `ACTIVE` (yangi, blok kuryerga taklif bormasin), online, cooldown'da emas (rad etgan yoki javob bermagan kuryer shu buyurtma uchun 10 daqiqa), boshqa `PENDING` taklifi yo'q, faol buyurtmalar < `max_active_orders` (standart 2), har mahsulot bo'yicha erkin zaxira yetarli, ikkinchi buyurtma yakordan `second_order_radius_m` (standart 1500 m) ichida. `coverageAreas`, smena, mashina va reyting shart **emas**.

**`preparing_reason` qiymatlari:** `NO_FREE_COURIER`, `NO_STOCK`, `ALL_DECLINED`.

**Topilgan va tuzatilgan nuqsonlar:**

- Javob bermagan kuryer ham `NO_STOCK` deb ko'rsatilardi: sabab noto'g'ri edi. `ALL_DECLINED` amalda deyarli chiqmasdi.
- Cooldown'dan keyin bir kuryerga qayta taklif borganda accept/reject `500` berardi (`courier_offers` da bir buyurtma+kuryer uchun ikki yozuv).
- `PREPARING` har 60 soniyada `order_status_history` ga qayta yozilardi (bu jadvaldan o'chirib bo'lmaydi).
- Kuryer biriktirilmagan buyurtma bekor qilinsa, taklif ochiq qolib, ovozli bildirishnoma 35 soniya davom etardi. Endi `OFFER_CANCELLED` darhol ketadi.
- Har kuryer uchun rad sababi logga yoziladi: `DEBUG`, `orderId, courierId, failedCondition`. Yoqish: `LOGGING_LEVEL_UZ_GIDROGO_MODULES_COURIER=DEBUG`.

**Eng ehtimolli sabab** — mashinada zaxira yo'qligi yoki kuryer birinchi taklifga javob bermagani (keyin 10 daqiqa cooldown). Bu faqat taxmin: prod ma'lumotisiz tasdiqlab bo'lmaydi.

**1–2-savollar uchun prod so'rovlari** (faqat o'qiydi):

```sql
SELECT id, farm_id, preparing_reason, preparing_since, dispatch_attempts
FROM orders WHERE status = 'PREPARING' ORDER BY preparing_since;

-- Takliflar tarixi: <orderId> o'rniga yuqoridagi id
SELECT id, courier_id, status, created_at, expires_at, responded_at, reject_reason
FROM courier_offers WHERE order_id = <orderId> ORDER BY id;

-- Shu fermaning kuryerlari va mashinadagi zaxira: <farmId>
SELECT u.id, u.full_name, u.status, vs.product_id, vs.quantity
FROM users u LEFT JOIN vehicle_stock vs ON vs.courier_id = u.id
WHERE u.farm_id = <farmId> AND u.role = 'COURIER';
```

Online va oxirgi GPS Redis'da: `GET courier:online:<id>`, `GET courier:loc:<id>` (`lat,lon,bearing,speedKmh,epochMillis`).

**4-savol.** `POST /api/courier/location` faol buyurtma yo'q paytda ham qabul qilinadi va `lastSeenAt` ni yangilaydi. Faqat `latitude`, `longitude` majburiy. Rate limit yo'q, 30 soniyada 1 ta so'rov muammo emas.

Eslatma: avval `lastSeenAt` har doim `null` bo'lgan, panelda kuryer `OFFLINE` va koordinatasiz ko'rinardi. Kod vaqtni noto'g'ri ustundan o'qirdi. Tuzatildi.

## 22-band — kuryer profili

**1. Soxta qiymatlar.** Manbasi baza emas, kodda qattiq yozilgan standart qiymatlar edi. Olib tashlandi: profil, dashboard, live tracking, manager ro'yxati va mijozning `CourierLiveDto` si. Baza qiymati yo'q bo'lsa → `null`.

Kuryer #89 bazasini bu yerdan ko'ra olmayman. Ilova eski profilni `PUT` bilan qayta yuborgan bo'lsa, soxta qiymat bazaga yozilgan bo'lishi mumkin. Tekshirish:

```sql
SELECT id, vehicle_model, vehicle_plate_number, max_capacity FROM users
WHERE role = 'COURIER' AND (vehicle_plate_number = '01 A 777 BA' OR vehicle_model = 'Chevrolet Damas');
```

Bu qatorlar avtomatik o'chirilmadi: "Chevrolet Damas" haqiqiy mashina ham bo'lishi mumkin.

**2. Yangi maydonlar.** `ratingCount`, `shiftType`, `shiftTypeLabel`. Qiymatlar: `DAY` (Kunduzgi), `NIGHT` (Tungi), `FULL_TIME` (To'liq). `shift_type` bo'sh bo'lsa `DAY`, Boss paneli bilan bir xil.
Server bu maydonni tekshirmaydi, boshqa matn ham yozilishi mumkin: unda label xom qiymatning o'zi bo'ladi. Prodda tekshirish: `SELECT DISTINCT shift_type FROM users;`

**3. Telefon.** `phone` so'rovdan olib tashlandi: kelsa e'tiborsiz qoladi, `200` qaytadi, telefon o'zgarmaydi.

**4. Takliflar.**

- `GET /api/courier/orders/offers`: `offerId` va `status` har doim bor.
- WS `NEW_OFFER` va `OFFER_CANCELLED`: `offerId` va `status` konvertning yuqori darajasida qo'shildi (`data` ichida ham qoldi).
- FCM `NEW_OFFER`: kodda data-only, `priority: high`, `collapseKey: offer_{offerId}`. **Prodda yuborilayotgani tasdiqlanmagan:** serverda `/etc/gidrogo/firebase-sa.json` bo'lishi kerak, aks holda push o'chiq. Tekshirish: logda `[FCM] Firebase initialized`.

**5. Vaqt formati.** Barcha API vaqtlari `Instant`, format `2026-10-29T15:05:00Z`. Zonasiz vaqt yo'q.

**6. Savollar.**

| № | Javob |
|---|---|
| 6.1 | `targetOrdersCount` (15) va `targetRevenue` (1 000 000) kodda qattiq yozilgan edi, hech kim belgilamaydi. Endi `null`. Reja kerak bo'lsa, alohida funksiya |
| 6.2 | `status` — akkaunt holati (`ACTIVE`, `BLOCKED`, `DELETED`). Smena emas |
| 6.3 | `PATCH /api/courier/status` javobi: `{status: "ONLINE" yoki "OFFLINE", message}`. `onlineEffective` yuborilmaydi: u serverning ichki hisobi, spec'dan olib tashlandi. So'rovda `online` (boolean) yoki `status` yetarli |
| 6.4 | Manager/Boss uchun endpoint hozir yo'q. Bu sizning qaroringiz |
| 6.5 | Bo'sh satr maydonni tozalaydi (`null`). Maydon yuborilmasa, o'zgarmaydi. `vehicleModel`, `vehiclePlateNumber`, `driverLicenseNumber`, `passportSerial` uchun amal qiladi |

## Sizdan kerak qaror

1. **GPS sharti.** TZ'dagi 120 soniyalik shartni yoqamizmi? Hozir yoqilsa, ilovaning 30 soniyalik yangilanishi chiqmaguncha GPS'siz kuryerlar taklif olmaydi. Tavsiya: ilova chiqqandan keyin yoqish.
2. **Eski mobil mijoz ilovasi** hali ishlatiladimi? Ishlatilsa, u `/ws/client` ga ulana olmaydi.
