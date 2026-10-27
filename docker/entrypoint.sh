#!/bin/sh
set -eu
umask 077
if [ ! -s /run/secrets/team_config ]; then
  echo 'Missing config/team.properties. Ask the team owner for this file.' >&2
  exit 1
fi
if [ ! -s /app/runtime/private.pem ]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out /app/runtime/private.pem.tmp 2>/dev/null
  mv /app/runtime/private.pem.tmp /app/runtime/private.pem
fi
openssl pkey -in /app/runtime/private.pem -pubout -out /app/runtime/public.pem 2>/dev/null
if [ ! -s /app/runtime/otp.secret ]; then
  openssl rand -hex 32 > /app/runtime/otp.secret.tmp
  mv /app/runtime/otp.secret.tmp /app/runtime/otp.secret
fi
# Container-local database and keys override machine-specific paths in a shared file.
cat /run/secrets/team_config > /tmp/bookstore.properties
cat >> /tmp/bookstore.properties <<EOF

db.url=jdbc:postgresql://db:5432/goc_sach_final_demo
db.user=bookstore
db.password=goc-sach-local-demo-only
cookie.secure=false
jwt.private-key-file=/app/runtime/private.pem
jwt.public-key-file=/app/runtime/public.pem
jwt.issuer=https://goc-sach.team.local
jwt.audience=goc-sach-api
jwt.lifetime-seconds=600
EOF
printf 'otp.secret=%s\n' "$(cat /app/runtime/otp.secret)" >> /tmp/bookstore.properties
exec java -Dfile.encoding=UTF-8 -Dbookstore.config=/tmp/bookstore.properties -jar /app/goc-sach.jar
