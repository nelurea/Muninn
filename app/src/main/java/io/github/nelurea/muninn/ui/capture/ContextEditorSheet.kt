package io.github.nelurea.muninn.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.nelurea.muninn.data.db.AestheticResponseVocabularyEntity
import io.github.nelurea.muninn.data.db.AttractionVocabularyEntity
import io.github.nelurea.muninn.data.db.PurposeVocabularyEntity
import io.github.nelurea.muninn.data.repository.CapturedWorkRepository
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.CenterFocusStrong
import io.github.nelurea.muninn.data.db.MediaFocusEntity

private data class AttractionDimensionUi(
    val key: String,
    val label: String,
    val icon:
    androidx.compose.ui.graphics.vector.ImageVector
)

private val attractionDimensions =
    listOf(
        AttractionDimensionUi(
            key =
                "CHARACTER_IDENTITY",
            label =
                "Character",
            icon =
                Icons.Default.Person
        ),
        AttractionDimensionUi(
            key =
                "CHARACTER_ATTRIBUTE",
            label =
                "Attribute",
            icon =
                Icons.Default.AutoAwesome
        ),
        AttractionDimensionUi(
            key =
                "EXPRESSION_GESTURE",
            label =
                "Expression",
            icon =
                Icons.Default.Face
        ),
        AttractionDimensionUi(
            key =
                "BODY_COSTUME",
            label =
                "Costume",
            icon =
                Icons.Default.Checkroom
        ),
        AttractionDimensionUi(
            key =
                "RELATIONSHIP_INTERACTION",
            label =
                "Relationship",
            icon =
                Icons.Default.Groups
        ),
        AttractionDimensionUi(
            key =
                "SCENARIO",
            label =
                "Scenario",
            icon =
                Icons.Default.Photo
        ),
        AttractionDimensionUi(
            key =
                "COMPOSITION_CAMERA",
            label =
                "Composition",
            icon =
                Icons.Default.CropFree
        ),
        AttractionDimensionUi(
            key =
                "STYLE_RENDERING",
            label =
                "Style",
            icon =
                Icons.Default.Brush
        ),
        AttractionDimensionUi(
            key =
                "COLOR_LIGHT",
            label =
                "Color / Light",
            icon =
                Icons.Default.LightMode
        ),
        AttractionDimensionUi(
            key =
                "ATMOSPHERE",
            label =
                "Atmosphere",
            icon =
                Icons.Default.Cloud
        )
    )

@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun ContextEditorSheet(
    workId: Long,
    currentMediaId: Long?,
    repository: CapturedWorkRepository,
    onDismiss: () -> Unit
) {
    var selectedTab by remember {
        mutableIntStateOf(1)
    }

    ModalBottomSheet(
        onDismissRequest =
            onDismiss
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        bottom = 24.dp
                    )
        ) {
            Text(
                text =
                    "Add context",
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
                fontWeight =
                    FontWeight.Bold,
                modifier =
                    Modifier.padding(
                        horizontal = 20.dp,
                        vertical = 8.dp
                    )
            )

            TabRow(
                selectedTabIndex =
                    selectedTab
            ) {
                Tab(
                    selected =
                        selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                    },
                    text = {
                        Text(
                            "Purpose"
                        )
                    },
                    icon = {
                        Icon(
                            imageVector =
                                Icons.Default.Bookmark,
                            contentDescription =
                                null
                        )
                    }
                )

                Tab(
                    selected =
                        selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                    },
                    text = {
                        Text(
                            "Attraction"
                        )
                    },
                    icon = {
                        Icon(
                            imageVector =
                                Icons.Default.Favorite,
                            contentDescription =
                                null
                        )
                    }
                )

                Tab(
                    selected =
                        selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                    },
                    text = {
                        Text(
                            "Response"
                        )
                    },
                    icon = {
                        Icon(
                            imageVector =
                                Icons.Default.Star,
                            contentDescription =
                                null
                        )
                    }
                )

                Tab(
                    selected =
                        selectedTab == 3,
                    onClick = {
                        selectedTab = 3
                    },
                    text = {
                        Text(
                            "Focus"
                        )
                    },
                    icon = {
                        Icon(
                            imageVector =
                                Icons.Default.CenterFocusStrong,
                            contentDescription =
                                null
                        )
                    }
                )
            }

            Spacer(
                Modifier.height(
                    16.dp
                )
            )

            when (
                selectedTab
            ) {
                0 -> {
                    PurposeEditor(
                        workId =
                            workId,
                        repository =
                            repository
                    )
                }

                1 -> {
                    AttractionEditor(
                        workId =
                            workId,
                        currentMediaId =
                            currentMediaId,
                        repository =
                            repository
                    )
                }

                2 -> {
                    ResponseEditor(
                        workId =
                            workId,
                        repository =
                            repository
                    )
                }

                3 -> {
                    FocusEditor(
                        currentMediaId =
                            currentMediaId,
                        repository =
                            repository
                    )
                }
            }
        }
    }
}

@Composable
private fun PurposeEditor(
    workId: Long,
    repository: CapturedWorkRepository
) {
    var vocabulary by remember {
        mutableStateOf<
                List<PurposeVocabularyEntity>
                >(
            emptyList()
        )
    }

    var selected by remember {
        mutableStateOf<
                List<PurposeVocabularyEntity>
                >(
            emptyList()
        )
    }

    var newLabel by remember {
        mutableStateOf("")
    }

    val scope =
        rememberCoroutineScope()

    suspend fun reload() {
        vocabulary =
            repository
                .getPurposeVocabulary()

        selected =
            repository
                .getPurposesForWork(
                    workId
                )
    }

    LaunchedEffect(
        workId
    ) {
        reload()
    }

    EditorSection(
        title =
            "Why would you want this later?"
    ) {
        if (
            vocabulary.isNotEmpty()
        ) {
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
                vocabulary
                    .forEach {
                            item ->

                        val isSelected =
                            selected.any {
                                it.id ==
                                        item.id
                            }

                        FilterChip(
                            selected =
                                isSelected,
                            onClick = {
                                scope.launch {
                                    if (
                                        isSelected
                                    ) {
                                        repository
                                            .removePurposeFromWork(
                                                workId =
                                                    workId,
                                                purposeVocabularyId =
                                                    item.id
                                            )
                                    } else {
                                        repository
                                            .addPurposeToWork(
                                                workId =
                                                    workId,
                                                label =
                                                    item.label
                                            )
                                    }

                                    reload()
                                }
                            },
                            label = {
                                Text(
                                    item.label
                                )
                            }
                        )
                    }
            }

            Spacer(
                Modifier.height(
                    12.dp
                )
            )
        }

        AddTermField(
            value =
                newLabel,
            label =
                "Add purpose",
            onValueChange = {
                newLabel =
                    it
            },
            onAdd = {
                val value =
                    newLabel.trim()

                if (
                    value.isNotBlank()
                ) {
                    scope.launch {
                        repository
                            .addPurposeToWork(
                                workId =
                                    workId,
                                label =
                                    value
                            )

                        newLabel =
                            ""

                        reload()
                    }
                }
            }
        )
    }
}

@Composable
private fun AttractionEditor(
    workId: Long,
    currentMediaId: Long?,
    repository: CapturedWorkRepository
) {
    var vocabulary by remember {
        mutableStateOf<
                List<AttractionVocabularyEntity>
                >(
            emptyList()
        )
    }

    var workSelected by remember {
        mutableStateOf<
                List<AttractionVocabularyEntity>
                >(
            emptyList()
        )
    }

    var pageSelected by remember {
        mutableStateOf<
                List<AttractionVocabularyEntity>
                >(
            emptyList()
        )
    }

    var selectedDimension by remember {
        mutableStateOf(
            "EXPRESSION_GESTURE"
        )
    }

    var pageTarget by remember {
        mutableStateOf(false)
    }

    var newLabel by remember {
        mutableStateOf("")
    }

    val scope =
        rememberCoroutineScope()

    suspend fun reloadWork() {
        vocabulary =
            repository
                .getAttractionVocabulary()

        workSelected =
            repository
                .getAttractionsForWork(
                    workId
                )
    }

    suspend fun reloadPage() {
        val mediaId =
            currentMediaId

        pageSelected =
            if (
                mediaId == null
            ) {
                emptyList()
            } else {
                repository
                    .getAttractionsForMedia(
                        mediaId
                    )
            }

        vocabulary =
            repository
                .getAttractionVocabulary()
    }

    LaunchedEffect(
        workId,
        currentMediaId
    ) {
        reloadWork()
        reloadPage()
    }

    EditorSection(
        title =
            "What draws you to it?"
    ) {
        Text(
            text =
                "Apply to",
            style =
                MaterialTheme
                    .typography
                    .labelLarge
        )

        Spacer(
            Modifier.height(
                6.dp
            )
        )

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {
            FilterChip(
                selected =
                    !pageTarget,
                onClick = {
                    pageTarget =
                        false
                },
                label = {
                    Text(
                        "Work"
                    )
                }
            )

            FilterChip(
                selected =
                    pageTarget,
                enabled =
                    currentMediaId !=
                            null,
                onClick = {
                    pageTarget =
                        true
                },
                label = {
                    Text(
                        "This page"
                    )
                }
            )
        }

        Spacer(
            Modifier.height(
                18.dp
            )
        )

        Text(
            text =
                "What kind?",
            style =
                MaterialTheme
                    .typography
                    .labelLarge
        )

        Spacer(
            Modifier.height(
                8.dp
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
            attractionDimensions
                .forEach {
                        dimension ->

                    FilterChip(
                        selected =
                            selectedDimension ==
                                    dimension.key,
                        onClick = {
                            selectedDimension =
                                dimension.key
                        },
                        leadingIcon = {
                            Icon(
                                imageVector =
                                    dimension.icon,
                                contentDescription =
                                    null
                            )
                        },
                        label = {
                            Text(
                                dimension.label
                            )
                        }
                    )
                }
        }

        val terms =
            vocabulary.filter {
                it.dimension ==
                        selectedDimension
            }

        if (
            terms.isNotEmpty()
        ) {
            Spacer(
                Modifier.height(
                    18.dp
                )
            )

            Text(
                text =
                    "Terms",
                style =
                    MaterialTheme
                        .typography
                        .labelLarge
            )

            Spacer(
                Modifier.height(
                    8.dp
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
                terms.forEach {
                        term ->

                    val isSelected =
                        if (
                            pageTarget
                        ) {
                            pageSelected.any {
                                it.id ==
                                        term.id
                            }
                        } else {
                            workSelected.any {
                                it.id ==
                                        term.id
                            }
                        }

                    FilterChip(
                        selected =
                            isSelected,
                        onClick = {
                            scope.launch {
                                if (
                                    pageTarget
                                ) {
                                    val mediaId =
                                        currentMediaId
                                            ?: return@launch

                                    if (
                                        isSelected
                                    ) {
                                        repository
                                            .removeAttractionFromMedia(
                                                mediaId =
                                                    mediaId,
                                                attractionVocabularyId =
                                                    term.id
                                            )
                                    } else {
                                        repository
                                            .addAttractionToMedia(
                                                mediaId =
                                                    mediaId,
                                                dimension =
                                                    term.dimension,
                                                label =
                                                    term.label
                                            )
                                    }

                                    reloadPage()
                                } else {
                                    if (
                                        isSelected
                                    ) {
                                        repository
                                            .removeAttractionFromWork(
                                                workId =
                                                    workId,
                                                attractionVocabularyId =
                                                    term.id
                                            )
                                    } else {
                                        repository
                                            .addAttractionToWork(
                                                workId =
                                                    workId,
                                                dimension =
                                                    term.dimension,
                                                label =
                                                    term.label
                                            )
                                    }

                                    reloadWork()
                                }
                            }
                        },
                        label = {
                            Text(
                                term.label
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

        AddTermField(
            value =
                newLabel,
            label =
                "Add term",
            onValueChange = {
                newLabel =
                    it
            },
            onAdd = {
                val value =
                    newLabel
                        .trim()

                if (
                    value.isBlank()
                ) {
                    return@AddTermField
                }

                scope.launch {
                    if (
                        pageTarget
                    ) {
                        val mediaId =
                            currentMediaId
                                ?: return@launch

                        repository
                            .addAttractionToMedia(
                                mediaId =
                                    mediaId,
                                dimension =
                                    selectedDimension,
                                label =
                                    value
                            )

                        reloadPage()
                    } else {
                        repository
                            .addAttractionToWork(
                                workId =
                                    workId,
                                dimension =
                                    selectedDimension,
                                label =
                                    value
                            )

                        reloadWork()
                    }

                    newLabel =
                        ""
                }
            }
        )
    }
}

@Composable
private fun ResponseEditor(
    workId: Long,
    repository: CapturedWorkRepository
) {
    var vocabulary by remember {
        mutableStateOf<
                List<AestheticResponseVocabularyEntity>
                >(
            emptyList()
        )
    }

    var selected by remember {
        mutableStateOf<
                List<AestheticResponseVocabularyEntity>
                >(
            emptyList()
        )
    }

    var newLabel by remember {
        mutableStateOf("")
    }

    val scope =
        rememberCoroutineScope()

    suspend fun reload() {
        vocabulary =
            repository
                .getResponseVocabulary()

        selected =
            repository
                .getResponsesForWork(
                    workId
                )
    }

    LaunchedEffect(
        workId
    ) {
        reload()
    }

    EditorSection(
        title =
            "How does it make you feel?"
    ) {
        if (
            vocabulary.isNotEmpty()
        ) {
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
                vocabulary
                    .forEach {
                            response ->

                        val isSelected =
                            selected.any {
                                it.id ==
                                        response.id
                            }

                        FilterChip(
                            selected =
                                isSelected,
                            onClick = {
                                scope.launch {
                                    if (
                                        isSelected
                                    ) {
                                        repository
                                            .removeResponseFromWork(
                                                workId =
                                                    workId,
                                                responseVocabularyId =
                                                    response.id
                                            )
                                    } else {
                                        repository
                                            .addResponseToWork(
                                                workId =
                                                    workId,
                                                label =
                                                    response.label
                                            )
                                    }

                                    reload()
                                }
                            },
                            label = {
                                Text(
                                    response.label
                                )
                            }
                        )
                    }
            }

            Spacer(
                Modifier.height(
                    12.dp
                )
            )
        }

        AddTermField(
            value =
                newLabel,
            label =
                "Add response",
            onValueChange = {
                newLabel =
                    it
            },
            onAdd = {
                val value =
                    newLabel
                        .trim()

                if (
                    value.isNotBlank()
                ) {
                    scope.launch {
                        repository
                            .addResponseToWork(
                                workId =
                                    workId,
                                label =
                                    value
                            )

                        newLabel =
                            ""

                        reload()
                    }
                }
            }
        )
    }
}

@Composable
private fun FocusEditor(
    currentMediaId: Long?,
    repository: CapturedWorkRepository
) {
    var attractionVocabulary by remember {
        mutableStateOf<
                List<AttractionVocabularyEntity>
                >(
            emptyList()
        )
    }

    var pageAttractions by remember {
        mutableStateOf<
                List<AttractionVocabularyEntity>
                >(
            emptyList()
        )
    }

    var focuses by remember {
        mutableStateOf<
                List<MediaFocusEntity>
                >(
            emptyList()
        )
    }

    var selectedAttractionId by remember {
        mutableStateOf<Long?>(
            null
        )
    }

    var note by remember {
        mutableStateOf("")
    }

    val scope =
        rememberCoroutineScope()

    suspend fun reload() {
        val mediaId =
            currentMediaId

        if (
            mediaId == null
        ) {
            attractionVocabulary =
                emptyList()

            pageAttractions =
                emptyList()

            focuses =
                emptyList()

            return
        }

        attractionVocabulary =
            repository
                .getAttractionVocabulary()

        pageAttractions =
            repository
                .getAttractionsForMedia(
                    mediaId
                )

        focuses =
            repository
                .getFocusForMedia(
                    mediaId
                )
    }

    LaunchedEffect(
        currentMediaId
    ) {
        selectedAttractionId =
            null

        note =
            ""

        reload()
    }

    EditorSection(
        title =
            "What matters on this page?"
    ) {
        if (
            currentMediaId == null
        ) {
            Text(
                text =
                    "No page is available."
            )

            return@EditorSection
        }

        Text(
            text =
                "Focus always belongs to the current page.",
            style =
                MaterialTheme
                    .typography
                    .bodyMedium
        )

        Spacer(
            Modifier.height(
                16.dp
            )
        )

        Text(
            text =
                "Related attraction",
            style =
                MaterialTheme
                    .typography
                    .labelLarge
        )

        Spacer(
            Modifier.height(
                8.dp
            )
        )

        if (
            pageAttractions.isEmpty()
        ) {
            Text(
                text =
                    "No attraction is attached to this page yet.",
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium
            )
        } else {
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
                pageAttractions
                    .forEach {
                            attraction ->

                        FilterChip(
                            selected =
                                selectedAttractionId ==
                                        attraction.id,
                            onClick = {
                                selectedAttractionId =
                                    if (
                                        selectedAttractionId ==
                                        attraction.id
                                    ) {
                                        null
                                    } else {
                                        attraction.id
                                    }
                            },
                            label = {
                                Text(
                                    attraction.label
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
                note,
            onValueChange = {
                note =
                    it
            },
            modifier =
                Modifier
                    .fillMaxWidth(),
            label = {
                Text(
                    "Focus note"
                )
            },
            placeholder = {
                Text(
                    "What about this page matters?"
                )
            }
        )

        TextButton(
            enabled =
                selectedAttractionId != null ||
                        note.isNotBlank(),
            onClick = {
                val mediaId =
                    currentMediaId

                if (
                    mediaId != null
                ) {
                    scope.launch {
                        repository
                            .addFocusToMedia(
                                mediaId =
                                    mediaId,
                                attractionVocabularyId =
                                    selectedAttractionId,
                                note =
                                    note
                            )

                        selectedAttractionId =
                            null

                        note =
                            ""

                        reload()
                    }
                }
            }
        ) {
            Text(
                "Add focus"
            )
        }

        if (
            focuses.isNotEmpty()
        ) {
            Spacer(
                Modifier.height(
                    20.dp
                )
            )

            Text(
                text =
                    "Saved focus",
                style =
                    MaterialTheme
                        .typography
                        .labelLarge
            )

            Spacer(
                Modifier.height(
                    8.dp
                )
            )

            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    )
            ) {
                focuses.forEach {
                        focus ->

                    val attraction =
                        attractionVocabulary
                            .firstOrNull {
                                it.id ==
                                        focus
                                            .attractionVocabularyId
                            }

                    FocusItem(
                        attractionLabel =
                            attraction?.label,
                        note =
                            focus.note,
                        onDelete = {
                            scope.launch {
                                repository
                                    .removeFocus(
                                        focus.id
                                    )

                                reload()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusItem(
    attractionLabel: String?,
    note: String?,
    onDelete: () -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical =
                        4.dp
                )
    ) {
        attractionLabel
            ?.let {
                Text(
                    text =
                        it,
                    style =
                        MaterialTheme
                            .typography
                            .titleSmall,
                    fontWeight =
                        FontWeight.SemiBold
                )
            }

        note
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {
                Text(
                    text =
                        it,
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium
                )
            }

        TextButton(
            onClick =
                onDelete
        ) {
            Text(
                "Remove"
            )
        }
    }
}

@Composable
private fun EditorSection(
    title: String,
    content:
    @Composable () -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 20.dp
                )
    ) {
        Text(
            text =
                title,
            style =
                MaterialTheme
                    .typography
                    .titleMedium,
            fontWeight =
                FontWeight.SemiBold
        )

        Spacer(
            Modifier.height(
                12.dp
            )
        )

        content()
    }
}

@Composable
private fun AddTermField(
    value: String,
    label: String,
    onValueChange:
        (String) -> Unit,
    onAdd:
        () -> Unit
) {
    OutlinedTextField(
        value =
            value,
        onValueChange =
            onValueChange,
        modifier =
            Modifier
                .fillMaxWidth(),
        label = {
            Text(
                label
            )
        },
        singleLine =
            true
    )

    TextButton(
        enabled =
            value.isNotBlank(),
        onClick =
            onAdd
    ) {
        Text(
            "Add"
        )
    }
}