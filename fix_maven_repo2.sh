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

create_pom "com.android.tools.analytics-library" "crash" "31.2.0"
create_pom "com.android.tools.analytics-library" "shared" "31.2.0"
create_pom "com.android.tools.lint" "lint-model" "31.2.0"
create_pom "com.android.tools.lint" "lint-typedef-remover" "31.2.0"
create_pom "androidx.databinding" "databinding-compiler-common" "8.2.0"
create_pom "androidx.databinding" "databinding-common" "8.2.0"
create_pom "com.android.databinding" "baseLibrary" "8.2.0"
create_pom "com.android.tools.layoutlib" "layoutlib-api" "31.2.0"
create_pom "com.android.tools.utp" "android-device-provider-ddmlib-proto" "31.2.0"
create_pom "com.android.tools.utp" "android-device-provider-gradle-proto" "31.2.0"
create_pom "com.android.tools.utp" "android-test-plugin-host-additional-test-output-proto" "31.2.0"

echo "Done fixing maven repo part 2"
