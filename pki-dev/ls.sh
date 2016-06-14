#!/bin/bash
set -eu

cd $(dirname "$0")/ca

ls -c1 intermediate/certs/*.chained.cert.pem
