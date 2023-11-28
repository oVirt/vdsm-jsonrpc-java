#!/bin/bash -xe

# Directory, where build artifacts will be stored, should be passed as the 1st parameter
ARTIFACTS_DIR=${1:-exported-artifacts}
export ARTIFACTS_DIR

# Prepare source archive
[[ -d rpmbuild/SOURCES ]] || mkdir -p rpmbuild/SOURCES

./autogen.sh --system
./configure

# clean
rm -rf rpmbuild/SOURCES/*
make clean

# build tarballs
make dist

SUFFIX=".$(date -u +%Y%m%d%H%M%S).git$(git rev-parse --short HEAD)"

# Build SRPMs
rpmbuild \
    -D "_topdir rpmbuild" \
    ${SUFFIX:+ -D "release_suffix ${SUFFIX}"} \
    -ts ./*.tar.gz
