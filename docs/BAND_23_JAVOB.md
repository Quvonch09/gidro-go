# 23-band bo'yicha javob

**Holat:** kodda tayyor, lokal testlardan o'tgan (53 ta). Prodga chiqishi va baza tozalanishi alohida yoziladi.

## 1. Bot klaviaturasi va menyu tugmasi

- Kontakt tasdiqlangach:
  - birinchi xabar `reply_markup: {"remove_keyboard": true}` bilan keladi — "📱 Telefon raqamni yuborish" klaviaturasi yo'qoladi;
  - ikkinchi xabarda inline "💧 GidroGo'ni ochish" (`web_app`) tugmasi bor;
  - shu chat uchun `setChatMenuButton` chaqiriladi, menyu tugmasi matni "GidroGo".
- Faol OTP kodi bo'lsa, kod tasdiq xabarining o'ziga qo'shiladi.
- Raqami tasdiqlangan mijoz `/start` yozsa, kontakt so'ralmaydi: salom va inline tugma keladi.
- Matnlar `language_code` bo'yicha: `ru…` bo'lsa ruscha, qolgan hollarda o'zbekcha.
- Server ishga tushganda hamma chatlar uchun standart menyu tugmasi o'rnatiladi (`setChatMenuButton`, `chat_id` siz, idempotent).

## 2. Ferma xizmat hududi

Qoida (yagona joy: `FarmServiceArea`):

1. Fermaning **faol** `ServiceRegion` poligonlaridan biri nuqtani o'z ichiga olsa, ferma xizmat qiladi.
2. Faol poligoni yo'q bo'lsa, ferma koordinatasidan **30 km** ichida bo'lishi kerak. Radius `FARM_SERVICE_RADIUS_KM` bilan o'zgartiriladi.
3. Koordinatasi ham, poligoni ham yo'q ferma hech qayerda chiqmaydi.

Qo'llanadigan joylar:

- `GET /api/client/farms` (`lat`, `lon` bilan): faqat xizmat qiladigan fermalar, haqiqiy `distanceKm`, masofa bo'yicha saralangan. Hech biri xizmat qilmasa, `200` va `[]`.
- `GET /api/client/farms/nearby`: xuddi shu filtr. Koordinatasiz ferma chiqmaydi (soxta `distanceMeters: 0` yo'q). `farm.city` va `farm.district` endi to'ldiriladi.
- `409 FARM_NOT_SERVING_ADDRESS` qaytaradigan joylar:
  - `POST /api/client/telegram/register` (eslatma: haqiqiy yo'l shu, `/api/telegram/register` emas);
  - `POST /api/client/addresses`, `PUT /api/client/addresses/{id}` — mijozning fermasi tekshiriladi;
  - `POST /api/client/cart/checkout`;
  - `PUT /api/client/farm` — yangi ferma mijozning asosiy manziliga xizmat qilishi kerak.
- Ro'yxatdan o'tishda koordinata endi **majburiy**: `latitude` yoki `longitude` yo'q bo'lsa, `400 LOCATION_REQUIRED`. Avval ular bo'lmasa, manzil `0, 0` bilan saqlanardi.

## 3. Savollar

| № | Javob |
|---|---|
| 3.1 | Prodda (2026-11-03) `service_regions` bo'sh: 0 ta poligon. Hozir hamma fermada 30 km radius asosiy qoida |
| 3.2 | Qabul qilinadi: GeoJSON `Polygon`, `MultiPolygon` yoki `Feature` — koordinata tartibi `[lon, lat]`; yoki eski oddiy massiv `[[lat, lon], ...]`. Kamida 3 nuqta. Noto'g'ri bo'lsa, `POST /api/manager/regions` → `400 INVALID_POLYGON`. Xaritada ko'rsatish uchun GeoJSON tavsiya etiladi |
| 3.3 | Standart qiymat `https://gidrogo-miniapp.pages.dev` (CORS ro'yxatidagi Pages domeni). Boshqa bo'lsa, serverda `MINIAPP_URL` muhit o'zgaruvchisi bilan beriladi |

## Tekshirilmagan

- Bot xabarlari va menyu tugmasi faqat unit test bilan tekshirildi (Telegram API soxtalashtirilgan). Haqiqiy Telegram'da egasi qabul mezoni 1–2 ni o'zi sinaydi.
