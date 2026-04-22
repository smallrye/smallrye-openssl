#!/bin/bash
set -euo pipefail

#
# Builds netty-tcnative openssl-dynamic against the system's OpenSSL.
# Outputs the platform-classified JAR to a specified directory.
#
# Usage: build-native.sh <repo-url> <tag> <output-dir>
#
# The output JAR will be named: netty-tcnative-<arch>.jar
# where <arch> is detected from the build (e.g., linux-x86_64, linux-aarch_64)
#

REPO_URL="$1"
TAG="$2"
OUTPUT_DIR="$3"

CLONE_DIR="$(mktemp -d)/netty-tcnative"

mkdir -p "${OUTPUT_DIR}"

echo "=== System info ==="
echo "OpenSSL: $(openssl version)"
echo "Arch: $(uname -m)"
echo "OS: $(cat /etc/os-release | grep PRETTY_NAME | cut -d= -f2)"
echo ""

echo "=== Cloning netty-tcnative at tag ${TAG} ==="
git clone --depth 1 --branch "${TAG}" "${REPO_URL}" "${CLONE_DIR}"

echo "=== Building openssl-dynamic ==="
mvn -f "${CLONE_DIR}" install \
  -pl openssl-dynamic \
  -am \
  -DskipTests \
  -Dmaven.javadoc.skip=true

echo "=== Collecting build output ==="

# Find the platform-classified JAR (e.g., netty-tcnative-2.0.76.Final-linux-x86_64.jar)
NATIVE_JAR=$(find "${CLONE_DIR}/openssl-dynamic/target" \
  -name "netty-tcnative-*-linux-*.jar" \
  ! -name "*-sources.jar" \
  ! -name "*-javadoc.jar" \
  | head -1)

if [ -z "${NATIVE_JAR}" ]; then
  echo "ERROR: Could not find built native JAR"
  exit 1
fi

# Extract the classifier from the filename (e.g., linux-x86_64 or linux-x86_64-fedora)
CLASSIFIER=$(basename "${NATIVE_JAR}" | sed -E "s/netty-tcnative-[^-]+-//; s/\.jar//")

echo "Native JAR: ${NATIVE_JAR}"
echo "Classifier: ${CLASSIFIER}"

cp "${NATIVE_JAR}" "${OUTPUT_DIR}/netty-tcnative-${CLASSIFIER}.jar"

# Also copy the sources JAR (same for all platforms)
SOURCES_JAR=$(find "${CLONE_DIR}/openssl-dynamic/target" -name "*-sources.jar" | head -1)
if [ -n "${SOURCES_JAR}" ]; then
  cp "${SOURCES_JAR}" "${OUTPUT_DIR}/sources.jar"
fi


echo ""
echo "=== Done ==="
echo "Output: ${OUTPUT_DIR}/netty-tcnative-${CLASSIFIER}.jar"
