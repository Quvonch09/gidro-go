# 24-band bo'yicha javob — kassa topshirish tasdig'i

**Holat:** kodda tayyor, qabul mezonlari 1–6 integratsiya testi bilan tekshirildi (`CashHandoverNotificationsIT`). Server holati deploydan keyin yoziladi.

## 1. Manager xabar topadi

- **Bildirishnomalar** (sxema kuryerniki bilan bir xil: `id, title, message, type, referenceId, isRead, createdAt`):
  - `GET /api/manager/notifications?page=&size=&unreadOnly=`
  - `GET /api/manager/notifications/unread-count`
  - `PATCH /api/manager/notifications/{id}/read`
  - `POST /api/manager/notifications/read-all`
  - Boss uchun xuddi shu to'plam `/api/boss/notifications/**` da ham bor. `/api/manager/**` ham Boss'ga ochiq.
  - Har bir xodim faqat o'z bildirishnomasini ko'radi: begonasini o'qishga urinsa, `404`.
- Kuryer `POST /api/courier/cash/handovers` yuborganda fermaning **barcha faol Manager'lari va Boss'i** bildirishnoma oladi:
  `type = CASH_HANDOVER_REQUESTED`, `referenceId = handoverId`, matn: "Ali Valiyev 19 999 so'm topshirmoqchi".
- `GET /api/manager/dashboard` → `pendingHandoversCount`.
- **WS:** alohida `/ws/manager` kanali yo'q. Panel mavjud STOMP `/ws/client` (JWT bilan) orqali olishi mumkin:
  - `/user/queue/notifications` — `{eventType: "NOTIFICATION", data: NotificationItemResponse}`;
  - `/topic/farm/{farmId}/cash` — `{eventType, farmId, data: CashHandoverResponse}`, `eventType`: `CASH_HANDOVER_REQUESTED`, `CASH_HANDOVER_CONFIRMED`, `CASH_HANDOVER_REJECTED`, `CASH_HANDOVER_CANCELLED`.

  WS ishlatilmasa, `unread-count` polling yetarli (1 va 3-bandlar).

## 2. Kuryer natijani ko'radi

- `GET /api/courier/cash/handovers?status=&page=&size=` → `PageResponse<CashHandoverResponse>`.
  Maydonlar: `declaredAmount, confirmedAmount, status, note, rejectReason, managerName, resolvedByRole, createdAt, resolvedAt`.
- `GET /api/courier/cash` → `pendingHandover.status` qo'shildi.
- Kuryer bildirishnomalari **avval yuborilmagan** (faqat WS bor edi). Endi `/api/courier/notifications` orqali boradi:
  - `CASH_HANDOVER_CONFIRMED` — "20 000 so'm qabul qilindi (siz 19 999 so'm yozgansiz). Izoh: …"; izoh faqat summa farq qilganda qo'shiladi;
  - `CASH_HANDOVER_REJECTED` — "19 999 so'm rad etildi. Sabab: …";
  - ikkalasida `referenceId = handoverId`.
- `/ws/courier` dagi kassa hodisalari (`handoverId` va `status` endi konvertning yuqori darajasida ham bor):

```json
{"type": "CASH_HANDOVER_CONFIRMED", "handoverId": 12, "status": "CONFIRMED",
 "data": {"handoverId": 12, "confirmedAmount": 20000, "newBalance": 30000}, "timestamp": "2026-11-04T18:00:00Z"}
{"type": "CASH_HANDOVER_REJECTED", "handoverId": 12, "status": "REJECTED",
 "data": {"handoverId": 12, "reason": "Summa noto'g'ri"}, "timestamp": "2026-11-04T18:00:00Z"}
```

  `/ws/courier` dagi boshqa turlar: `CONNECTED`, `PONG`, `NEW_OFFER`, `OFFER_CANCELLED`, `ORDER_ASSIGNED`, `ORDER_SEQUENCE_CHANGED`.

## 3. Kuryer bekor qiladi

- Endpoint: `DELETE /api/courier/cash/handovers/{id}`.
  - Faqat o'z so'rovini va faqat `PENDING` holatda bekor qila oladi. Natija `status = CANCELLED_BY_COURIER`, yozuv o'chirilmaydi.
  - Holat boshqa bo'lsa `409 HANDOVER_NOT_PENDING`, so'rov boshqa kuryerniki bo'lsa `403`.
  - Bekor qilingach yangi so'rov `409` bermaydi.
  - Manager va Boss `CASH_HANDOVER_CANCELLED` bildirishnomasini oladi.
- Tasdiqlash, rad etish va bekor qilish bir vaqtda kelsa, so'rov qulflanadi (`SELECT … FOR UPDATE`) va faqat bittasi o'tadi.

## 4. `type` qiymatlari (OpenAPI'da `enum` sifatida ham hujjatlangan)

| Kim | Qiymatlar |
|---|---|
| Kuryer | `NEW_OFFER`, `OFFER_CANCELLED`, `ORDER_ASSIGNED`, `CASH_HANDOVER_CONFIRMED`, `CASH_HANDOVER_REJECTED`, `SYSTEM` |
| Manager / Boss | `CASH_HANDOVER_REQUESTED`, `CASH_HANDOVER_CANCELLED`, `CASH_HANDOVER_SHORTAGE` (faqat Boss), `FEEDBACK_CREATED`, `SYSTEM` |

## 5. Savollar

| № | Javob |
|---|---|
| 5.1 | Manager bo'lmasa ham so'rov yopiladi: Boss bildirishnoma oladi va `POST /api/boss/cash/handovers/{id}/confirm`, `/reject` bilan qaror qiladi. So'rov cheksiz PENDING qolmaydi. Javobda kim qaror qilgani `resolvedByRole` (`BOSS`/`MANAGER`) da qaytadi |
| 5.2 | Tuzatish endpointi **yo'q**. `cash_ledger` o'zgarmas, shuning uchun to'g'ri yo'l — teskari yozuv. Bu sizning qaroringiz: kim qila oladi (faqat Boss?), izoh majburiymi? |
| 5.3 | Hozir chegara **yo'q**, balans dispatch'ga ta'sir qilmaydi. Kerak bo'lsa, chegara summasini va u ferma sozlamasimi yoki umumiy qiymatmi — shuni ayting |
| 5.4 | Ha, endi bor: tasdiqlangan summa e'lon qilingandan kam bo'lsa, Boss'ga `CASH_HANDOVER_SHORTAGE` boradi (izoh va ikkala summa bilan) |
| 5.5 | Yuqoridagi 2-bo'limda |
