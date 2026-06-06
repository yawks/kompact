import re

fdroid_yml = "fdroiddata/metadata/com.kompact.yml"
with open(fdroid_yml, "r") as f:
    text = f.read()

# 1. Remove Donate URL
text = re.sub(r'Donate:.*?\n', '', text)

# 2. Fix AutoUpdateMode
text = re.sub(r'AutoUpdateMode: Version v%v', 'AutoUpdateMode: Version', text)

# 3. Add 1.0.9 block and fix subdir (remove it entirely as it defaults to root if blank, or use empty string, actually if it's the root we just remove `subdir: .` from all builds)
text = text.replace('    subdir: .\n', '')

build_block = """Builds:
  - versionName: 1.0.9
    versionCode: 13
    commit: v1.0.9
    timeout: 7200
    gradle:
      - yes
    output: app/build/outputs/apk/release/app-release-unsigned.apk

  - versionName: 1.0.8
    versionCode: 12
    commit: v1.0.8
    timeout: 7200
    gradle:
      - yes
    output: app/build/outputs/apk/release/app-release-unsigned.apk"""

text = re.sub(r'Builds:\s+- versionName: 1\.0\.8.*?output: app/build/outputs/apk/release/app-release-unsigned\.apk', build_block, text, flags=re.DOTALL)

text = re.sub(r'CurrentVersion: 1\.0\.8', 'CurrentVersion: 1.0.9', text)
text = re.sub(r'CurrentVersionCode: 12', 'CurrentVersionCode: 13', text)

with open(fdroid_yml, "w") as f:
    f.write(text)

