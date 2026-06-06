import re

with open('fdroid/metadata/com.kompact.yml', 'r') as f:
    data = f.read()

new_build = """Builds:
  - versionName: 1.0.7
    versionCode: 11
    commit: placeholder
    timeout: 7200
    subdir: .
    gradle:
      - yes
    output: app/build/outputs/apk/release/app-release-unsigned.apk

  - versionName: 1.0.6"""

data = data.replace('Builds:\n  - versionName: 1.0.6', new_build)

with open('fdroid/metadata/com.kompact.yml', 'w') as f:
    f.write(data)
