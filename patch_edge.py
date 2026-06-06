import re

file_path = "app/src/main/java/com/kompact/util/ImageProcessing.kt"

with open(file_path, "r") as f:
    text = f.read()

text = text.replace("private const val MAX_IMAGE_EDGE = 4096", "private const val MAX_IMAGE_EDGE = 16384")

with open(file_path, "w") as f:
    f.write(text)

