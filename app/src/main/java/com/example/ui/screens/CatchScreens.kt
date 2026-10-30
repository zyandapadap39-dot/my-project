package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phishing
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.billing.BillingRepository
import com.example.data.model.CatchRecord
import com.example.data.model.FishingSession
import com.example.data.model.FishingSpot
import com.example.data.model.UiState
import com.example.data.preferences.UserPreferences
import com.example.ui.components.CatchListItemCard
import com.example.ui.components.PlanQuotaPill
import com.example.ui.components.PremiumFeatureBadge
import com.example.util.ImageEncodingUtils
import com.example.util.UnitFormatter
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch

private val COMMON_METHODS = listOf(
    "Spinning", "Fly Fishing", "Baitcasting", "Trolling", "Jigging", "Bottom Fishing"
)

private val WATER_CONDITIONS = listOf(
    "Clear & Calm", "Slightly Stained", "Choppy Surface", "Fast Current", "Murky / High Water", "Cold Deep"
)

private val POPULAR_SPECIES = listOf(
    "Largemouth Bass", "Smallmouth Bass", "Rainbow Trout", "Brown Trout",
    "Northern Pike", "Walleye", "Chinook Salmon", "Redfish", "Striped Bass", "Channel Catfish"
)

@Composable
fun CatchListScreen(
    catchesState: UiState<List<CatchRecord>>,
    preferences: UserPreferences,
    isPremium: Boolean = false,
    onAddCatch: () -> Unit,
    onSelectCatch: (CatchRecord) -> Unit,
    onExportPdf: (Uri) -> Unit = {},
    onExportCsv: (Uri) -> Unit = {},
    onOpenPremium: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedMethodFilter by remember { mutableStateOf<String?>(null) }
    val totalCatches = (catchesState as? UiState.Success)?.data?.size ?: 0

    val pdfExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) onExportPdf(uri)
    }
    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) onExportCsv(uri)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Catch Logbook",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Every fish recorded with measurements, tackle, and water conditions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                PlanQuotaPill(
                    usedCount = totalCatches,
                    freeLimit = BillingRepository.FREE_MAX_CATCHES,
                    isPremium = isPremium,
                    unitLabel = "Catches",
                    onClickUpgrade = onOpenPremium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (isPremium) pdfExportLauncher.launch("FishLog_Angler_Report.pdf") else onOpenPremium()
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isPremium) Icons.Filled.PictureAsPdf else Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("PDF Report", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = {
                        if (isPremium) csvExportLauncher.launch("FishLog_Catches.csv") else onOpenPremium()
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isPremium) Icons.Filled.TableChart else Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("CSV Export", style = MaterialTheme.typography.labelMedium)
                }
                if (!isPremium) {
                    PremiumFeatureBadge(
                        isLocked = true,
                        label = "EXPORT PRO",
                        onClick = onOpenPremium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search species, location, or lure...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("catch_search_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.FilterList,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                FilterChip(
                    selected = selectedMethodFilter == null,
                    onClick = { selectedMethodFilter = null },
                    label = { Text("All Methods") }
                )
                COMMON_METHODS.forEach { method ->
                    FilterChip(
                        selected = selectedMethodFilter == method,
                        onClick = {
                            selectedMethodFilter = if (selectedMethodFilter == method) null else method
                        },
                        label = { Text(method) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (catchesState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Text(
                        text = catchesState.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                is UiState.Success -> {
                    val filtered = catchesState.data.filter { item ->
                        val matchesQuery = searchQuery.isBlank() ||
                            item.species.contains(searchQuery, ignoreCase = true) ||
                            item.locationName.contains(searchQuery, ignoreCase = true) ||
                            item.baitOrLure.contains(searchQuery, ignoreCase = true)
                        val matchesMethod = selectedMethodFilter == null ||
                            item.fishingMethod.equals(selectedMethodFilter, ignoreCase = true)
                        matchesQuery && matchesMethod
                    }

                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.Phishing,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (catchesState.data.isEmpty()) "No Catches Logged Yet" else "No Matching Catches",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap the + button below to record a fish.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(top = 6.dp, bottom = 104.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filtered, key = { it.id }) { catchItem ->
                                CatchListItemCard(
                                    catchRecord = catchItem,
                                    weightUnit = preferences.weightUnit,
                                    lengthUnit = preferences.lengthUnit,
                                    onClick = { onSelectCatch(catchItem) }
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddCatch,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
                .testTag("fab_add_catch")
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Log New Catch")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatchDetailScreen(
    record: CatchRecord,
    preferences: UserPreferences,
    linkedSessionTitle: String?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    BackHandler { onBack() }
    var confirmDelete by remember { mutableStateOf(false) }
    val bitmap = remember(record.photoBase64) {
        ImageEncodingUtils.decodeBase64ToImageBitmap(record.photoBase64)
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Catch Record?") },
            text = { Text("Are you sure you want to permanently remove this ${record.species} catch?") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(record.species, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("catch_detail_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit, modifier = Modifier.testTag("catch_edit_button")) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Catch")
                    }
                    IconButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.testTag("catch_delete_button")
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete Catch",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "${record.species} photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(MaterialTheme.shapes.large)
                    )
                }

                // Measurements Card
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = UnitFormatter.formatWeight(record.weightKg, preferences.weightUnit),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Weight",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = UnitFormatter.formatLength(record.lengthCm, preferences.lengthUnit),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Length",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (record.released) "Released" else "Kept",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Outcome",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Field Details Card
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DetailFieldRow("Date & Time", UnitFormatter.formatDateTime(record.caughtAtEpochMillis))
                        DetailFieldRow("Fishing Location", record.locationName)
                        DetailFieldRow("Bait / Lure Used", record.baitOrLure)
                        DetailFieldRow("Fishing Method", record.fishingMethod)
                        DetailFieldRow("Water Conditions", record.waterConditions)
                        if (!linkedSessionTitle.isNullOrBlank()) {
                            DetailFieldRow("Journal Session", linkedSessionTitle)
                        }
                        if (!record.notes.isNullOrBlank()) {
                            DetailFieldRow("Personal Notes", record.notes)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailFieldRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CatchEditorScreen(
    existingCatch: CatchRecord?,
    preselectedSessionId: String?,
    preselectedSpot: FishingSpot?,
    availableSpots: List<FishingSpot>,
    availableSessions: List<FishingSession>,
    preferences: UserPreferences,
    isSaving: Boolean,
    onBack: () -> Unit,
    onSave: (CatchRecord, String?) -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var species by remember { mutableStateOf(existingCatch?.species ?: "") }
    var weightInput by remember {
        mutableStateOf(
            existingCatch?.let {
                String.format(Locale.US, "%.2f", UnitFormatter.displayWeightValue(it.weightKg, preferences.weightUnit))
            } ?: ""
        )
    }
    var lengthInput by remember {
        mutableStateOf(
            existingCatch?.let {
                String.format(Locale.US, "%.1f", UnitFormatter.displayLengthValue(it.lengthCm, preferences.lengthUnit))
            } ?: ""
        )
    }
    var caughtAtEpoch by remember {
        mutableLongStateOf(existingCatch?.caughtAtEpochMillis ?: System.currentTimeMillis())
    }
    var locationName by remember {
        mutableStateOf(existingCatch?.locationName ?: preselectedSpot?.name ?: "")
    }
    var selectedSpotId by remember {
        mutableStateOf(existingCatch?.spotId ?: preselectedSpot?.id)
    }
    var selectedSessionId by remember {
        mutableStateOf(existingCatch?.sessionId ?: preselectedSessionId)
    }
    var baitOrLure by remember { mutableStateOf(existingCatch?.baitOrLure ?: "") }
    var fishingMethod by remember { mutableStateOf(existingCatch?.fishingMethod ?: COMMON_METHODS.first()) }
    var waterConditions by remember { mutableStateOf(existingCatch?.waterConditions ?: WATER_CONDITIONS.first()) }
    var photoBase64 by remember { mutableStateOf(existingCatch?.photoBase64) }
    var notes by remember { mutableStateOf(existingCatch?.notes ?: "") }
    var released by remember { mutableStateOf(existingCatch?.released ?: true) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = ImageEncodingUtils.encodeUriToBase64Thumbnail(context, uri)
                if (encoded != null) {
                    photoBase64 = encoded
                }
            }
        }
    }

    val previewBitmap = remember(photoBase64) {
        ImageEncodingUtils.decodeBase64ToImageBitmap(photoBase64)
    }

    fun pickDateTime() {
        val cal = Calendar.getInstance().apply { timeInMillis = caughtAtEpoch }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val updated = Calendar.getInstance().apply {
                            set(year, month, day, hour, minute, 0)
                        }
                        caughtAtEpoch = updated.timeInMillis
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    false
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingCatch == null) "Log New Catch" else "Edit Catch") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("catch_editor_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Photo Upload Box (Zero-permission Android Photo Picker)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(185.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .testTag("catch_photo_picker_box"),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap,
                            contentDescription = "Selected catch photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.AddAPhoto,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Add Catch Photo (Optional)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap to choose from gallery",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Species Input + Quick Chips
                OutlinedTextField(
                    value = species,
                    onValueChange = { species = it },
                    label = { Text("Fish Species *") },
                    placeholder = { Text("e.g., Largemouth Bass, Rainbow Trout") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("catch_species_input")
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    POPULAR_SPECIES.take(6).forEach { sp ->
                        FilterChip(
                            selected = species.equals(sp, ignoreCase = true),
                            onClick = { species = sp },
                            label = { Text(sp, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Weight & Length
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("Weight (${preferences.weightUnit.symbol}) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("catch_weight_input")
                    )

                    OutlinedTextField(
                        value = lengthInput,
                        onValueChange = { lengthInput = it },
                        label = { Text("Length (${preferences.lengthUnit.symbol}) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("catch_length_input")
                    )
                }

                // Date & Time Picker Button
                OutlinedButton(
                    onClick = { pickDateTime() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("catch_datetime_button")
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Caught: ${UnitFormatter.formatDateTime(caughtAtEpoch)}")
                }

                // Fishing Location + Saved Spot Selector
                OutlinedTextField(
                    value = locationName,
                    onValueChange = { locationName = it },
                    label = { Text("Fishing Location *") },
                    placeholder = { Text("e.g., Mirror Lake North Point") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("catch_location_input")
                )

                if (availableSpots.isNotEmpty()) {
                    var spotMenuExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = spotMenuExpanded,
                        onExpandedChange = { spotMenuExpanded = it }
                    ) {
                        val linkedSpotName = availableSpots.firstOrNull { it.id == selectedSpotId }?.name
                            ?: "Link to Saved Map Spot (Optional)"
                        OutlinedTextField(
                            value = linkedSpotName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Saved Fishing Spot") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = spotMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = spotMenuExpanded,
                            onDismissRequest = { spotMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = {
                                    selectedSpotId = null
                                    spotMenuExpanded = false
                                }
                            )
                            availableSpots.forEach { spot ->
                                DropdownMenuItem(
                                    text = { Text(spot.name) },
                                    onClick = {
                                        selectedSpotId = spot.id
                                        locationName = spot.name
                                        spotMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Link to Fishing Journal Session
                if (availableSessions.isNotEmpty()) {
                    var sessionExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = sessionExpanded,
                        onExpandedChange = { sessionExpanded = it }
                    ) {
                        val linkedSessionLabel = availableSessions.firstOrNull { it.id == selectedSessionId }?.title
                            ?: "None (Standalone Catch)"
                        OutlinedTextField(
                            value = linkedSessionLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Fishing Journal Session") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sessionExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = sessionExpanded,
                            onDismissRequest = { sessionExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None (Standalone Catch)") },
                                onClick = {
                                    selectedSessionId = null
                                    sessionExpanded = false
                                }
                            )
                            availableSessions.forEach { session ->
                                DropdownMenuItem(
                                    text = { Text("${session.title} (${UnitFormatter.formatShortDate(session.startedAtEpochMillis)})") },
                                    onClick = {
                                        selectedSessionId = session.id
                                        if (locationName.isBlank()) locationName = session.locationName
                                        sessionExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Bait or Lure
                OutlinedTextField(
                    value = baitOrLure,
                    onValueChange = { baitOrLure = it },
                    label = { Text("Bait or Lure Used *") },
                    placeholder = { Text("e.g., Olive Woolly Bugger, Green Pumpkin Senko") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("catch_bait_input")
                )

                // Fishing Method Chips
                Text(
                    text = "Fishing Method *",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    COMMON_METHODS.forEach { method ->
                        FilterChip(
                            selected = fishingMethod == method,
                            onClick = { fishingMethod = method },
                            label = { Text(method) }
                        )
                    }
                }

                // Water Conditions
                OutlinedTextField(
                    value = waterConditions,
                    onValueChange = { waterConditions = it },
                    label = { Text("Water Conditions *") },
                    placeholder = { Text("e.g., Clear, 58°F, slight ripple") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("catch_water_input")
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    WATER_CONDITIONS.forEach { cond ->
                        FilterChip(
                            selected = waterConditions == cond,
                            onClick = { waterConditions = cond },
                            label = { Text(cond, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Catch & Release Toggle
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Catch & Release", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Fish was returned unharmed to the water",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = released,
                            onCheckedChange = { released = it }
                        )
                    }
                }

                // Personal Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Personal Notes") },
                    placeholder = { Text("Strike depth, retrieve cadence, fight details...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("catch_notes_input")
                )

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = {
                        val parsedWeight = weightInput.trim().toDoubleOrNull()
                        val parsedLength = lengthInput.trim().toDoubleOrNull()
                        when {
                            species.isBlank() -> validationError = "Please enter the fish species."
                            parsedWeight == null || parsedWeight <= 0.0 ->
                                validationError = "Please enter a valid positive weight."
                            parsedLength == null || parsedLength <= 0.0 ->
                                validationError = "Please enter a valid positive length."
                            locationName.isBlank() ->
                                validationError = "Please enter the fishing location."
                            baitOrLure.isBlank() ->
                                validationError = "Please enter the bait or lure used."
                            fishingMethod.isBlank() ->
                                validationError = "Please select a fishing method."
                            waterConditions.isBlank() ->
                                validationError = "Please enter water conditions."
                            else -> {
                                validationError = null
                                val normalizedKg = UnitFormatter.inputWeightToKg(parsedWeight, preferences.weightUnit)
                                val normalizedCm = UnitFormatter.inputLengthToCm(parsedLength, preferences.lengthUnit)
                                if (normalizedKg > 1500.0 || normalizedCm > 1000.0) {
                                    validationError = "Entered weight or length exceeds realistic biological bounds."
                                    return@Button
                                }
                                val linkedSpot = availableSpots.firstOrNull { it.id == selectedSpotId }
                                val updatedRecord = CatchRecord(
                                    id = existingCatch?.id ?: "",
                                    species = species.trim(),
                                    weightKg = normalizedKg,
                                    lengthCm = normalizedCm,
                                    caughtAtEpochMillis = caughtAtEpoch,
                                    locationName = locationName.trim(),
                                    spotId = selectedSpotId,
                                    sessionId = selectedSessionId,
                                    latitude = linkedSpot?.latitude ?: existingCatch?.latitude,
                                    longitude = linkedSpot?.longitude ?: existingCatch?.longitude,
                                    baitOrLure = baitOrLure.trim(),
                                    fishingMethod = fishingMethod.trim(),
                                    waterConditions = waterConditions.trim(),
                                    photoBase64 = photoBase64,
                                    notes = notes.trim(),
                                    released = released,
                                    createdAt = existingCatch?.createdAt
                                )
                                onSave(updatedRecord, existingCatch?.sessionId)
                            }
                        }
                    },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_catch_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (existingCatch == null) "Save Catch to Logbook" else "Update Catch Record",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
