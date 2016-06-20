#!/bin/bash
set -eu

if [ $# -lt 1 ]; then
    echo "No domain name given"
    exit 1
fi

umask 0077

cd $(dirname "$0")/ca

INTERMEDIATE="$1"

openssl ca -config openssl.cnf \
    -revoke "$INTERMEDIATE/certs/intermediate.cert.pem" \
    -batch

openssl ca -config openssl.cnf \
      -gencrl -out crl/ca.crl.pem \
      -batch
