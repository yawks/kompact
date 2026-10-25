package com.kompact.folder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.kompact.R
import com.kompact.ui.theme.KompactTheme
import com.kompact.util.asReadableBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FolderShrinkActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = getSharedPreferences("folder-settings", MODE_PRIVATE)
        setContent {
            val workManager = remember { WorkManager.getInstance(this) }
            val works by remember { workManager.getWorkInfosForUniqueWorkFlow(FolderShrinkWorker.WORK_NAME) }
                .collectAsState(initial = emptyList())
            var lastJob by rememberSaveable { mutableStateOf(preferences.getString("job", null)) }
            val work = works.firstOrNull { !it.state.isFinished } ?: works.firstOrNull { it.id.toString() == lastJob }
            val busy = work != null && !work.state.isFinished
            var tree by rememberSaveable { mutableStateOf(preferences.getString("tree", null)) }
            var megapixels by rememberSaveable { mutableStateOf(preferences.getString("megapixels", "5") ?: "5") }
            var recursive by rememberSaveable { mutableStateOf(preferences.getBoolean("recursive", false)) }
            var error by rememberSaveable { mutableStateOf<String?>(null) }
            var starting by remember { mutableStateOf(false) }
            var details by remember { mutableStateOf("") }
            var finishedSummary by remember(work?.id) { mutableStateOf<FolderJobStore.Summary?>(null) }
            val scope = rememberCoroutineScope()
            val limit = megapixels.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.1..200.0 }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                if (uri != null) {
                    try {
                        contentResolver.takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        tree = uri.toString()
                        preferences.edit().putString("tree", tree).apply()
                        error = null
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            }
            val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            fun startJob() {
                val selectedTree = tree ?: return
                val selectedLimit = limit ?: return
                starting = true
                error = null
                scope.launch {
                    try {
                        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        val id = FolderShrinkWorker.enqueue(this@FolderShrinkActivity, Uri.parse(selectedTree), selectedLimit, recursive)
                        lastJob = id.toString()
                        preferences.edit().putString("job", lastJob).putString("megapixels", megapixels)
                            .putBoolean("recursive", recursive).apply()
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        starting = false
                    }
                }
            }
            val mediaLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) startJob() else error = getString(R.string.folder_location_permission)
            }
            LaunchedEffect(work?.id, work?.state) {
                if (work?.state?.isFinished == true) {
                    val (summary, report) = withContext(Dispatchers.IO) {
                        FolderJobStore(this@FolderShrinkActivity).use {
                            it.summary(work.id.toString()) to it.details(work.id.toString())
                        }
                    }
                    finishedSummary = summary
                    details = report
                } else {
                    finishedSummary = null
                    details = ""
                }
            }
            KompactTheme {
                Scaffold(topBar = {
                    TopAppBar(title = { Text(stringResource(R.string.folder_title)) }, navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.folder_back))
                        }
                    })
                }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.folder_description))
                        OutlinedButton(onClick = { picker.launch(tree?.let(Uri::parse)) }, enabled = !busy && !starting) {
                            Text(stringResource(R.string.folder_choose))
                        }
                        tree?.let { Text(Uri.decode(it.substringAfterLast('/')), style = MaterialTheme.typography.bodySmall) }
                        OutlinedTextField(value = megapixels, onValueChange = { megapixels = it },
                            label = { Text(stringResource(R.string.folder_limit)) },
                            supportingText = { Text(stringResource(R.string.folder_limit_help)) },
                            isError = limit == null, singleLine = true, enabled = !busy && !starting,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = recursive, onCheckedChange = { recursive = it }, enabled = !busy && !starting)
                            Text(stringResource(R.string.folder_recursive))
                        }
                        Text(stringResource(R.string.folder_preservation), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                mediaLocation.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                            } else startJob()
                        }, enabled = tree != null && limit != null && !busy && !starting && Build.VERSION.SDK_INT >= 26) {
                            Text(stringResource(R.string.folder_start))
                        }
                        if (Build.VERSION.SDK_INT < 26) Text(stringResource(R.string.folder_android_version))
                        work?.let { info ->
                            val data = if (info.state == WorkInfo.State.SUCCEEDED) info.outputData else info.progress
                            Text(stringResource(when (info.state) {
                                WorkInfo.State.SUCCEEDED -> R.string.folder_finished
                                WorkInfo.State.FAILED -> R.string.folder_failed
                                WorkInfo.State.CANCELLED -> R.string.folder_cancelled
                                WorkInfo.State.RUNNING -> R.string.folder_running
                                else -> R.string.folder_waiting
                            }), style = MaterialTheme.typography.titleMedium)
                            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(stringResource(R.string.folder_summary, finishedSummary?.processed ?: data.getInt("processed", 0),
                                finishedSummary?.total ?: data.getInt("total", 0), finishedSummary?.reduced ?: data.getInt("reduced", 0),
                                finishedSummary?.skipped ?: data.getInt("skipped", 0), finishedSummary?.errors ?: data.getInt("errors", 0),
                                (finishedSummary?.saved ?: data.getLong("saved", 0)).asReadableBytes()))
                            info.outputData.getString("error")?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            if (busy) OutlinedButton(onClick = { workManager.cancelWorkById(info.id) }) {
                                Text(stringResource(R.string.folder_stop))
                            }
                            if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall)
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}
