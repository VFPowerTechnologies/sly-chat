#!/bin/bash
set -eu

if [ $# -lt 1 ]; then
    echo "No domain name given"
    exit 1
fi

cd $(dirname "$0")/ca

SITE="$1"
INTERMEDIATE="${2:-intermediate}"

#generate key
openssl genrsa -out "$INTERMEDIATE/private/$SITE.key.pem" 2048

#generate CSR
openssl req -config "$INTERMEDIATE/openssl.cnf" \
    -key "$INTERMEDIATE/private/$SITE.key.pem" \
    -new -sha256 -out "$INTERMEDIATE/csr/$SITE.csr.pem"

#sign CSR and generate cert
openssl ca -config "$INTERMEDIATE/openssl.cnf" \
    -extensions server_cert -days 1780 -notext -md sha256 \
    -in "$INTERMEDIATE/csr/$SITE.csr.pem" \
    -out "$INTERMEDIATE/certs/$SITE.cert.pem" \
    -batch

#bundle CA with intermediate CA cert
cat "$INTERMEDIATE/certs/$SITE.cert.pem" "$INTERMEDIATE/certs/intermediate.cert.pem" > "$INTERMEDIATE/certs/$SITE.chained.cert.pem"
