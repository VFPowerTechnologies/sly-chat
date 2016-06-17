set -eu

cd $(dirname "$0")

if [ -e ca ]; then
    echo "CA already exists"
    exit 1
fi

umask 0077

mkdir ca && cd ca

echo 1000 > serial
echo 1000 > crlnumber
touch index.txt

mkdir private newcerts crl certs

openssl genrsa -out private/ca.key.pem 4096

chmod 400 private/ca.key.pem

cp ../ca-openssl.cnf openssl.cnf

SUBJECT="/C=CA/ST=Quebec/L=Montreal/O=Keystream Information Systems/OU=Keystream Information Systems Certificate Authority/CN=Keystream Information Systems Root CA/emailAddress=ca@slychat.io/"
openssl req -config openssl.cnf \
     -key private/ca.key.pem \
     -new -x509 -days 7300 -sha256 -extensions v3_ca \
     -subj "$SUBJECT" \
     -out certs/ca.cert.pem
