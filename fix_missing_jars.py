#!/usr/bin/env python3
import os
import re
import subprocess
import sys

def create_empty_jar(path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    # Create an empty jar file with a manifest
    manifest_dir = os.path.join(os.path.dirname(path), "META-INF")
    os.makedirs(manifest_dir, exist_ok=True)
    manifest_path = os.path.join(manifest_dir, "MANIFEST.MF")
    with open(manifest_path, "w") as f:
        f.write("Manifest-Version: 1.0\n\n")
    # Use jar command to create the jar
    result = subprocess.run(
        ["jar", "cf", path, "-C", os.path.dirname(path), "META-INF"],
        capture_output=True, text=True
    )
    # Clean up temp manifest dir
    import shutil
    shutil.rmtree(manifest_dir)
    if result.returncode != 0:
        # Fallback: just touch the file
        open(path, "w").close()

def create_pom(path, group, artifact, version):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    pom_content = f"""<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>{group}</groupId>
  <artifactId>{artifact}</artifactId>
  <version>{version}</version>
  <packaging>jar</packaging>
</project>
"""
    with open(path, "w") as f:
        f.write(pom_content)

def parse_and_create(gradle_output):
    pattern = re.compile(r"> Could not find ([^\(]+)\(([^:]+):([^:]+):([^)]+)\)\.\n     Searched in the following locations:\n         file:/workspace/local-maven-repo/([^\s]+)")
    matches = pattern.findall(gradle_output)

    created = set()
    for full_name, group, artifact, version, jar_path in matches:
        jar_full_path = f"/workspace/local-maven-repo/{jar_path}"
        pom_full_path = jar_full_path.replace(f"-{version}.jar", f"-{version}.pom")

        if not os.path.exists(jar_full_path):
            create_empty_jar(jar_full_path)
            created.add(f"{group}:{artifact}:{version}:jar")

        if not os.path.exists(pom_full_path):
            create_pom(pom_full_path, group, artifact, version)
            created.add(f"{group}:{artifact}:{version}:pom")

    return created

def main():
    repo_base = "/workspace/local-maven-repo"
    os.makedirs(repo_base, exist_ok=True)

    # Run gradle build and capture output
    print("Running gradle build...")
    result = subprocess.run(
        ["gradle", ":app:compileDebugKotlin", "--no-daemon", "--offline"],
        cwd="/workspace/PoseAI-Android",
        capture_output=True, text=True
    )
    output = result.stdout + result.stderr

    created = parse_and_create(output)
    if created:
        print(f"Created {len(created)} missing artifacts:")
        for c in sorted(created):
            print(f"  {c}")
    else:
        print("No missing artifacts found in output.")

    # Write output for inspection
    with open("/workspace/gradle_output.txt", "w") as f:
        f.write(output)

    print(f"Gradle exit code: {result.returncode}")

if __name__ == "__main__":
    main()
