package io.github.nelurea.muninn.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nelurea.muninn.settings.ContentRatingLevel
import io.github.nelurea.muninn.settings.SensitiveContentVisibilityPolicy
import io.github.nelurea.muninn.settings.SensitiveVisibilityMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensitiveContentVisibilityControl(
    policy: SensitiveContentVisibilityPolicy,
    onPolicyChange: (SensitiveContentVisibilityPolicy) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSheet by remember {
        mutableStateOf(false)
    }

    FilterChip(
        selected =
            policy.visibilityMode !=
                SensitiveVisibilityMode.VISIBLE,
        onClick = {
            showSheet = true
        },
        label = {
            Text(
                text = visibilityModeLabel(
                    policy.visibilityMode
                )
            )
        },
        modifier = modifier
    )

    if (!showSheet) {
        return
    }

    ModalBottomSheet(
        onDismissRequest = {
            showSheet = false
        }
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            verticalArrangement =
                Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Sensitive content",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Threshold",
                style = MaterialTheme.typography.labelLarge
            )

            Row(
                modifier =
                    Modifier.horizontalScroll(
                        rememberScrollState()
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                ContentRatingLevel.entries.forEach { threshold ->
                    FilterChip(
                        selected =
                            policy.threshold == threshold,
                        onClick = {
                            onPolicyChange(
                                policy.copy(
                                    threshold = threshold
                                )
                            )
                        },
                        label = {
                            Text(
                                text = thresholdLabel(
                                    threshold
                                )
                            )
                        }
                    )
                }
            }

            Text(
                text = "Visibility",
                style = MaterialTheme.typography.labelLarge
            )

            Row(
                modifier =
                    Modifier.horizontalScroll(
                        rememberScrollState()
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                SensitiveVisibilityMode.entries.forEach { mode ->
                    FilterChip(
                        selected =
                            policy.visibilityMode == mode,
                        onClick = {
                            onPolicyChange(
                                policy.copy(
                                    visibilityMode = mode
                                )
                            )
                        },
                        label = {
                            Text(
                                text = visibilityModeLabel(
                                    mode
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

private fun visibilityModeLabel(
    mode: SensitiveVisibilityMode
): String {
    return when (mode) {
        SensitiveVisibilityMode.HIDDEN -> "Hidden"
        SensitiveVisibilityMode.BLURRED -> "Blurred"
        SensitiveVisibilityMode.VISIBLE -> "Visible"
    }
}

private fun thresholdLabel(
    threshold: ContentRatingLevel
): String {
    return when (threshold) {
        ContentRatingLevel.R18 -> "R-18"
        ContentRatingLevel.SENSITIVE -> "Sensitive"
        ContentRatingLevel.R18G -> "R-18G"
    }
}
