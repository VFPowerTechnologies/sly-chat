#!/bin/bash
set -eu

if [ $# -lt 1 ]; then
    echo "No domain name given"
    exit 1
fi

cd $(dirname "$0")/ca

SITE="$1"

openssl ca -config intermediate/openssl.cnf \
    -revoke intermediate/certs/$SITE.cert.pem \
    -batch

openssl ca -config intermediate/openssl.cnf \
      -gencrl -out intermediate/crl/intermediate.crl.pem \
      -batch
