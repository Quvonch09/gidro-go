#!/usr/bin/env bash
# GidroGo: domensiz HTTPS/WSS — Let's Encrypt IP sertifikati (bepul).
# Ubuntu/Debian serverda root sifatida ishga tushiriladi:
#   sudo SERVER_IP=169.58.215.170 EMAIL=siz@example.com ./setup_tls.sh            # avval staging
#   sudo SERVER_IP=169.58.215.170 EMAIL=siz@example.com STAGING=0 ./setup_tls.sh  # keyin haqiqiy
#
# Talablar: 80 va 443 portlar internetdan ochiq; backend 127.0.0.1:8080 da ishlaydi.
# Sertifikat 6 kunlik ("shortlived" profili — IP sertifikati uchun Let's Encrypt talabi),
# certbot taymeri uni avtomatik yangilaydi va deploy-hook nginx'ni qayta yuklaydi.
set -euo pipefail

: "${SERVER_IP:?SERVER_IP kerak (masalan 169.58.215.170)}"
: "${EMAIL:?EMAIL kerak (sertifikat muddati haqida ogohlantirish uchun)}"
STAGING="${STAGING:-1}"
HERE="$(cd "$(dirname "$0")" && pwd)"
CONF_SRC="$HERE/../nginx/gidrogo.conf"
LEGAL_SRC="$HERE/../../../docs/legal"
SITE=/etc/nginx/sites-available/gidrogo
ACME_ROOT=/var/www/acme

echo "== 1. nginx va certbot (snap, ≥5.4 — IP sertifikati uchun)"
apt-get update -qq
apt-get install -y -qq nginx snapd
snap install core >/dev/null 2>&1 || true
snap install --classic certbot >/dev/null 2>&1 || snap refresh certbot >/dev/null 2>&1 || true
ln -sf /snap/bin/certbot /usr/bin/certbot
certbot --version

echo "== 2. Vaqtinchalik 80-port konfiguratsiyasi (faqat ACME)"
mkdir -p "$ACME_ROOT" /var/www/gidrogo-legal
cp "$LEGAL_SRC"/*.html /var/www/gidrogo-legal/ 2>/dev/null || true
cat > "$SITE" <<EOF
server {
    listen 80 default_server;
    server_name $SERVER_IP;
    location ^~ /.well-known/acme-challenge/ { root $ACME_ROOT; default_type text/plain; }
    location / { return 404; }
}
EOF
ln -sf "$SITE" /etc/nginx/sites-enabled/gidrogo
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx

echo "== 3. Sertifikat olish (staging=$STAGING)"
STAGING_FLAG=""
[ "$STAGING" = "1" ] && STAGING_FLAG="--staging"
certbot certonly $STAGING_FLAG --non-interactive --agree-tos -m "$EMAIL" \
  --preferred-profile shortlived \
  --webroot --webroot-path "$ACME_ROOT" \
  --ip-address "$SERVER_IP" \
  --deploy-hook "systemctl reload nginx"

if [ "$STAGING" = "1" ]; then
  echo "Staging muvaffaqiyatli. Endi STAGING=0 bilan qayta ishga tushiring."
  echo "(Avval staging sertifikatini o'chiring: certbot delete --cert-name $SERVER_IP)"
  exit 0
fi

echo "== 4. To'liq konfiguratsiya (443 + WSS + /legal/)"
sed "s/__SERVER_IP__/$SERVER_IP/g" "$CONF_SRC" > "$SITE"
nginx -t && systemctl reload nginx

echo "== 5. Avtomatik yangilashni tekshirish"
systemctl list-timers | grep -i certbot || echo "OGOHLANTIRISH: certbot taymeri topilmadi"
certbot renew --dry-run

echo "== Tayyor: https://$SERVER_IP/api/... va wss://$SERVER_IP/ws/..."
echo "Tekshiruv: curl -sI https://$SERVER_IP/legal/ | head -1"
