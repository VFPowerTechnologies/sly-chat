#!/bin/bash
set -eu

if [ $# -lt 1 ]; then
    echo "No domain name given"
    exit 1
fi

cd $(dirname "$0")/ca

INTERMEDIATE="$1"

mkdir "$INTERMEDIATE"
echo 1000 > "$INTERMEDIATE/serial"
echo 1000 > "$INTERMEDIATE/crlnumber"
touch "$INTERMEDIATE/index.txt"

cd "$INTERMEDIATE"
mkdir private newcerts csr crl certs
cd ..

sed -e "s/INTERMEDIATE_DIR/$INTERMEDIATE/" ../intermediate-openssl-template.cnf > "$INTERMEDIATE/openssl.cnf"

#generated a private key
openssl genrsa -out "$INTERMEDIATE/private/intermediate.key.pem" 4096

#generate a CSR for the intermediate CA
openssl req -config "$INTERMEDIATE/openssl.cnf" -new -sha256 \
    -key "$INTERMEDIATE/private/intermediate.key.pem" \
    -out "$INTERMEDIATE/csr/intermediate.csr.pem"

#sign the CSR with the root CA
openssl ca -config openssl.cnf -extensions v3_intermediate_ca \
    -days 3650 -notext -md sha256 \
    -in "$INTERMEDIATE/csr/intermediate.csr.pem" \
    -out "$INTERMEDIATE/certs/intermediate.cert.pem" \
    -batch

#generate an empty CRL
openssl ca -config "$INTERMEDIATE/openssl.cnf" \
      -gencrl -out "$INTERMEDIATE/crl/intermediate.crl.pem" \
      -batch
