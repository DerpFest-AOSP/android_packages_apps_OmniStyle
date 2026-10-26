/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package org.omnirom.omnistyle

import android.content.res.Resources
import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun HeaderPickerScreen(
    title: String,
    pickerMode: Boolean,
    initialPackKey: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var packs by remember { mutableStateOf<List<HeaderPack>?>(null) }
    var selectedPackKey by rememberSaveable { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf<LoadedPack?>(null) }
    var applied by remember { mutableStateOf<AppliedHeader?>(null) }

    LaunchedEffect(Unit) {
        val found = withContext(Dispatchers.IO) { HeaderRepository.queryPacks(context) }
        val current = HeaderRepository.appliedHeader(context)
        val (packKey, appliedPack) = withContext(Dispatchers.IO) {
            HeaderRepository.initialSelection(context, found, initialPackKey, current)
        }
        packs = found
        applied = current?.copy(packKey = appliedPack)
        if (selectedPackKey == null || found.none { it.key == selectedPackKey }) {
            selectedPackKey = packKey
        }
    }

    LaunchedEffect(selectedPackKey, packs) {
        val pack = packs?.firstOrNull { it.key == selectedPackKey } ?: return@LaunchedEffect
        if (loaded?.pack?.key != pack.key) loaded = null
        loaded = withContext(Dispatchers.IO) { HeaderRepository.loadPack(context, pack) }
    }

    val selectedPack = packs?.firstOrNull { it.key == selectedPackKey }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                subtitle = if (selectedPack == null) {
                    null
                } else {
                    { Text(selectedPack.label) }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        when {
            packs == null -> LoadingBox(Modifier.fillMaxSize().padding(innerPadding))
            packs!!.isEmpty() -> EmptyState(
                title = stringResource(R.string.header_empty_title),
                summary = stringResource(R.string.header_empty_summary),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
            else -> Column(Modifier.fillMaxSize().padding(innerPadding)) {
                PackChips(
                    packs = packs!!,
                    selectedPackKey = selectedPackKey,
                    onSelect = { selectedPackKey = it },
                )
                loaded?.let { pack ->
                    Text(
                        text = packSummary(pack),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                when {
                    loaded == null -> LoadingBox(Modifier.weight(1f).fillMaxWidth())
                    loaded!!.headers.isEmpty() -> EmptyState(
                        title = stringResource(R.string.header_pack_empty),
                        summary = null,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    else -> {
                        val pack = loaded!!
                        HeaderGrid(
                            pack = pack,
                            applied = applied,
                            pickerMode = pickerMode,
                            onOpenPack = { selectedPackKey = it },
                            onApply = { entry ->
                                HeaderRepository.applyHeader(
                                    context, pack.pack.packageName, entry.image
                                )
                                applied = AppliedHeader(
                                    pack.pack.packageName, entry.image, pack.pack.key
                                )
                                scope.launch {
                                    snackbar.showSnackbar(
                                        context.getString(R.string.custom_header_image_notice)
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PackChips(
    packs: List<HeaderPack>,
    selectedPackKey: String?,
    onSelect: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedPackKey, packs) {
        val index = packs.indexOfFirst { it.key == selectedPackKey }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(packs, key = { it.key }) { pack ->
            val selected = pack.key == selectedPackKey
            FilterChip(
                selected = selected,
                onClick = { onSelect(pack.key) },
                label = { Text(pack.label) },
                leadingIcon = if (selected) {
                    {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun HeaderGrid(
    pack: LoadedPack,
    applied: AppliedHeader?,
    pickerMode: Boolean,
    onOpenPack: (String) -> Unit,
    onApply: (HeaderEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reqWidth = thumbnailWidthPx()
    val showJumpCard = applied != null && !pack.contains(applied)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(320.dp),
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showJumpCard && applied != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "current") {
                CurrentHeaderCard(applied = applied, reqWidth = reqWidth, onOpenPack = onOpenPack)
            }
        }
        itemsIndexed(pack.headers, key = { index, entry -> "${entry.image}#$index" }) { _, entry ->
            val isApplied = applied?.packageName == pack.pack.packageName &&
                applied.image == entry.image
            HeaderCard(
                resources = pack.resources,
                packageName = pack.pack.packageName,
                entry = entry,
                selected = isApplied,
                reqWidth = reqWidth,
                onClick = if (pickerMode) {
                    { onApply(entry) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun CurrentHeaderCard(
    applied: AppliedHeader,
    reqWidth: Int,
    onOpenPack: (String) -> Unit,
) {
    val context = LocalContext.current
    val resources = remember(applied.packageName) {
        HeaderRepository.resourcesFor(context, applied.packageName)
    }
    val label = stringResource(R.string.header_current)
    HeaderCard(
        resources = resources,
        packageName = applied.packageName,
        entry = HeaderEntry(applied.image, label, hour = -1, day = -1, month = -1),
        selected = true,
        reqWidth = reqWidth,
        onClick = applied.packKey?.let { key -> { onOpenPack(key) } },
    )
}

@Composable
private fun HeaderCard(
    resources: Resources?,
    packageName: String,
    entry: HeaderEntry,
    selected: Boolean,
    reqWidth: Int,
    onClick: (() -> Unit)?,
) {
    val selectedLabel = stringResource(R.string.header_selected)
    val description = if (selected) "${entry.name ?: entry.image}, $selectedLabel" else {
        entry.name ?: entry.image
    }
    val shape = MaterialTheme.shapes.extraLarge
    val border = if (selected) {
        BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
    } else {
        null
    }
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    val modifier = Modifier
        .fillMaxWidth()
        .semantics {
            contentDescription = description
            this.selected = selected
        }
    val body: @Composable ColumnScope.() -> Unit = {
        HeaderArtwork(resources, packageName, entry.image, reqWidth, selected)
        val schedule = scheduleLabel(entry)
        if (entry.name != null || schedule != null) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                entry.name?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium)
                }
                schedule?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = colors,
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = body,
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = colors,
            border = border,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = body,
        )
    }
}

@Composable
private fun HeaderArtwork(
    resources: Resources?,
    packageName: String,
    image: String,
    reqWidth: Int,
    selected: Boolean,
) {
    val bitmap = rememberHeaderBitmap(resources, packageName, image, reqWidth)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (selected) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(32.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingBox(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.header_loading)
    Box(
        modifier.semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        ContainedLoadingIndicator()
    }
}

@Composable
private fun EmptyState(title: String, summary: String?, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (summary != null) {
            Text(
                summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun packSummary(pack: LoadedPack): String {
    val count = pluralStringResource(R.plurals.header_count, pack.headers.size, pack.headers.size)
    val creator = pack.creator ?: return count
    return stringResource(
        R.string.header_pack_summary,
        count,
        stringResource(R.string.header_by_creator, creator),
    )
}

@Composable
private fun scheduleLabel(entry: HeaderEntry): String? {
    val context = LocalContext.current
    return remember(entry) {
        when {
            entry.hour in 0..23 -> {
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, entry.hour)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                DateFormat.getTimeFormat(context).format(calendar.time)
            }
            entry.day in 1..31 && entry.month in 1..12 -> {
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.MONTH, entry.month - 1)
                    set(Calendar.DAY_OF_MONTH, entry.day)
                }
                val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd")
                SimpleDateFormat(pattern, Locale.getDefault()).format(calendar.time)
            }
            else -> null
        }
    }
}

@Composable
private fun thumbnailWidthPx(): Int {
    val configuration = LocalConfiguration.current
    val columns = (configuration.screenWidthDp / 320).coerceAtLeast(1)
    return with(LocalDensity.current) {
        (configuration.screenWidthDp.dp.roundToPx() / columns).coerceAtLeast(1)
    }
}

@Composable
private fun rememberHeaderBitmap(
    resources: Resources?,
    packageName: String,
    image: String,
    reqWidth: Int,
): Bitmap? {
    var bitmap by remember(packageName, image, reqWidth) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(resources, packageName, image, reqWidth) {
        bitmap = if (resources == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                HeaderRepository.loadThumbnail(resources, packageName, image, reqWidth)
            }
        }
    }
    return bitmap
}

private fun LoadedPack.contains(applied: AppliedHeader): Boolean {
    return pack.packageName == applied.packageName && headers.any { it.image == applied.image }
}
