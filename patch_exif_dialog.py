import re

file_path = "app/src/main/java/com/kompact/ui/KompactScreen.kt"

with open(file_path, "r") as f:
    text = f.read()

orig_call = """    if (state.isExifEditorVisible && (exifEditingEnabled || state.selectedFiles.isEmpty())) {
        ExifEditorDialog(
            exifData = state.exifData,
            originalExifData = state.originalExifData,
            onExifDataChange = onExifDataChange,
            onDismiss = onToggleExifEditor,
        )
    }"""

new_call = """    // Local state for controlling the bulk EXIF removal dialog
    var showBulkExifRemovalDialog by remember { mutableStateOf(false) }

    // Show bulk EXIF removal dialog when multiple images are selected and EXIF editor is visible
    if (state.isExifEditorVisible && state.sourceCategory == SourceCategory.IMAGE && state.selectedFiles.size > 1) {
        showBulkExifRemovalDialog = true
        onToggleExifEditor() // Reset the global state
    }

    if (showBulkExifRemovalDialog) {
        RemoveExifDialog(
            onConfirm = {
                onImageConfigChange(state.imageConfig.copy(stripExif = true))
                showBulkExifRemovalDialog = false
            },
            onDismiss = { showBulkExifRemovalDialog = false }
        )
    }

    if (state.isExifEditorVisible && state.sourceCategory == SourceCategory.IMAGE && state.selectedFiles.size <= 1) {
        ExifEditorDialog(
            exifData = state.exifData,
            originalExifData = state.originalExifData,
            onExifDataChange = onExifDataChange,
            stripExif = state.imageConfig.stripExif,
            onStripExifChange = { strip -> onImageConfigChange(state.imageConfig.copy(stripExif = strip)) },
            onDismiss = onToggleExifEditor,
        )
    }"""

if orig_call in text:
    text = text.replace(orig_call, new_call)
    print("Patched dialog invocation")
else:
    print("Could not patch dialog invocation")

# Now change ExifEditorDialog signature
sig_orig = """fun ExifEditorDialog(
    exifData: ExifData?,
    originalExifData: ExifData?,
    onExifDataChange: (ExifData?) -> Unit,
    onDismiss: () -> Unit,
) {"""

sig_new = """fun ExifEditorDialog(
    exifData: ExifData?,
    originalExifData: ExifData?,
    onExifDataChange: (ExifData?) -> Unit,
    stripExif: Boolean = false,
    onStripExifChange: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
) {"""
text = text.replace(sig_orig, sig_new)

# Add RemoveExifDialog
remove_exif_dialog = """
@Composable
fun RemoveExifDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.edit_exif_metadata), style = MaterialTheme.typography.titleMedium)
                Text("Do you want to delete all metadata from all selected images? This action cannot be undone.")
                
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onConfirm) { 
                        Text(stringResource(R.string.apply)) 
                    }
                }
            }
        }
    }
}
"""

if "fun RemoveExifDialog" not in text:
    text = text.replace("fun ExifEditorDialog(", remove_exif_dialog + "\nfun ExifEditorDialog(")

with open(file_path, "w") as f:
    f.write(text)
