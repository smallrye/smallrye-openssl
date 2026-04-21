#!/bin/bash
set -euo pipefail

REPO_URL="$1"
TAG="$2"
TARGET_DIR="$3"

CLONE_DIR="${TARGET_DIR}/netty-tcnative-src"
OUTPUT_DIR="${TARGET_DIR}/native-lib"

mkdir -p "${OUTPUT_DIR}"

# Clone netty-tcnative at the specified tag
if [ ! -d "${CLONE_DIR}" ]; then
  echo "Cloning netty-tcnative at tag ${TAG}..."
  git clone --depth 1 --branch "${TAG}" "${REPO_URL}" "${CLONE_DIR}"
fi

# Build openssl-classes and openssl-dynamic
echo "Building netty-tcnative openssl-dynamic against system OpenSSL..."
echo "OpenSSL version: $(openssl version)"

mvn -f "${CLONE_DIR}" install \
  -pl openssl-classes,openssl-dynamic \
  -am \
  -DskipTests \
  -Dmaven.javadoc.skip=true

# Extract the .so from the built JAR
# The JAR with the native lib has a platform classifier (e.g., linux-x86_64)
NATIVE_JAR=$(find "${CLONE_DIR}/openssl-dynamic/target" -name "netty-tcnative-*-linux-*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" | head -1)

if [ -z "${NATIVE_JAR}" ]; then
  echo "ERROR: Could not find built native JAR"
  exit 1
fi

echo "Found native JAR: ${NATIVE_JAR}"

# Extract .so files from JAR
cd "${OUTPUT_DIR}"
jar xf "${NATIVE_JAR}" META-INF/native/
mv META-INF/native/*.so . 2>/dev/null || true
rm -rf META-INF

echo "Native library built successfully:"
ls -la "${OUTPUT_DIR}"/*.so

# Verify it links against OpenSSL 3.x
echo "Dynamic dependencies:"
readelf -d "${OUTPUT_DIR}"/*.so | grep NEEDED
