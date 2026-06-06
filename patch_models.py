import re

file_path = "app/src/main/java/com/kompact/model/CompressionModels.kt"

with open(file_path, "r") as f:
    text = f.read()

text = re.sub(r'(val originalExifData\s*:\s*ExifData\?\s*=\s*null,)', r'\1\n    val stripExif: Boolean = false,', text)

with open(file_path, "w") as f:
    f.write(text)

