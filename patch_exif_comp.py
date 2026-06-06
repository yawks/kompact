import re

file_path = "app/src/main/java/com/kompact/ui/KompactScreen.kt"

with open(file_path, "r") as f:
    text = f.read()

text = text.replace("@Composable\n\n@Composable\nfun RemoveExifDialog", "@Composable\nfun RemoveExifDialog")
text = text.replace("fun ExifEditorDialog(", "@Composable\nfun ExifEditorDialog(")
text = text.replace("@Composable\n@Composable\nfun ExifEditorDialog(", "@Composable\nfun ExifEditorDialog(")

with open(file_path, "w") as f:
    f.write(text)
