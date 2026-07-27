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

create_pom "org.jetbrains.kotlin" "kotlin-reflect" "2.0.21"
create_pom "org.jetbrains.kotlin" "kotlin-gradle-plugins-bom" "1.9.20" "pom"
create_pom "com.google.devtools.ksp" "symbol-processing-gradle-plugin" "1.9.20-1.0.14"
create_pom "com.android.tools.build" "gradle-settings-api" "8.2.0"
create_pom "com.android.tools.ddms" "ddmlib" "31.2.0"
create_pom "com.android.tools.build" "aapt2-proto" "8.2.0-10154469"
create_pom "com.android.tools.build" "aaptcompiler" "8.2.0"

echo "Done fixing maven repo part 3"
