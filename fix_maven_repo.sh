#!/bin/bash
set -e

REPO=/workspace/local-maven-repo

# Function to create a simple POM
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

# Create parent POMs
create_pom "org.apache.commons" "commons-parent" "25" "pom"
create_pom "org.ow2" "ow2" "1.5" "pom"
create_pom "org.apache.httpcomponents" "httpcomponents-client" "4.5.6" "pom"
create_pom "org.jetbrains.kotlin" "kotlin-stdlib-jdk8" "1.9.0"
create_pom "com.android.tools.utp" "android-test-plugin-host-coverage-proto" "31.2.0"
create_pom "com.android.tools.utp" "android-test-plugin-host-emulator-control-proto" "31.2.0"
create_pom "com.android.tools.utp" "android-test-plugin-host-retention-proto" "31.2.0"
create_pom "org.apache.httpcomponents" "httpmime" "4.5.6"
create_pom "commons-io" "commons-io" "2.4"
create_pom "org.ow2.asm" "asm" "9.2"
create_pom "org.ow2.asm" "asm-analysis" "9.2"
create_pom "org.ow2.asm" "asm-commons" "9.2"
create_pom "org.ow2.asm" "asm-util" "9.2"

echo "Done fixing maven repo"
