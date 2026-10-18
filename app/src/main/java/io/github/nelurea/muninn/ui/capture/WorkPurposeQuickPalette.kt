package io.github.nelurea.muninn.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nelurea.muninn.data.db.PurposeUsageRow
import io.github.nelurea.muninn.data.db.PurposeVocabularyEntity
import io.github.nelurea.muninn.data.repository.CapturedWorkRepository
import kotlinx.coroutines.launch

/** A fast work-purpose entry point. Session purpose is inherited, never copied to each work. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkPurposeQuickPalette(
    workId: Long,
    sessionId: Long?,
    repository: CapturedWorkRepository,
    onDismiss: () -> Unit
) {
    val state = remember(workId, repository) {
        ContextEditorState(RepositoryContextEditorDataSource(repository))
    }
    state.bindTarget(workId, null)

    var sessionPurpose by remember(workId, sessionId) {
        mutableStateOf<PurposeVocabularyEntity?>(null)
    }
    var isDetour by remember(workId) { mutableStateOf(false) }
    var usage by remember(workId) { mutableStateOf<List<PurposeUsageRow>>(emptyList()) }
    var editingSession by remember(workId) { mutableStateOf(false) }
    var query by remember(workId) { mutableStateOf("") }
    var newLabel by remember(workId) { mutableStateOf("") }
    var loading by remember(workId) { mutableStateOf(true) }
    var busy by remember(workId) { mutableStateOf(false) }
    var error by remember(workId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(workId, sessionId, repository) {
        loading = true
        try {
            state.loadPurpose(workId)
            sessionPurpose = sessionId?.let { repository.getSessionPurpose(it) }
            isDetour = repository.isPurposeDetour(workId)
            usage = repository.getPurposeUsage()
        } catch (failure: Exception) {
            error = failure.message ?: "Unable to load Purpose"
        } finally {
            loading = false
        }
    }

    fun mutate(action: suspend () -> Unit) {
        if (loading || busy) return
        scope.launch {
            busy = true
            error = null
            try {
                action()
                state.loadPurpose(workId)
                sessionPurpose = sessionId?.let { repository.getSessionPurpose(it) }
                isDetour = repository.isPurposeDetour(workId)
                usage = repository.getPurposeUsage()
            } catch (failure: Exception) {
                error = failure.message ?: "Unable to save Purpose"
            } finally {
                busy = false
            }
        }
    }

    val vocabulary = state.purposeVocabulary(workId)
    val selectedWork = state.selectedPurposes(workId)
    val workIds = selectedWork.map { it.id }.toSet()
    val sessionSelectedId = sessionPurpose?.id
    val pinnedIds = if (editingSession) {
        setOfNotNull(sessionSelectedId)
    } else {
        workIds + setOfNotNull(sessionSelectedId)
    }
    val recent = PurposeSuggestionPolicy.recent(vocabulary, usage, pinnedIds)
    val recentIds = recent.map { it.id }.toSet()
    val frequent = PurposeSuggestionPolicy.frequent(vocabulary, usage, pinnedIds + recentIds)
    val groupIds = pinnedIds + recentIds + frequent.map { it.id }
    val other = PurposeSuggestionPolicy.search(
        vocabulary,
        query,
        if (query.isBlank()) groupIds else pinnedIds
    )

    fun toggle(item: PurposeVocabularyEntity) {
        mutate {
            if (editingSession) {
                // Setting a session purpose changes the shared context of every work in it.
                if (sessionId != null && sessionSelectedId != item.id) {
                    repository.setSessionPurpose(sessionId, item.label)
                }
            } else {
                state.setPurpose(workId, item, item.id in workIds)
                if (state.selectedPurposes(workId).isEmpty() && isDetour) {
                    repository.setPurposeDetour(workId, false)
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Purpose", style = MaterialTheme.typography.titleLarge)

            if (sessionId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Session Purpose", style = MaterialTheme.typography.titleSmall)
                        Text(
                            sessionPurpose?.label ?: "Not set",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!editingSession) {
                            Text(
                                if (isDetour) "Session context · not inherited (detour)"
                                else "Inherited by this work · no input required",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            editingSession = !editingSession
                            query = ""
                        },
                        enabled = !busy
                    ) {
                        Text(if (editingSession) "Edit Work" else "Change Session")
                    }
                }
                if (editingSession) {
                    Text(
                        "Changes to Session Purpose affect all works in this session.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (sessionPurpose != null) {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                mutate { repository.clearSessionPurpose(sessionId) }
                            }
                        ) { Text("Clear Session Purpose") }
                    }
                }
            }

            if (!editingSession) {
                Text("Work-specific Purpose", style = MaterialTheme.typography.titleSmall)
                if (selectedWork.isEmpty()) {
                    Text(
                        "None · the Session Purpose is enough unless you add one.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        selectedWork.forEach { PurposeChoiceChip(it, true, !busy) { toggle(it) } }
                    }
                }
                if (selectedWork.isNotEmpty() && sessionId != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(
                            checked = isDetour,
                            enabled = !busy,
                            onCheckedChange = { checked ->
                                mutate { repository.setPurposeDetour(workId, checked) }
                            }
                        )
                        Column {
                            Text("Detour / serendipity")
                            Text(
                                "Mark this work as a departure from the Session Purpose.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (loading) {
                CircularProgressIndicator()
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search purposes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 310.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (query.isBlank()) {
                        if (editingSession && sessionPurpose != null) {
                            Text("Current Session Purpose", style = MaterialTheme.typography.labelLarge)
                            PurposeChoiceChip(requireNotNull(sessionPurpose), true, !busy) { toggle(requireNotNull(sessionPurpose)) }
                        }
                        if (recent.isNotEmpty()) {
                            Text("Recently used", style = MaterialTheme.typography.labelLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(recent, key = { it.id }) { PurposeChoiceChip(it, false, !busy) { toggle(it) } }
                            }
                        }
                        if (frequent.isNotEmpty()) {
                            Text("Frequently used", style = MaterialTheme.typography.labelLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(frequent, key = { it.id }) { PurposeChoiceChip(it, false, !busy) { toggle(it) } }
                            }
                        }
                    }
                    Text(
                        if (query.isBlank()) "Other purposes" else "Matching purposes",
                        style = MaterialTheme.typography.labelLarge
                    )
                    if (other.isEmpty()) {
                        Text("No more matches", style = MaterialTheme.typography.bodySmall)
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            other.forEach { PurposeChoiceChip(it, false, !busy) { toggle(it) } }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newLabel,
                    onValueChange = { newLabel = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("New purpose") },
                    singleLine = true
                )
                Button(
                    enabled = !loading && !busy && newLabel.isNotBlank(),
                    onClick = {
                        val value = newLabel.trim()
                        mutate {
                            if (editingSession) {
                                sessionId?.let { repository.setSessionPurpose(it, value) }
                            } else {
                                state.addPurpose(workId, value)
                            }
                            newLabel = ""
                        }
                    }
                ) { Text("Add") }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy) {
                Text("Saving…", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PurposeChoiceChip(
    item: PurposeVocabularyEntity,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = { Text(item.label) }
    )
}
