# SmallRye OpenSSL

Pre-built `netty-tcnative` native libraries linked against OpenSSL 3.x, enabling post-quantum cryptography (x25519mlkem768) support.

## Why this exists

The `netty-tcnative` JARs published on Maven Central contain native `.so` files compiled against OpenSSL 1.x (`libssl.so.10`). Modern Linux distributions ship OpenSSL 3.x (`libssl.so.3`), making those JARs unusable. Additionally, post-quantum key exchange (x25519mlkem768) requires OpenSSL 3.6+.

This project builds the same `netty-tcnative` JNI bridge from source against OpenSSL 3.x on UBI 9 (RHEL-based) and publishes it to Maven Central.

## Usage

Replace your `netty-tcnative` dependency:

```xml
<dependency>
  <groupId>io.smallrye</groupId>
  <artifactId>smallrye-openssl-native</artifactId>
  <version>1.0.0</version>
</dependency>
```

Your system must have OpenSSL 3.x and `libapr-1` installed. For x25519mlkem768 support, OpenSSL 3.6+ is required at runtime.

## How it works

1. Clones `netty-tcnative` at a pinned tag (currently `netty-tcnative-parent-2.0.76.Final`)
2. Builds the `openssl-dynamic` module inside a UBI 9 container with OpenSSL 3.x
3. Extracts the resulting `.so` (JNI bridge) and packages it into a JAR under `META-INF/native/`

The `.so` dynamically links to `libssl.so.3`, `libcrypto.so.3`, `libapr-1.so.0`, and `libc.so.6` — all standard sonames shared across every Linux distribution with OpenSSL 3.x.

## Building locally

```bash
mvn clean package -DskipTests
```

Requires: OpenSSL 3.x dev headers, APR dev headers, autoconf, automake, libtool, gcc, make, git, JDK 21.

## Building with Docker (reproducible)

```bash
docker build -t smallrye-openssl .
docker cp $(docker create smallrye-openssl):/build/native/target/smallrye-openssl-native-1.0.0-SNAPSHOT.jar .
```
