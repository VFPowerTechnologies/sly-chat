#!/bin/bash
set -eu

if [ $# -lt 1 ]; then
    echo "No domain name given"
    exit 1
fi

umask 0077

cd $(dirname "$0")/ca

SITE="$1"
INTERMEDIATE="${2:-intermediate}"

openssl ca -config "$INTERMEDIATE/openssl.cnf" \
    -revoke "$INTERMEDIATE/certs/$SITE.cert.pem" \
    -batch

openssl ca -config "$INTERMEDIATE/openssl.cnf" \
      -gencrl -out "$INTERMEDIATE/crl/intermediate.crl.pem" \
      -batch
