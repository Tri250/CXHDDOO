#!/bin/bash
set -e

REPO=/workspace/local-maven-repo

create_pom() {
    local group=$1
    local artifact=$2
    local version=$3
    local packaging=${4:-jar}
    local dir="$REPO/${group//.//}/$artifact/$version"
    mkdir -p "$dir"
    cat > "$dir/$artifact-$version.pom" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>$group</groupId>
  <artifactId>$artifact</artifactId>
  <version>$version</version>
  <packaging>$packaging</packaging>
</project>
EOF
    if [ "$packaging" = "jar" ]; then
        touch "$dir/$artifact-$version.jar"
    fi
}

create_pom "io.grpc" "grpc-stub" "1.45.1"
create_pom "com.google.testing.platform" "core-proto" "0.0.8-alpha08"
create_pom "com.google.flatbuffers" "flatbuffers-java" "1.12.0"
create_pom "org.jetbrains.kotlin" "kotlin-gradle-plugin-model" "1.9.20"
create_pom "org.jetbrains.kotlin" "kotlin-gradle-plugin-api" "1.9.20"
create_pom "org.jetbrains.kotlin" "kotlin-gradle-plugin-idea-proto" "1.9.20"
create_pom "org.jetbrains.kotlin" "kotlin-gradle-plugin-idea" "1.9.20"
create_pom "org.jetbrains.kotlin" "kotlin-klib-commonizer-api" "1.9.20"
create_pom "org.jetbrains.kotlin" "kotlin-native-utils" "1.9.20"
create_pom "org.jetbrains.kotlin" "kotlin-util-klib" "1.9.20"
create_pom "org.jetbrains.kotlin" "kotlin-build-tools-api" "1.9.20"
create_pom "org.jetbrains.kotlin" "kotlin-android-extensions" "1.9.20"
create_pom "org.jetbrains.kotlin" "kotlin-compiler-runner" "1.9.20"
create_pom "org.jetbrains.kotlin" "kotlin-compiler-embeddable" "1.9.20"
create_pom "org.jetbrains.kotlin" "kotlin-scripting-compiler-embeddable" "1.9.20"
create_pom "org.jetbrains.kotlin" "kotlin-scripting-compiler-impl-embeddable" "1.9.20"
create_pom "io.grpc" "grpc-protobuf-lite" "1.45.1"
create_pom "io.grpc" "grpc-api" "1.45.1"
create_pom "javax.inject" "javax.inject" "1"
create_pom "net.sf.kxml" "kxml2" "2.3.0"
create_pom "org.bouncycastle" "bcprov-jdk15on" "1.67"
create_pom "org.jetbrains.intellij.deps" "trove4j" "1.0.20200330"
create_pom "xerces" "xercesImpl" "2.12.0"

touch $REPO/com/android/signflinger/8.2.0/signflinger-8.2.0.jar

echo "Done fixing maven repo part 5"
