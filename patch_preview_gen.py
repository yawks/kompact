import re

file_path = "app/src/main/java/com/kompact/data/ImagePreviewGenerator.kt"

with open(file_path, "r") as f:
    text = f.read()

orig_logic = """        val exifBytes = if (supportsExif(config.format)) writeExifData(previewBytes, config.exifData) else previewBytes"""
new_logic = """        val exifBytes = if (supportsExif(config.format)) {
            if (config.stripExif) writeExifData(previewBytes, null) else writeExifData(previewBytes, config.exifData)
        } else previewBytes"""

if orig_logic in text:
    text = text.replace(orig_logic, new_logic)
    with open(file_path, "w") as f:
        f.write(text)
    print("Patched Preview Gen")
else:
    print("Could not patch Preview Gen")
