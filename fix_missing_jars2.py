#!/usr/bin/env python3
import re
import subprocess
import os

def run_gradle():
    result = subprocess.run(
        ["gradle", ":app:compileDebugKotlin", "--no-daemon", "--offline"],
        cwd="/workspace/PoseAI-Android",
        capture_output=True, text=True
    )
    return result.stdout + result.stderr

output = run_gradle()

# Parse missing JARs
pattern = re.compile(r"> Could not find ([^\(]+)\(([^:]+):([^:]+):([^)]+)\)\.\n\s+Searched in the following locations:\n\s+file:/workspace/local-maven-repo/([^\s]+)")
matches = pattern.findall(output)

print(f"Found {len(matches)} missing JARs")

for full_name, group, artifact, version, jar_path in matches:
    jar_full_path = f"/workspace/local-maven-repo/{jar_path}"
    dir_path = os.path.dirname(jar_full_path)
    
    # Determine source URL
    if group.startswith("com.android") or group.startswith("androidx"):
        base = "https://dl.google.com/dl/android/maven2"
    else:
        base = "https://repo1.maven.org/maven2"
    
    group_path = group.replace(".", "/")
    url = f"{base}/{group_path}/{artifact}/{version}/{artifact}-{version}.jar"
    
    if os.path.exists(jar_full_path):
        size = os.path.getsize(jar_full_path)
        if size > 1000:
            print(f"SKIP (exists, size={size}): {group}:{artifact}:{version}")
            continue
    
    os.makedirs(dir_path, exist_ok=True)
    print(f"DOWNLOADING: {url}")
    result = subprocess.run(["curl", "-s", "-L", "-o", jar_full_path, url], capture_output=True)
    
    if os.path.exists(jar_full_path):
        size = os.path.getsize(jar_full_path)
        print(f"  -> size={size} bytes")
        if size < 100:
            print(f"  -> WARNING: file too small, might be error page")
    else:
        print(f"  -> FAILED")

print("Done!")
