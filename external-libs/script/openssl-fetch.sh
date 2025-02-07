#!/usr/bin/env bash

set -e

source script/env.sh

cd $EXTERNAL_LIBS_BUILD_ROOT

if [ ! -f "OpenSSL_1.1.1w.tar.gz" ]; then
  wget https://github.com/openssl/openssl/archive/OpenSSL_1.1.1w.tar.gz
fi

if [ ! -d "android-openssl" ]; then
  mkdir android-openssl && cd android-openssl
  tar xfz ../OpenSSL_1.1.1w.tar.gz
fi
