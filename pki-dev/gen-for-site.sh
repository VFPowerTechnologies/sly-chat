#!/bin/bash
set -eu

if [ $# -lt 1 ]; then
    echo "No domain name given"
    exit 1
fi

cd $(dirname "$0")/ca

SITE="$1"

#generate key
openssl genrsa -out intermediate/private/$SITE.key.pem 2048

#generate CSR
openssl req -config intermediate/openssl.cnf \
    -key intermediate/private/$SITE.key.pem \
    -new -sha256 -out intermediate/csr/$SITE.csr.pem \

#sign CSR and generate cert
openssl ca -config intermediate/openssl.cnf \
    -extensions server_cert -days 7300 -notext -md sha256 \
    -in intermediate/csr/$SITE.csr.pem \
    -out intermediate/certs/$SITE.cert.pem \
    -batch

#bundle CA with intermediate CA cert
cat intermediate/certs/$SITE.cert.pem intermediate/certs/intermediate.cert.pem > intermediate/certs/$SITE.chained.cert.pem
