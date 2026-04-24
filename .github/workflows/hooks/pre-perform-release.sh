#!/bin/bash
set -e

mkdir -p target/native-jars
gh run download "$GITHUB_RUN_ID" -n native-linux-x86_64 -D target/native-jars
gh run download "$GITHUB_RUN_ID" -n native-linux-aarch_64 -D target/native-jars
mkdir -p target/native-jars/maven-archiver
ls -la target/native-jars/
