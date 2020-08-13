#.PHONY: f-droid-sign f-droid-clean \
#build-external-libs use-prebuilt-external-libs \
#toolchain openssl boost wownero collect \
#clean-external-libs \
#f-droid-sign f-droid-clean \
#gradle-release gradle-build gradle-sign gradle-clean \
#apk-install remove-exif

#all: build-external-libs

#build-external-libs: clean-external-libs collect

all: scala

scala: toolchain libsodium openssl boost scala_dl openssl_sysroot
	script/scala-build.sh

scala_dl:
	script/scala-fetch.sh

toolchain:
	script/toolchain-build.sh

openssl: toolchain openssl_dl
	script/openssl-build.sh

openssl_sysroot:
	script/openssl-install.sh

openssl_dl:
	script/openssl-fetch.sh
	script/openssl-patch.sh

boost: toolchain boost_dl
	script/boost-build.sh

boost_dl:
	script/boost-fetch.sh

libsodium: toolchain libsodium_dl
	script/libsodium-build.sh

libsodium_dl:
	script/libsodium-fetch.sh

install: all
	script/install.sh

clean:
	script/clean.sh

distclean: clean
	find scala    -type f -a ! -name ".gitignore" -a ! -name "wallet2_api.h" -exec rm {} \;
	find boost     -type f -a ! -name ".gitignore" -exec rm {} \;
	find libsodium -type f -a ! -name ".gitignore" -exec rm {} \;
	find openssl   -type f -a ! -name ".gitignore" -exec rm {} \;

archive: libsodium openssl boost scala
	echo "Packing external-libs"
	tar czfv libsodium openssl boost scala external-libs.tgz
