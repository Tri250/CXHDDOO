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

# Create missing jars
touch $REPO/com/android/zipflinger/8.2.0/zipflinger-8.2.0.jar
touch $REPO/com/android/tools/annotations/31.2.0/annotations-31.2.0.jar
touch $REPO/io/perfmark/perfmark-api/0.23.0/perfmark-api-0.23.0.jar
touch $REPO/com/android/tools/build/apksig/8.2.0/apksig-8.2.0.jar

create_pom "org.jetbrains.kotlin" "kotlin-gradle-plugin-annotations" "1.9.20"
create_pom "org.jetbrains.kotlin" "kotlin-project-model" "1.9.20"
create_pom "org.jetbrains.kotlin" "kotlin-tooling-core" "1.9.20"
create_pom "org.jetbrains.kotlin" "kotlin-util-io" "1.9.20"
create_pom "org.jetbrains.kotlin" "kotlin-daemon-embeddable" "1.9.20"
create_pom "org.jetbrains.kotlin" "kotlin-daemon-client" "1.9.20"
create_pom "org.jetbrains.kotlinx" "kotlinx-coroutines-core-jvm" "1.5.0"
create_pom "org.jetbrains.kotlin" "kotlin-scripting-jvm" "1.9.20"
create_pom "org.jetbrains.kotlin" "kotlin-scripting-common" "1.9.20"
create_pom "net.java.dev.jna" "jna-platform" "5.6.0"
create_pom "net.java.dev.jna" "jna" "5.6.0"
create_pom "xml-apis" "xml-apis" "1.4.01"
create_pom "io.grpc" "grpc-context" "1.45.1"
create_pom "io.netty" "netty-handler" "4.1.72.Final"
create_pom "io.netty" "netty-codec-socks" "4.1.72.Final"
create_pom "io.netty" "netty-codec" "4.1.72.Final"
create_pom "io.netty" "netty-transport" "4.1.72.Final"
create_pom "io.netty" "netty-buffer" "4.1.72.Final"
create_pom "io.netty" "netty-resolver" "4.1.72.Final"
create_pom "io.netty" "netty-common" "4.1.72.Final"

echo "Done fixing maven repo part 4"
