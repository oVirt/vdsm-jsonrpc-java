.PHONY: installdeps srpm git-safe

installdeps:
	dnf -y install \
		apache-commons-lang3 \
		autoconf \
		automake \
		createrepo_c \
		dnf-utils \
		findutils \
		gcc \
		git \
		jackson-annotations \
		jackson-core \
		jackson-databind \
		java-11-openjdk-devel \
		javapackages-tools \
		make \
		maven-source-plugin \
		maven \
		rpm-build \
		slf4j \
		slf4j-jdk14 \
		sed

# explicity mark the copr generated git repo directory (which is done prior to the mock
# # call to the make_srpm and will be the current pwd) as safe for git commands
git-safe:
	git config --global --add safe.directory "$(shell pwd)"

srpm: installdeps git-safe
	# $(eval SUFFIX=$(shell sh -c " echo '.$$(date -u +%Y%m%d%H%M%S).git$$(git rev-parse --short HEAD)'"))
	# changing the spec file as passing -D won't preserve the suffix when rebuilding in mock
	sed "s:%{?release_suffix}:${SUFFIX}:" -i vdsm-jsonrpc-java.spec.in
	.automation/build-srpm.sh
	cp rpmbuild/SRPMS/$(shell sh -c "basename '$(spec)'|cut -f1 -d.")*.src.rpm $(outdir)
