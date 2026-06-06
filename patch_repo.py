import re

file_path = "app/src/main/java/com/kompact/data/CompressionRepository.kt"

with open(file_path, "r") as f:
    text = f.read()

orig_logic = """                val originalExif = if (supportsExif(config.format)) readExifData(resolver, uri) else null
                val exifToWrite = if (config.exifData != config.originalExifData) config.exifData else originalExif
                val finalBytes = if (supportsExif(config.format)) {
                    writeExifData(compressedBytes, exifToWrite, resolver, uri)
                } else {
                    compressedBytes
                }"""

new_logic = """                val finalBytes = if (supportsExif(config.format)) {
                    if (config.stripExif) {
                        writeExifData(compressedBytes, null, null, null)
                    } else {
                        val originalExif = readExifData(resolver, uri)
                        val exifToWrite = if (config.exifData != config.originalExifData) config.exifData else originalExif
                        writeExifData(compressedBytes, exifToWrite, resolver, uri)
                    }
                } else {
                    compressedBytes
                }"""

if orig_logic in text:
    text = text.replace(orig_logic, new_logic)
    with open(file_path, "w") as f:
        f.write(text)
    print("Patched Repo")
else:
    print("Could not patch Repo")
