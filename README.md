# SmallRye OpenSSL

Pre-built `netty-tcnative` native libraries linked against OpenSSL 3.x, enabling post-quantum cryptography (x25519mlkem768) support.

## Why this exists

The `netty-tcnative` JARs on Maven Central contain native `.so` files compiled against OpenSSL 1.x (`libssl.so.10`). Modern Linux distributions ship OpenSSL 3.x (`libssl.so.3`), making those JARs unusable. Post-quantum key exchange (x25519mlkem768) requires OpenSSL 3.6+ at runtime.

This project builds the `netty-tcnative` JNI bridge from source against OpenSSL 3.x on RHEL/UBI and publishes platform-classified JARs to Maven Central.

## Usage

```xml
<dependency>
  <groupId>io.smallrye</groupId>
  <artifactId>smallrye-openssl</artifactId>
  <version>1.0.0</version>
  <classifier>linux-x86_64</classifier>
</dependency>
```

Or for ARM:

```xml
<classifier>linux-aarch_64</classifier>
```

Your system must have OpenSSL 3.x and `libapr-1` installed. For x25519mlkem768 support, OpenSSL 3.5+ is required at runtime.

## How it works

```
┌─────────────────────────┐     ┌─────────────────────────┐
│  RHEL 10 x86_64 container│     │ RHEL 10 aarch64 container│
│                         │     │                         │
│  1. Clone netty-tcnative│     │  1. Clone netty-tcnative│
│  2. mvn install          │     │  2. mvn install          │
│     -pl openssl-classes, │     │     -pl openssl-classes, │
│        openssl-dynamic   │     │        openssl-dynamic   │
│  3. Output: JAR with .so │     │  3. Output: JAR with .so │
│     linked to libssl.so.3│     │     linked to libssl.so.3│
└───────────┬─────────────┘     └───────────┬─────────────┘
            │                               │
            └───────────┬───────────────────┘
                        ▼
          ┌─────────────────────────┐
          │    Assembly step         │
          │                         │
          │  Collects both JARs,    │
          │  attaches as classified │
          │  artifacts, deploys to  │
          │  Maven Central          │
          └─────────────────────────┘
```

## Project structure

```
smallrye-openssl/
├── pom.xml              # Parent POM, pins netty-tcnative version
├── build-native.sh      # Clones netty-tcnative, builds, extracts platform JAR
├── assembly/
│   └── pom.xml          # Collects platform JARs, attaches classifiers, deploys
└── .github/workflows/
    └── build.yml        # CI: two platform builds → assembly → Maven Central
```

## Building locally

```bash
# Build the native JAR for your current platform
mkdir -p target/native-jars
./build-native.sh \
  https://github.com/netty/netty-tcnative.git \
  netty-tcnative-parent-2.0.76.Final \
  target/native-jars

# Package the assembly (needs the native JARs in target/native-jars/)
mvn package -pl assembly -Dnative.jars.dir=$(pwd)/target/native-jars
```

Requires: OpenSSL 3.x dev headers, APR dev headers, autoconf, automake, libtool, gcc, make, git, JDK 21.
