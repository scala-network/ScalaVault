#!/usr/bin/env bash

set -e

source script/env.sh

cd $EXTERNAL_LIBS_BUILD_ROOT

url="https://github.com/m2049r/scala"
version="release-v0.16.0.0-monerujo"

if [ ! -d "scala" ]; then
  git clone ${url} -b ${version}
  cd scala
  git submodule update --recursive --init
else
  cd scala
  git checkout ${version}
  git pull
  git submodule update --recursive --init
fi
