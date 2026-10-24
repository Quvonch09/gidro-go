# GidroGo server ops (nol byudjet)

Bu skriptlarni **serverda** (hozir `169.58.215.170`) root huquqi bilan egasi yoki
backend dasturchi ishga tushiradi. Hammasi bepul; domen sotib olish shart emas.

> Avval aniqlang: server kimniki, pullimi va qaysi davlatda. O'zbekiston
> fuqarolarining shaxsiy ma'lumotlari O'zbekistondagi serverda saqlanishi kerak
> (`claude/GIDROGO_NOL_BYUDJET_REJA_2026-10-21.md`).

## 1. HTTPS/WSS (GG-06, GG-40) — `tls/setup_tls.sh`

Let's Encrypt 2026-yildan IP manzil uchun ham sertifikat beradi (6 kunlik,
`shortlived` profili, Certbot ≥ 5.4). Skript nginx'ni backend (`127.0.0.1:8080`)
oldiga qo'yadi, `/ws/` uchun WebSocket proksi va `/legal/` sahifalarini beradi.

```bash
sudo SERVER_IP=169.58.215.170 EMAIL=<email> ./tls/setup_tls.sh          # 1) staging
sudo certbot delete --cert-name 169.58.215.170
sudo SERVER_IP=169.58.215.170 EMAIL=<email> STAGING=0 ./tls/setup_tls.sh  # 2) haqiqiy
```

Talab: 80 va 443 portlar internetdan ochiq. Tekshiruv:
`curl -sI https://169.58.215.170/legal/` → `200`.

**Mobil ilovani o'tkazish tartibi** (TLS ishlagandan keyin):
1. APK'ni `--dart-define=API_BASE_URL=https://169.58.215.170 --dart-define=WS_BASE_URL=wss://169.58.215.170`
   bilan yig'ing (CI'da `API_BASE_URL`, `WS_BASE_URL` secret'lari).
2. Telefonlarda sinab, `android/app/src/main/res/xml/network_security_config.xml`
   dagi `169.58.215.170` cleartext istisnosini olib tashlang.
3. Eski APK'lar ishlatilmay qolgach, 8080 portni tashqaridan yoping:
   `sudo ufw deny 8080/tcp` (nginx 127.0.0.1 orqali ishlashda davom etadi).

## 2. Ma'lumotlar bazasi zaxirasi — `backup/pg_backup.sh`

Kunlik `pg_dump`, 14 kun saqlanadi, har zaxira `pg_restore -l` bilan tekshiriladi.
cron qatori skript boshida. Zaxirani haftasiga bir marta serverdan tashqariga
ko'chiring — o'sha diskdagi zaxira disk buzilishidan himoya qilmaydi.

## 3. OSRM (ixtiyoriy) — `osrm/prepare_and_run.sh`

Faqat backend `/route` yoki dispatch ETA pullik tashqi API ishlatsa kerak.
O'zbekiston OSM ma'lumoti bilan `127.0.0.1:5000` da marshrut va ETA matritsasi
(`/route`, `/table`) beradi. ~1 GB disk, 2 GB RAM yetarli bo'lishi kutiladi —
birinchi ishga tushirishda tekshiring.

## Nima bu yerda yo'q

Backend servislarining `docker-compose` fayli — backend kodi bu repoda emas.
Backend uchun talablar: `docs/BACKEND_NOL_BYUDJET_SPEC.md`.
