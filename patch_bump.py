import re

# 1. Bump version in Build.gradle
app_gradle = "app/build.gradle.kts"
with open(app_gradle, "r") as f:
    text = f.read()

text = re.sub(r'versionCode = .*', 'versionCode = 13', text)
text = re.sub(r'versionName = ".*"', 'versionName = "1.0.9"', text)

with open(app_gradle, "w") as f:
    f.write(text)

# 2. Fix fdroiddata
fdroid_yml = "fdroiddata/metadata/com.kompact.yml"
with open(fdroid_yml, "r") as f:
    orig = f.read()

print(orig)
