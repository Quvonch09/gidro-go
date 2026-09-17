# GidroGo Backend v2.0 💧

Ichimlik suvi yetkazib berish korxonalari uchun zamonaviy **Multi-Farm Marketplace & CRM Platformasi**.

## 🛠 Texnologik Stack
* **Java 21** & **Spring Boot 3.3.4**
* **PostgreSQL 16** (Flyway migratsiyalari bilan)
* **Redis 7** (Kuryerlar live GPS tracking va OTP tokenlar keshlash)
* **JWT (JSON Web Token)** autentifikatsiya
* **GitHub Actions** (Avtomatlashtirilgan CI/CD)

---

## 🚀 Loyihani Ishga Tushirish

### 1. Mahalliy muhitda:
```bash
# Repositoryni klonlash
git clone https://github.com/Quvonch09/gidro-go.git
cd gidro-go

# Paketlash va ishga tushirish
./mvnw clean spring-boot:run
```

### 2. Swagger API Hujjatlari:
* **Lokal**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
* **Server**: [http://169.58.215.170:8080/swagger-ui/index.html](http://169.58.215.170:8080/swagger-ui/index.html)
* **OpenAPI Docs**: [http://169.58.215.170:8080/v3/api-docs](http://169.58.215.170:8080/v3/api-docs)

---

## ⚙️ GitHub Actions CI/CD Sozlamalari

Loyihaning `.github/workflows/deploy.yml` fayli orqali har safar `main` yoki `master` filialiga `git push` amalga oshirilganda:
1. Loyiha Ubuntu muhitida avtomatik JDK 21 bilan paketlanadi (`mvn clean package`).
2. Tayyorlangan `gidrogo-backend-2.0.0.jar` ishlab chiqarish serveriga SSH/SCP orqali yuklanadi.
3. Serverda `gidrogo.service` qayta ishga tushirilib, uning faolligi tekshiriladi.

### GitHub Repository Secrets:
GitHub omboringizda `Settings` -> `Secrets and variables` -> `Actions` bo'limiga quyidagi o'zgaruvchilarni qo'shishingiz mumkin:
* `SERVER_HOST`: `169.58.215.170`
* `SERVER_USER`: `root`
* `SERVER_PASSWORD`: `<server_paroli>`
* `SERVER_PORT`: `22`
*(Standart sozlamalar workflow faylining o'zida ham ko'rsatilgan).*

---

## 👥 Foydalanuvchi Rollari & Yagona Kirish API
Barcha rollar uchun yagona login:
`POST /api/auth/login`
* **SUPER_ADMIN**: Tizim monitoringi, viloyatlar statistikasi va fermalar boshqaruvi.
* **BOSS**: Suv korxonasi rahbari, to'liq moliya va xodimlar nazorati.
* **MANAGER**: Kunlik buyurtmalar, dastavkachilar va ombor harakati boshqaruvi.
* **COURIER**: Buyurtmalarni yetkazish, mashinadagi suv balansi va jonli GPS joylashuv.
* **CLIENT**: Suv buyurtma qilish, tara balansi va reyting qoldirish.
