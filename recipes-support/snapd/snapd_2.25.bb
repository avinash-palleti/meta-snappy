SUMMARY = "The snapd and snap tools enable systems to work with .snap files."
HOMEPAGE = "https://www.snapcraft.io"
LICENSE = "GPL-3.0"
LIC_FILES_CHKSUM = "file://${WORKDIR}/${PN}-${PV}/COPYING;md5=d32239bcb673463ab874e80d47fae504"

SRC_URI = "https://github.com/snapcore/snapd/releases/download/${PV}/snapd_${PV}.vendor.tar.xz"

SRC_URI[md5sum] = "453ffdc2ecdbb7058ac193f81ac37135"
SRC_URI[sha256sum] = "accd4c94049ce79443ff995c27111f3851e9896bbad502dd5d341f8847645b90"

SNAPD_PKG = "github.com/snapcore/snapd"

DEPENDS += " \
	go-cross \
	glib-2.0 \
	udev \
	xfsprogs \
"

RDEPENDS_${PN} += " \
	ca-certificates \
	kernel-module-squashfs \
"

S = "${WORKDIR}/${PN}-${PV}"

EXTRA_OECONF += " \
	--disable-apparmor \
	--disable-seccomp \
	--libexecdir=${libdir}/snapd \
"

inherit systemd autotools pkgconfig

# Our tools build with autotools are inside the cmd subdirectory
# and we need to tell the autotools class to look in there.
AUTOTOOLS_SCRIPT_PATH = "${S}/cmd"

SYSTEMD_SERVICE_${PN} = "snapd.service"

do_configure_prepend() {
	(cd ${S} ; ./mkversion.sh ${PV})
}

do_compile_prepend() {
	export GOARCH="${TARGET_ARCH}"
	# supported amd64, 386, arm arm64
	if [ "${TARGET_ARCH}" = "x86_64" ]; then
		export GOARCH="amd64"
	fi
	if [ "${TARGET_ARCH}" = "aarch64" ]; then
		export GOARCH="arm64"
	fi
	if [ "${TARGET_ARCH}" = "i586" ]; then
		export GOARCH="386"
	fi

	# Set GOPATH. See 'PACKAGERS.md'. Don't rely on
	# docker to download its dependencies but rather
	# use dependencies packaged independently.
	cd ${S}
	rm -rf .gopath
	mkdir -p .gopath/src/"$(dirname "${SNAPD_PKG}")"
	ln -sf ../../../.. .gopath/src/"${SNAPD_PKG}"
	export GOPATH="${S}/.gopath:${S}/vendor:${STAGING_DIR_TARGET}/${prefix}/local/go"
	export GOROOT="${STAGING_DIR_NATIVE}/${nonarch_libdir}/${HOST_SYS}/go"
	cd -

	# Pass the needed cflags/ldflags so that cgo
	# can find the needed headers files and libraries
	export CGO_ENABLED="1"
	export CGO_CFLAGS="${BUILDSDK_CFLAGS} --sysroot=${STAGING_DIR_TARGET}"
	export CGO_LDFLAGS="${BUILDSDK_LDFLAGS} --sysroot=${STAGING_DIR_TARGET}"

	rm -rf ${B}/build
	mkdir ${B}/build
	go build -a -v -o ${B}/build/snapd ${SNAPD_PKG}/cmd/snapd
	go build -a -v -o ${B}/build/snap ${SNAPD_PKG}/cmd/snap
	go build -a -v -o ${B}/build/snapctl ${SNAPD_PKG}/cmd/snapctl
	go build -a -v -o ${B}/build/snap-exec ${SNAPD_PKG}/cmd/snap-exec
}

do_install_append() {
	install -d ${D}${libdir}/snapd
	install -d ${D}${bindir}
	install -d ${D}${systemd_unitdir}/system
	install -d ${D}/var/lib/snapd
	install -d ${D}/var/lib/snapd/snaps
	install -d ${D}/var/lib/snapd/lib/gl
	install -d ${D}/var/lib/snapd/desktop
	install -d ${D}/var/lib/snapd/environment
	install -d ${D}/var/snap
	install -d ${D}${sysconfdir}/profile.d

	# NOTE: This file needs to be present to allow snapd's service
	# units to startup.
	touch ${D}/${sysconfdir}/environment

	oe_runmake -C ${S}/data/systemd install DESTDIR=${D}

	install -m 0755 ${B}/build/snapd ${D}${libdir}/snapd/
	install -m 0755 ${B}/build/snap-exec ${D}${libdir}/snapd/
	install -m 0755 ${B}/build/snap ${D}${bindir}
	install -m 0755 ${B}/build/snapctl ${D}${bindir}

	echo "PATH=\$PATH:/snap/bin" > ${D}${sysconfdir}/profile.d/20-snap.sh
}

RDEPENDS_${PN} += "squashfs-tools"
FILES_${PN} += " \
	${systemd_unitdir}/system/ \
	/var/lib/snapd \
	/var/snap \
	${baselib}/udev/snappy-app-dev \
"

# ERROR: snapd-2.23.5-r0 do_package_qa: QA Issue: No GNU_HASH in the elf binary:
# '.../snapd/usr/lib/snapd/snap-exec' [ldflags]
INSANE_SKIP_${PN} = "ldflags"
