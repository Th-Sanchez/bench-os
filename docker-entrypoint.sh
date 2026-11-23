#!/bin/sh
set -eu

# Configure PostgreSQL mTLS client certificates from Base64-encoded env vars when provided
# (Required by Vertra Cloud managed PostgreSQL; JDBC requires PKCS#8 DER format for sslkey)
if [ -n "${DB_SSL_CA_B64:-}" ] && [ -n "${DB_SSL_CERT_B64:-}" ] && [ -n "${DB_SSL_KEY_B64:-}" ]; then
  CERT_DIR="/tmp/db-certs"
  mkdir -p "$CERT_DIR"
  chmod 700 "$CERT_DIR"

  printf '%s' "$DB_SSL_CA_B64" | tr -d ' \n\r\t' | base64 -d > "$CERT_DIR/ca.pem"
  printf '%s' "$DB_SSL_CERT_B64" | tr -d ' \n\r\t' | base64 -d > "$CERT_DIR/client.crt"
  printf '%s' "$DB_SSL_KEY_B64" | tr -d ' \n\r\t' | base64 -d > "$CERT_DIR/client.key"

  openssl pkcs8 -topk8 -inform PEM -outform DER \
    -in "$CERT_DIR/client.key" \
    -out "$CERT_DIR/client.pk8" \
    -nocrypt

  chmod 600 "$CERT_DIR/ca.pem" "$CERT_DIR/client.crt" "$CERT_DIR/client.key" "$CERT_DIR/client.pk8"

  case "${DATASOURCE_URL:-}" in
    *"sslmode="*) ;;
    *"?"*)
      export DATASOURCE_URL="${DATASOURCE_URL}&sslmode=verify-full&sslrootcert=${CERT_DIR}/ca.pem&sslcert=${CERT_DIR}/client.crt&sslkey=${CERT_DIR}/client.pk8"
      ;;
    "") ;;
    *)
      export DATASOURCE_URL="${DATASOURCE_URL}?sslmode=verify-full&sslrootcert=${CERT_DIR}/ca.pem&sslcert=${CERT_DIR}/client.crt&sslkey=${CERT_DIR}/client.pk8"
      ;;
  esac
fi

exec java $JAVA_OPTS -jar /opt/benchos/app.jar
