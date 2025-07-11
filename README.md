# JSON RPC Java client (vdsm-jsonrpc-java) for oVirt

[![Copr build status](https://copr.fedorainfracloud.org/coprs/ovirt/ovirt-master-snapshot/package/vdsm-jsonrpc-java/status_image/last_build.png)](https://copr.fedorainfracloud.org/coprs/ovirt/ovirt-master-snapshot/package/vdsm-jsonrpc-java/)
[![Maven Central](https://img.shields.io/maven-central/v/org.ovirt.vdsm-jsonrpc-java/vdsm-jsonrpc-java-client.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.ovirt.vdsm-jsonrpc-java/vdsm-jsonrpc-java-client)

Welcome to the vdsm-jsonrpc-java source repository.

## Building from Source

This package uses the standard autotools build system.

### Prerequisites
- Java 11+ SDK
- Maven 3.6+
- autotools (autoconf, automake)

### Build Steps
```bash
$ autoreconf -ivf
$ ./configure
$ make
$ make install
```

The Java SDK artifacts are available for distribution-specific installation at `$(builddir)/target`.

### Maven Central Publishing

To create a new release available on Maven Central one should provide a settings.xml file in the .m2 folder following the structure:
```xml
<settings>
    <servers>
        <server>
            <id>central</id>
            <username>${mvn_central_generated_username}</username>
            <password>${mvn_central_generated_pwd}</password>
        </server>
    </servers>
    <profiles>
        <profile>
            <id>central</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <gpg.executable>gpg2</gpg.executable>
                <gpg.passphrase>${gpg_passphrase}</gpg.passphrase>
            </properties>
        </profile>
    </profiles>
</settings>
```

After that all version tags need to be updated, that can be done using:

```bash
$ mvn release:prepare
```

For publishing artifacts to Maven Central (The `sign` profile is not required for SNAPSHOT versions):
```bash
$ mvn clean deploy -Psign
```

> [!NOTE]
> Snapshot versions do currently have a shelf life of 90 days on maven central.

## How to contribute

### Submitting patches

Patches are welcome!

Please submit patches to [GitHub:vdsm-jsonrpc-java](https://github.com/oVirt/vdsm-jsonrpc-java).

### Found a bug or documentation issue?
To submit a bug or suggest an enhancement for vdsm-jsonrpc-java please use [GitHub issues](https://github.com/ovirt/vdsm-jsonrpc-java/issues)

If you find a documentation issue on the oVirt website please navigate and click "Report an issue on GitHub" in the page footer.


## Still need help?
If you have any other questions, please join [oVirt Users forum / mailing list](https://lists.ovirt.org/admin/lists/users.ovirt.org/) and ask there.
