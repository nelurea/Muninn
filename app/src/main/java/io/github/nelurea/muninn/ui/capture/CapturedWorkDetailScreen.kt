package io.github.nelurea.muninn.ui.capture

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.nelurea.muninn.data.db.AestheticResponseVocabularyEntity
import io.github.nelurea.muninn.data.db.AttractionVocabularyEntity
import io.github.nelurea.muninn.data.db.CapturedWorkWithMedia
import io.github.nelurea.muninn.data.db.PurposeVocabularyEntity
import io.github.nelurea.muninn.data.repository.CapturedWorkRepository
import kotlinx.coroutines.launch

private val attractionDimensions =
    listOf(
        "CHARACTER_IDENTITY" to
                "Character",
        "CHARACTER_ATTRIBUTE" to
                "Attribute",
        "EXPRESSION_GESTURE" to
                "Expression",
        "BODY_COSTUME" to
                "Body / Costume",
        "RELATIONSHIP_INTERACTION" to
                "Relationship",
        "SCENARIO" to
                "Scenario",
        "COMPOSITION_CAMERA" to
                "Composition",
        "STYLE_RENDERING" to
                "Style",
        "COLOR_LIGHT" to
                "Color / Light",
        "ATMOSPHERE" to
                "Atmosphere"
    )

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun CapturedWorkDetailScreen(
    workId: Long,
    repository: CapturedWorkRepository
) {
    var capturedWork by remember {
        mutableStateOf<CapturedWorkWithMedia?>(
            null
        )
    }

    var loading by remember {
        mutableStateOf(true)
    }

    var purposeVocabulary by remember {
        mutableStateOf<
                List<PurposeVocabularyEntity>
                >(
            emptyList()
        )
    }

    var selectedPurposes by remember {
        mutableStateOf<
                List<PurposeVocabularyEntity>
                >(
            emptyList()
        )
    }

    var newPurposeLabel by remember {
        mutableStateOf("")
    }

    var attractionVocabulary by remember {
        mutableStateOf<
                List<AttractionVocabularyEntity>
                >(
            emptyList()
        )
    }

    var selectedWorkAttractions by remember {
        mutableStateOf<
                List<AttractionVocabularyEntity>
                >(
            emptyList()
        )
    }

    var selectedMediaAttractions by remember {
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

    var attractionTargetIsPage by remember {
        mutableStateOf(false)
    }

    var newAttractionLabel by remember {
        mutableStateOf("")
    }

    var responseVocabulary by remember {
        mutableStateOf<
                List<AestheticResponseVocabularyEntity>
                >(
            emptyList()
        )
    }

    var selectedResponses by remember {
        mutableStateOf<
                List<AestheticResponseVocabularyEntity>
                >(
            emptyList()
        )
    }

    var newResponseLabel by remember {
        mutableStateOf("")
    }

    val scope =
        rememberCoroutineScope()

    suspend fun reloadPurposeState() {
        purposeVocabulary =
            repository.getPurposeVocabulary()

        selectedPurposes =
            repository.getPurposesForWork(
                workId
            )
    }

    suspend fun reloadWorkAttractionState() {
        attractionVocabulary =
            repository.getAttractionVocabulary()

        selectedWorkAttractions =
            repository.getAttractionsForWork(
                workId
            )
    }

    suspend fun reloadMediaAttractionState(
        mediaId: Long
    ) {
        attractionVocabulary =
            repository.getAttractionVocabulary()

        selectedMediaAttractions =
            repository.getAttractionsForMedia(
                mediaId
            )
    }

    suspend fun reloadResponseState() {
        responseVocabulary =
            repository.getResponseVocabulary()

        selectedResponses =
            repository.getResponsesForWork(
                workId
            )
    }

    LaunchedEffect(workId) {
        capturedWork =
            repository.getWithMediaById(
                workId
            )

        reloadPurposeState()
        reloadWorkAttractionState()
        reloadResponseState()

        loading =
            false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Captured Work"
                    )
                }
            )
        }
    ) {
            paddingValues ->

        when {
            loading -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                paddingValues
                            ),
                    contentAlignment =
                        Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            capturedWork == null -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                paddingValues
                            ),
                    contentAlignment =
                        Alignment.Center
                ) {
                    Text(
                        "Captured work not found"
                    )
                }
            }

            else -> {
                val item =
                    capturedWork
                        ?: return@Scaffold

                val media =
                    remember(
                        item.media
                    ) {
                        item.media
                            .sortedBy {
                                it.mediaIndex
                            }
                    }

                val pagerState =
                    rememberPagerState(
                        pageCount = {
                            media.size
                        }
                    )

                val currentMedia =
                    media.getOrNull(
                        pagerState.currentPage
                    )

                LaunchedEffect(
                    currentMedia?.id
                ) {
                    val mediaId =
                        currentMedia
                            ?.id

                    selectedMediaAttractions =
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
                }

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                paddingValues
                            )
                            .verticalScroll(
                                rememberScrollState()
                            )
                ) {

                    if (
                        media.isNotEmpty()
                    ) {
                        HorizontalPager(
                            state =
                                pagerState,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(
                                        520.dp
                                    )
                        ) {
                                page ->

                            val pageMedia =
                                media[page]

                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                            ) {
                                AsyncImage(
                                    model =
                                        pageMedia
                                            .localUri,
                                    contentDescription =
                                        null,
                                    modifier =
                                        Modifier
                                            .fillMaxSize(),
                                    contentScale =
                                        ContentScale
                                            .Fit
                                )

                                if (
                                    pageMedia
                                        .isHighlighted
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .padding(
                                                    12.dp
                                                )
                                                .size(
                                                    28.dp
                                                )
                                                .background(
                                                    color =
                                                        MaterialTheme
                                                            .colorScheme
                                                            .surface
                                                            .copy(
                                                                alpha =
                                                                    0.8f
                                                            ),
                                                    shape =
                                                        CircleShape
                                                )
                                                .align(
                                                    Alignment
                                                        .TopEnd
                                                ),
                                        contentAlignment =
                                            Alignment
                                                .Center
                                    ) {
                                        Text(
                                            text =
                                                "✓",
                                            fontWeight =
                                                FontWeight
                                                    .Bold
                                        )
                                    }
                                }
                            }
                        }

                        if (
                            media.size > 1
                        ) {
                            Text(
                                text =
                                    "${pagerState.currentPage + 1} / ${media.size}",
                                modifier =
                                    Modifier
                                        .align(
                                            Alignment
                                                .CenterHorizontally
                                        )
                                        .padding(
                                            top =
                                                8.dp
                                        )
                            )
                        }
                    }

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    16.dp
                                )
                    ) {
                        item.work.title
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?.let {
                                    title ->

                                Text(
                                    text =
                                        title,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleLarge,
                                    fontWeight =
                                        FontWeight
                                            .Bold
                                )

                                Spacer(
                                    Modifier.height(
                                        8.dp
                                    )
                                )
                            }

                        Text(
                            text =
                                item.work
                                    .authorName,
                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium
                        )

                        item.work.caption
                            .takeIf {
                                it.isNotBlank()
                            }
                            ?.let {
                                    caption ->

                                Spacer(
                                    Modifier.height(
                                        16.dp
                                    )
                                )

                                Text(
                                    text =
                                        caption
                                )
                            }

                        val tags =
                            remember(
                                item.tags
                            ) {
                                item.tags
                                    .sortedBy {
                                        it.position
                                    }
                                    .map {
                                        it.tag
                                    }
                            }

                        if (
                            tags.isNotEmpty()
                        ) {
                            Spacer(
                                Modifier.height(
                                    16.dp
                                )
                            )

                            Text(
                                text =
                                    tags.joinToString(
                                        separator =
                                            "  "
                                    ) {
                                        "#$it"
                                    },
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodyMedium
                            )
                        }

                        Spacer(
                            Modifier.height(
                                24.dp
                            )
                        )

                        /*
                         * Purpose
                         */

                        Text(
                            text =
                                "Purpose",
                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium,
                            fontWeight =
                                FontWeight
                                    .SemiBold
                        )

                        Spacer(
                            Modifier.height(
                                8.dp
                            )
                        )

                        if (
                            purposeVocabulary
                                .isNotEmpty()
                        ) {
                            FlowRow(
                                horizontalArrangement =
                                    Arrangement
                                        .spacedBy(
                                            8.dp
                                        ),
                                verticalArrangement =
                                    Arrangement
                                        .spacedBy(
                                            8.dp
                                        )
                            ) {
                                purposeVocabulary
                                    .forEach {
                                            purpose ->

                                        val selected =
                                            selectedPurposes
                                                .any {
                                                    it.id ==
                                                            purpose.id
                                                }

                                        FilterChip(
                                            selected =
                                                selected,
                                            onClick = {
                                                scope.launch {
                                                    if (
                                                        selected
                                                    ) {
                                                        repository
                                                            .removePurposeFromWork(
                                                                workId =
                                                                    workId,
                                                                purposeVocabularyId =
                                                                    purpose.id
                                                            )
                                                    } else {
                                                        repository
                                                            .addPurposeToWork(
                                                                workId =
                                                                    workId,
                                                                label =
                                                                    purpose.label
                                                            )
                                                    }

                                                    reloadPurposeState()
                                                }
                                            },
                                            label = {
                                                Text(
                                                    purpose.label
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

                        OutlinedTextField(
                            value =
                                newPurposeLabel,
                            onValueChange = {
                                newPurposeLabel =
                                    it
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth(),
                            label = {
                                Text(
                                    "Add purpose"
                                )
                            },
                            singleLine =
                                true
                        )

                        TextButton(
                            enabled =
                                newPurposeLabel
                                    .isNotBlank(),
                            onClick = {
                                val label =
                                    newPurposeLabel
                                        .trim()

                                if (
                                    label.isNotBlank()
                                ) {
                                    scope.launch {
                                        repository
                                            .addPurposeToWork(
                                                workId =
                                                    workId,
                                                label =
                                                    label
                                            )

                                        newPurposeLabel =
                                            ""

                                        reloadPurposeState()
                                    }
                                }
                            }
                        ) {
                            Text(
                                "Add"
                            )
                        }

                        Spacer(
                            Modifier.height(
                                28.dp
                            )
                        )

                        /*
                         * Attraction
                         */

                        Text(
                            text =
                                "What do you like?",
                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium,
                            fontWeight =
                                FontWeight
                                    .SemiBold
                        )

                        Spacer(
                            Modifier.height(
                                8.dp
                            )
                        )

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

                        FlowRow(
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                )
                        ) {
                            FilterChip(
                                selected =
                                    !attractionTargetIsPage,
                                onClick = {
                                    attractionTargetIsPage =
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
                                    attractionTargetIsPage,
                                enabled =
                                    currentMedia !=
                                            null,
                                onClick = {
                                    attractionTargetIsPage =
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
                                16.dp
                            )
                        )

                        Text(
                            text =
                                "Type",
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
                                        (
                                            dimension,
                                            label
                                        ) ->

                                    FilterChip(
                                        selected =
                                            selectedDimension ==
                                                    dimension,
                                        onClick = {
                                            selectedDimension =
                                                dimension
                                        },
                                        label = {
                                            Text(
                                                label
                                            )
                                        }
                                    )
                                }
                        }

                        val visibleAttractions =
                            attractionVocabulary
                                .filter {
                                    it.dimension ==
                                            selectedDimension
                                }

                        if (
                            visibleAttractions
                                .isNotEmpty()
                        ) {
                            Spacer(
                                Modifier.height(
                                    14.dp
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
                                visibleAttractions
                                    .forEach {
                                            attraction ->

                                        val selected =
                                            if (
                                                attractionTargetIsPage
                                            ) {
                                                selectedMediaAttractions
                                                    .any {
                                                        it.id ==
                                                                attraction.id
                                                    }
                                            } else {
                                                selectedWorkAttractions
                                                    .any {
                                                        it.id ==
                                                                attraction.id
                                                    }
                                            }

                                        FilterChip(
                                            selected =
                                                selected,
                                            onClick = {
                                                scope.launch {
                                                    if (
                                                        attractionTargetIsPage
                                                    ) {
                                                        val mediaId =
                                                            currentMedia
                                                                ?.id
                                                                ?: return@launch

                                                        if (
                                                            selected
                                                        ) {
                                                            repository
                                                                .removeAttractionFromMedia(
                                                                    mediaId =
                                                                        mediaId,
                                                                    attractionVocabularyId =
                                                                        attraction.id
                                                                )
                                                        } else {
                                                            repository
                                                                .addAttractionToMedia(
                                                                    mediaId =
                                                                        mediaId,
                                                                    dimension =
                                                                        attraction.dimension,
                                                                    label =
                                                                        attraction.label
                                                                )
                                                        }

                                                        reloadMediaAttractionState(
                                                            mediaId
                                                        )
                                                    } else {
                                                        if (
                                                            selected
                                                        ) {
                                                            repository
                                                                .removeAttractionFromWork(
                                                                    workId =
                                                                        workId,
                                                                    attractionVocabularyId =
                                                                        attraction.id
                                                                )
                                                        } else {
                                                            repository
                                                                .addAttractionToWork(
                                                                    workId =
                                                                        workId,
                                                                    dimension =
                                                                        attraction.dimension,
                                                                    label =
                                                                        attraction.label
                                                                )
                                                        }

                                                        reloadWorkAttractionState()
                                                    }
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
                                12.dp
                            )
                        )

                        OutlinedTextField(
                            value =
                                newAttractionLabel,
                            onValueChange = {
                                newAttractionLabel =
                                    it
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth(),
                            label = {
                                Text(
                                    "Add term"
                                )
                            },
                            singleLine =
                                true
                        )

                        TextButton(
                            enabled =
                                newAttractionLabel
                                    .isNotBlank(),
                            onClick = {
                                val label =
                                    newAttractionLabel
                                        .trim()

                                if (
                                    label.isBlank()
                                ) {
                                    return@TextButton
                                }

                                scope.launch {
                                    if (
                                        attractionTargetIsPage
                                    ) {
                                        val mediaId =
                                            currentMedia
                                                ?.id
                                                ?: return@launch

                                        repository
                                            .addAttractionToMedia(
                                                mediaId =
                                                    mediaId,
                                                dimension =
                                                    selectedDimension,
                                                label =
                                                    label
                                            )

                                        reloadMediaAttractionState(
                                            mediaId
                                        )
                                    } else {
                                        repository
                                            .addAttractionToWork(
                                                workId =
                                                    workId,
                                                dimension =
                                                    selectedDimension,
                                                label =
                                                    label
                                            )

                                        reloadWorkAttractionState()
                                    }

                                    newAttractionLabel =
                                        ""
                                }
                            }
                        ) {
                            Text(
                                "Add"
                            )
                        }

                        Spacer(
                            Modifier.height(
                                28.dp
                            )
                        )

                        /*
                         * Aesthetic Response
                         */

                        Text(
                            text =
                                "How does it feel?",
                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium,
                            fontWeight =
                                FontWeight
                                    .SemiBold
                        )

                        Spacer(
                            Modifier.height(
                                8.dp
                            )
                        )

                        if (
                            responseVocabulary
                                .isNotEmpty()
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
                                responseVocabulary
                                    .forEach {
                                            response ->

                                        val selected =
                                            selectedResponses
                                                .any {
                                                    it.id ==
                                                            response.id
                                                }

                                        FilterChip(
                                            selected =
                                                selected,
                                            onClick = {
                                                scope.launch {
                                                    if (
                                                        selected
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

                                                    reloadResponseState()
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

                        OutlinedTextField(
                            value =
                                newResponseLabel,
                            onValueChange = {
                                newResponseLabel =
                                    it
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth(),
                            label = {
                                Text(
                                    "Add response"
                                )
                            },
                            singleLine =
                                true
                        )

                        TextButton(
                            enabled =
                                newResponseLabel
                                    .isNotBlank(),
                            onClick = {
                                val label =
                                    newResponseLabel
                                        .trim()

                                if (
                                    label.isBlank()
                                ) {
                                    return@TextButton
                                }

                                scope.launch {
                                    repository
                                        .addResponseToWork(
                                            workId =
                                                workId,
                                            label =
                                                label
                                        )

                                    newResponseLabel =
                                        ""

                                    reloadResponseState()
                                }
                            }
                        ) {
                            Text(
                                "Add"
                            )
                        }

                        Spacer(
                            Modifier.height(
                                28.dp
                            )
                        )

                        /*
                         * Existing metadata
                         */

                        DetailRow(
                            label =
                                "Published",
                            value =
                                item.work
                                    .publishedAt
                        )

                        DetailRow(
                            label =
                                "Captured",
                            value =
                                item.work
                                    .capturedAt
                        )

                        DetailRow(
                            label =
                                "Discovery",
                            value =
                                item.work
                                    .discoveryMode
                        )

                        DetailRow(
                            label =
                                "Query",
                            value =
                                item.work
                                    .discoveryQuery
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String?
) {
    if (
        value.isNullOrBlank()
    ) {
        return
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical =
                        3.dp
                ),
        horizontalArrangement =
            Arrangement.spacedBy(
                12.dp
            )
    ) {
        Text(
            text =
                "$label:",
            fontWeight =
                FontWeight
                    .SemiBold
        )

        Text(
            text =
                value
        )
    }
}