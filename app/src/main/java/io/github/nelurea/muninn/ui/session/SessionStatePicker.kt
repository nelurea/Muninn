package io.github.nelurea.muninn.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nelurea.muninn.data.db.StateVocabularyEntity

@Composable
fun SessionStatePicker(
    vocabulary: List<StateVocabularyEntity>,
    selectedStateIds: Set<Long>,
    newStateLabel: String,
    onNewStateLabelChange: (String) -> Unit,
    onToggleState: (
        StateVocabularyEntity
    ) -> Unit,
    onAddState: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit
) {
    AlertDialog(
        onDismissRequest =
            onSkip,
        title = {
            Text(
                "How are you feeling?"
            )
        },
        text = {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text(
                    text =
                        "Choose a state if you want.",
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium
                )

                if (
                    vocabulary.isNotEmpty()
                ) {
                    Spacer(
                        Modifier.height(
                            12.dp
                        )
                    )

                    FlowRow(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {
                        vocabulary.forEach {
                                state ->

                            FilterChip(
                                selected =
                                    state.id in
                                            selectedStateIds,
                                onClick = {
                                    onToggleState(
                                        state
                                    )
                                },
                                label = {
                                    Text(
                                        state.label
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(
                    Modifier.height(
                        16.dp
                    )
                )

                OutlinedTextField(
                    value =
                        newStateLabel,
                    onValueChange =
                        onNewStateLabelChange,
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    label = {
                        Text(
                            "Add State"
                        )
                    },
                    singleLine =
                        true
                )

                TextButton(
                    enabled =
                        newStateLabel
                            .isNotBlank(),
                    onClick =
                        onAddState
                ) {
                    Text(
                        "Add"
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick =
                    onDone
            ) {
                Text(
                    "Done"
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick =
                    onSkip
            ) {
                Text(
                    "Skip"
                )
            }
        }
    )
}