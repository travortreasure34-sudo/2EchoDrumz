package com.echodrumz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echodrumz.AppViewModel

@Composable
fun ProjectScreen(vm: AppViewModel) {
    val echo = LocalEcho.current
    var nameField by remember(vm.project.name) { mutableStateOf(vm.project.name) }
    var toDelete by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Project", fontWeight = FontWeight.Bold, color = echo.accent) }
        item {
            OutlinedTextField(nameField, { nameField = it }, label = { Text("Project name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveProject() }) { Text("Save") }
                OutlinedButton(onClick = { vm.saveAs(nameField) }) { Text("Save As") }
                OutlinedButton(onClick = { vm.renameCurrent(nameField) }) { Text("Rename") }
                OutlinedButton(onClick = { vm.newProject() }) { Text("New") }
            }
        }
        item { Text("Saved projects", fontWeight = FontWeight.Bold, color = echo.accent, modifier = Modifier.padding(top = 12.dp)) }
        if (vm.savedProjects.isEmpty()) item { Text("No saved projects yet.", fontSize = 13.sp, color = echo.text.copy(alpha = 0.6f)) }
        items(vm.savedProjects.toList()) { name ->
            Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .background(echo.surface).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), maxLines = 1)
                TextButton(onClick = { vm.openProject(name) }) { Text("Open") }
                TextButton(onClick = { vm.duplicateProject(name) }) { Text("Copy") }
                TextButton(onClick = { toDelete = name }) { Text("Delete") }
            }
        }
        item { Text("Theme", fontWeight = FontWeight.Bold, color = echo.accent, modifier = Modifier.padding(top = 12.dp)) }
        items(allThemes(vm.customHue)) { t ->
            Row(Modifier.fillMaxWidth().clickable { vm.setTheme(t.id) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = vm.themeId == t.id, onClick = { vm.setTheme(t.id) })
                Box(Modifier.size(16.dp).clip(CircleShape).background(t.accent))
                Text(t.label, Modifier.padding(start = 10.dp))
            }
        }
        if (vm.themeId == "custom") item {
            LabeledSlider("Accent", vm.customHue, 0f..359f, "${vm.customHue.toInt()}°") { vm.updateCustomHue(it) }
        }
        item {
            Text("Works fully offline. Projects, kits, samples and settings are stored privately on this device. " +
                "EchoDrumz has no audio export.", fontSize = 11.sp, color = echo.text.copy(alpha = 0.5f), modifier = Modifier.padding(top = 16.dp))
        }
    }

    toDelete?.let { n ->
        AlertDialog(onDismissRequest = { toDelete = null },
            title = { Text("Delete project?") }, text = { Text("\"$n\" will be permanently deleted.") },
            confirmButton = { TextButton(onClick = { vm.deleteProject(n); toDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } })
    }
}
