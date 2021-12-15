.PHONY: installdeps srpm

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
		javassist \
		make \
		maven-source-plugin \
		maven \
		rpm-build \
		slf4j \
		slf4j-jdk14 \
		sed

srpm: installdeps
	$(eval SUFFIX=$(shell sh -c " echo '.$$(date -u +%Y%m%d%H%M%S).git$$(git rev-parse --short HEAD)'"))
	# changing the spec file as passing -D won't preserve the suffix when rebuilding in mock
	sed "s:%{?release_suffix}:${SUFFIX}:" -i vdsm-jsonrpc-java.spec.in
	.automation/build-srpm.sh
	cp rpmbuild/SRPMS/$(shell sh -c "basename '$(spec)'|cut -f1 -d.")*.src.rpm $(outdir)
