FROM registry.access.redhat.com/ubi9/ubi:latest

RUN dnf install -y \
    java-21-openjdk-devel \
    maven \
    openssl-devel \
    apr-devel \
    autoconf \
    automake \
    libtool \
    gcc \
    make \
    git \
    && dnf clean all

WORKDIR /build
COPY . /build

RUN openssl version && \
    echo "Building native library..." && \
    mvn clean package -DskipTests
