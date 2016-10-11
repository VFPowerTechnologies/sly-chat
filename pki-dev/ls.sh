#!/bin/bash
set -eu

cd $(dirname "$0")/ca

INTERMEDIATE=${1:-intermediate}

ls -c1 $INTERMEDIATE/certs/*.chained.cert.pem
