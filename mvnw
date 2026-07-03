#!/usr/bin/env sh
set -eu

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
MAVEN_VERSION="3.9.11"
MAVEN_HOME="$BASE_DIR/.mvn/apache-maven-$MAVEN_VERSION"
MAVEN_ZIP="$BASE_DIR/.mvn/apache-maven-$MAVEN_VERSION-bin.zip"
MAVEN_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.zip"

if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  mkdir -p "$BASE_DIR/.mvn"
  if [ ! -f "$MAVEN_ZIP" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fsSL "$MAVEN_URL" -o "$MAVEN_ZIP"
    elif command -v wget >/dev/null 2>&1; then
      wget -q "$MAVEN_URL" -O "$MAVEN_ZIP"
    else
      echo "curl or wget is required to bootstrap Maven." >&2
      exit 1
    fi
  fi
  unzip -q -o "$MAVEN_ZIP" -d "$BASE_DIR/.mvn"
fi

exec "$MAVEN_HOME/bin/mvn" "$@"

