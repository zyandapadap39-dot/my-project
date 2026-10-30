package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Phishing
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.billing.BillingRepository
import com.example.data.model.CatchRecord
import com.example.data.model.FishingSpot
import com.example.data.model.UiState
import com.example.data.preferences.UserPreferences
import com.example.ui.components.CatchListItemCard
import com.example.ui.components.PlanQuotaPill
import com.example.ui.components.PremiumFeatureBadge
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val WATER_TYPES = listOf("Lake", "River", "Stream", "Reservoir", "Coastal", "Pond")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FishingMapScreen(
    currentUserId: String,
    isGuestMode: Boolean = false,
    isPremium: Boolean = false,
    canCreateSpot: Boolean = true,
    spotsState: UiState<List<FishingSpot>>,
    catches: List<CatchRecord>,
    currentGps: Pair<Double, Double>?,
    preferences: UserPreferences,
    isSaving: Boolean,
    onRequestGpsUpdate: () -> Unit,
    onSaveSpot: (FishingSpot) -> Unit,
    onDeleteSpot: (FishingSpot) -> Unit,
    onLogCatchAtSpot: (FishingSpot) -> Unit,
    onSelectCatch: (CatchRecord) -> Unit,
    onOpenPremium: () -> Unit = {}
) {
    val context = LocalContext.current
    val allSpots = (spotsState as? UiState.Success)?.data.orEmpty()
    val mySpots = remember(allSpots, currentUserId) { allSpots.filter { it.userId == currentUserId } }

    var showCommunityShared by remember { mutableStateOf(false) }
    var proHeatmapEnabled by remember { mutableStateOf(false) }
    var proDetailedContoursEnabled by remember { mutableStateOf(false) }
    var proSolunarGridEnabled by remember { mutableStateOf(false) }

    val visibleSpots = remember(allSpots, mySpots, showCommunityShared, isGuestMode) {
        if (!isGuestMode && showCommunityShared) allSpots else mySpots
    }

    // Center coordinates of interactive map viewport
    var centerLat by remember(currentGps, mySpots.firstOrNull()?.id) {
        mutableDoubleStateOf(
            currentGps?.first ?: mySpots.firstOrNull()?.latitude ?: 44.4280
        )
    }
    var centerLon by remember(currentGps, mySpots.firstOrNull()?.id) {
        mutableDoubleStateOf(
            currentGps?.second ?: mySpots.firstOrNull()?.longitude ?: -110.5885
        )
    }

    // Scale in pixels per degree (zoomable)
    var zoomScale by remember { mutableFloatStateOf(2400f) }

    var selectedSpot by remember { mutableStateOf<FishingSpot?>(null) }
    var editingSpot by remember { mutableStateOf<FishingSpot?>(null) }
    var draftCoordsForNewSpot by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    val gpsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            onRequestGpsUpdate()
        }
    }

    // Spot editor dialog / sheet
    if (draftCoordsForNewSpot != null || editingSpot != null) {
        SpotEditorSheet(
            existingSpot = editingSpot,
            initialLat = editingSpot?.latitude ?: draftCoordsForNewSpot?.first ?: centerLat,
            initialLon = editingSpot?.longitude ?: draftCoordsForNewSpot?.second ?: centerLon,
            defaultPrivate = preferences.defaultPrivateSpots,
            isGuestMode = isGuestMode,
            isSaving = isSaving,
            onDismiss = {
                draftCoordsForNewSpot = null
                editingSpot = null
            },
            onSave = { spot ->
                onSaveSpot(spot)
                draftCoordsForNewSpot = null
                editingSpot = null
                centerLat = spot.latitude
                centerLon = spot.longitude
            }
        )
    }

    // Selected Spot Detail Sheet with associated catches
    if (selectedSpot != null) {
        val spot = selectedSpot!!
        val isOwner = spot.userId == currentUserId
        val spotCatches = catches.filter {
            it.spotId == spot.id || it.locationName.equals(spot.name, ignoreCase = true)
        }
        ModalBottomSheet(
            onDismissRequest = { selectedSpot = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SpotDetailBottomSheetContent(
                spot = spot,
                isOwner = isOwner,
                associatedCatches = spotCatches,
                preferences = preferences,
                onEdit = {
                    selectedSpot = null
                    editingSpot = spot
                },
                onDelete = {
                    onDeleteSpot(spot)
                    selectedSpot = null
                },
                onLogCatchHere = {
                    val target = spot
                    selectedSpot = null
                    onLogCatchAtSpot(target)
                },
                onSelectCatch = { catchItem ->
                    selectedSpot = null
                    onSelectCatch(catchItem)
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 840.dp)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Fishing Map",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Tap anywhere on the chart to pin a fishing spot. Private spots stay hidden unless shared.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            PlanQuotaPill(
                usedCount = mySpots.size,
                freeLimit = BillingRepository.FREE_MAX_SPOTS,
                isPremium = isPremium,
                unitLabel = "Spots",
                onClickUpgrade = onOpenPremium
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Filter row: My Private & Saved Spots vs Shared Spots
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showCommunityShared || isGuestMode,
                    onClick = { showCommunityShared = false },
                    leadingIcon = {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text("My Spots (${mySpots.size})") }
                )
                if (!isGuestMode) {
                    FilterChip(
                        selected = showCommunityShared,
                        onClick = { showCommunityShared = true },
                        leadingIcon = {
                            Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Include Shared") }
                    )
                }
            }

            Button(
                onClick = {
                    if (canCreateSpot) {
                        draftCoordsForNewSpot = Pair(centerLat, centerLon)
                    } else {
                        onOpenPremium()
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("pin_spot_button")
            ) {
                Icon(Icons.Filled.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pin Spot")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Advanced Fishing Maps Layers (Premium-locked with Lock Icon + Badge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                FilterChip(
                    selected = isPremium && proHeatmapEnabled,
                    onClick = {
                        if (isPremium) proHeatmapEnabled = !proHeatmapEnabled else onOpenPremium()
                    },
                    leadingIcon = if (!isPremium) {
                        { Icon(Icons.Filled.Lock, contentDescription = "Locked", modifier = Modifier.size(13.dp)) }
                    } else null,
                    label = { Text("Heatmap", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("map_layer_heatmap")
                )
                FilterChip(
                    selected = isPremium && proDetailedContoursEnabled,
                    onClick = {
                        if (isPremium) proDetailedContoursEnabled = !proDetailedContoursEnabled else onOpenPremium()
                    },
                    leadingIcon = if (!isPremium) {
                        { Icon(Icons.Filled.Lock, contentDescription = "Locked", modifier = Modifier.size(13.dp)) }
                    } else null,
                    label = { Text("Depth Pro", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("map_layer_depth_pro")
                )
                FilterChip(
                    selected = isPremium && proSolunarGridEnabled,
                    onClick = {
                        if (isPremium) proSolunarGridEnabled = !proSolunarGridEnabled else onOpenPremium()
                    },
                    leadingIcon = if (!isPremium) {
                        { Icon(Icons.Filled.Lock, contentDescription = "Locked", modifier = Modifier.size(13.dp)) }
                    } else null,
                    label = { Text("Solunar", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("map_layer_solunar")
                )
            }
            if (!isPremium) {
                PremiumFeatureBadge(
                    isLocked = true,
                    label = "PRO MAPS",
                    onClick = onOpenPremium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Interactive Topographic & Bathymetric Fishing Chart Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp)
                .clip(MaterialTheme.shapes.large)
                .background(Color(0xFF081B2B))
                .testTag("interactive_fishing_map_canvas")
        ) {
            val primaryPinColor = MaterialTheme.colorScheme.primary
            val trophyPinColor = MaterialTheme.colorScheme.secondary
            val sharedPinColor = Color(0xFF48CAE4)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(visibleSpots, zoomScale, centerLat, centerLon) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(250f, 35000f)
                            val cosLat = cos(Math.toRadians(centerLat)).coerceAtLeast(0.2)
                            centerLon -= (pan.x / (zoomScale * cosLat))
                            centerLat = (centerLat + (pan.y / zoomScale)).coerceIn(-85.0, 85.0)
                        }
                    }
                    .pointerInput(visibleSpots, zoomScale, centerLat, centerLon, canCreateSpot) {
                        detectTapGestures { tapOffset ->
                            val w = size.width
                            val h = size.height
                            val cosLat = cos(Math.toRadians(centerLat)).coerceAtLeast(0.2)

                            // Check if user tapped an existing pin within 34dp hit radius
                            val hitRadiusPx = 34.dp.toPx()
                            val tappedPin = visibleSpots.firstOrNull { spot ->
                                val px = (w / 2f) + ((spot.longitude - centerLon) * cosLat * zoomScale).toFloat()
                                val py = (h / 2f) - ((spot.latitude - centerLat) * zoomScale).toFloat()
                                hypot(tapOffset.x - px, tapOffset.y - py) <= hitRadiusPx
                            }

                            if (tappedPin != null) {
                                selectedSpot = tappedPin
                            } else if (canCreateSpot) {
                                val tappedLon = centerLon + ((tapOffset.x - w / 2f) / (zoomScale * cosLat))
                                val tappedLat = (centerLat - ((tapOffset.y - h / 2f) / zoomScale)).coerceIn(-85.0, 85.0)
                                draftCoordsForNewSpot = Pair(tappedLat, tappedLon)
                            } else {
                                onOpenPremium()
                            }
                        }
                    }
            ) {
                val w = size.width
                val h = size.height

                // Deep water bathymetric gradient
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF10324E), Color(0xFF0A2136), Color(0xFF061523)),
                        center = Offset(w * 0.5f, h * 0.5f),
                        radius = maxOf(w, h) * 0.75f
                    )
                )

                // Latitude / Longitude graticule grid lines
                val gridStepPx = 56.dp.toPx()
                val offsetX = (((-centerLon * zoomScale) % gridStepPx).toFloat() + gridStepPx) % gridStepPx
                val offsetY = (((centerLat * zoomScale) % gridStepPx).toFloat() + gridStepPx) % gridStepPx

                var gx = offsetX
                while (gx < w) {
                    drawLine(
                        color = Color(0x226BA8D1),
                        start = Offset(gx, 0f),
                        end = Offset(gx, h),
                        strokeWidth = 1f
                    )
                    gx += gridStepPx
                }

                var gy = offsetY
                while (gy < h) {
                    drawLine(
                        color = Color(0x226BA8D1),
                        start = Offset(0f, gy),
                        end = Offset(w, gy),
                        strokeWidth = 1f
                    )
                    gy += gridStepPx
                }

                // Stylized bathymetric depth contour rings around center (expanded in Depth Pro mode)
                val ringCount = if (isPremium && proDetailedContoursEnabled) 8 else 4
                val ringSpacing = if (isPremium && proDetailedContoursEnabled) 24.dp.toPx() else 44.dp.toPx()
                for (ring in 1..ringCount) {
                    val path = Path()
                    val baseR = ring * ringSpacing
                    val steps = 36
                    for (i in 0..steps) {
                        val angle = (i.toDouble() / steps) * Math.PI * 2.0
                        val wave = sin(angle * 3.0 + ring) * 10.dp.toPx()
                        val rx = w / 2f + ((baseR + wave) * cos(angle)).toFloat()
                        val ry = h / 2f + ((baseR * 0.72f + wave) * sin(angle)).toFloat()
                        if (i == 0) path.moveTo(rx, ry) else path.lineTo(rx, ry)
                    }
                    path.close()
                    drawPath(
                        path = path,
                        color = if (isPremium && proDetailedContoursEnabled) Color(0x442EE585) else Color(0x1942B883),
                        style = Stroke(width = if (isPremium && proDetailedContoursEnabled) 2f else 1.5f)
                    )
                }

                // Solunar directional bearing rays when unlocked & active
                if (isPremium && proSolunarGridEnabled) {
                    for (deg in 0 until 360 step 45) {
                        val rad = Math.toRadians(deg.toDouble())
                        val endX = w / 2f + (maxOf(w, h) * cos(rad)).toFloat()
                        val endY = h / 2f + (maxOf(w, h) * sin(rad)).toFloat()
                        drawLine(
                            color = Color(0x33FFCA45),
                            start = Offset(w / 2f, h / 2f),
                            end = Offset(endX, endY),
                            strokeWidth = 1.4f
                        )
                    }
                }

                val cosLat = cos(Math.toRadians(centerLat)).coerceAtLeast(0.2)

                // Catch Heatmap thermal halos (Premium layer)
                if (isPremium && proHeatmapEnabled) {
                    visibleSpots.forEach { spot ->
                        val px = (w / 2f) + ((spot.longitude - centerLon) * cosLat * zoomScale).toFloat()
                        val py = (h / 2f) - ((spot.latitude - centerLat) * zoomScale).toFloat()
                        if (px in -80f..(w + 80f) && py in -80f..(h + 80f)) {
                            val spotCatchDensity = catches.count {
                                it.spotId == spot.id || it.locationName.equals(spot.name, ignoreCase = true)
                            }.coerceAtLeast(1)
                            val heatRadius = (36.dp.toPx() + spotCatchDensity * 10.dp.toPx()).coerceAtMost(96.dp.toPx())
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0x6600E676),
                                        Color(0x33FFCA45),
                                        Color.Transparent
                                    ),
                                    center = Offset(px, py),
                                    radius = heatRadius
                                ),
                                radius = heatRadius,
                                center = Offset(px, py)
                            )
                        }
                    }
                }

                // Draw current GPS blue pulse if available
                currentGps?.let { (gpsLat, gpsLon) ->
                    val gxPos = (w / 2f) + ((gpsLon - centerLon) * cosLat * zoomScale).toFloat()
                    val gyPos = (h / 2f) - ((gpsLat - centerLat) * zoomScale).toFloat()
                    if (gxPos in -40f..(w + 40f) && gyPos in -40f..(h + 40f)) {
                        drawCircle(
                            color = Color(0x4438BDF8),
                            radius = 18.dp.toPx(),
                            center = Offset(gxPos, gyPos)
                        )
                        drawCircle(
                            color = Color(0xFF38BDF8),
                            radius = 6.dp.toPx(),
                            center = Offset(gxPos, gyPos)
                        )
                    }
                }

                // Draw Fishing Spot Pins
                visibleSpots.forEach { spot ->
                    val px = (w / 2f) + ((spot.longitude - centerLon) * cosLat * zoomScale).toFloat()
                    val py = (h / 2f) - ((spot.latitude - centerLat) * zoomScale).toFloat()
                    if (px in -30f..(w + 30f) && py in -30f..(h + 30f)) {
                        val pinColor = when {
                            spot.userId != currentUserId -> sharedPinColor
                            spot.isSuccessful -> trophyPinColor
                            else -> primaryPinColor
                        }
                        // Outer halo
                        drawCircle(
                            color = pinColor.copy(alpha = 0.28f),
                            radius = 18.dp.toPx(),
                            center = Offset(px, py)
                        )
                        // Solid pin circle
                        drawCircle(
                            color = pinColor,
                            radius = 9.dp.toPx(),
                            center = Offset(px, py)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3.5.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }

                // Center reticle crosshair
                val reticleLen = 8.dp.toPx()
                drawLine(
                    color = Color.White.copy(alpha = 0.45f),
                    start = Offset(w / 2f - reticleLen, h / 2f),
                    end = Offset(w / 2f + reticleLen, h / 2f),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.45f),
                    start = Offset(w / 2f, h / 2f - reticleLen),
                    end = Offset(w / 2f, h / 2f + reticleLen),
                    strokeWidth = 1.5f
                )
            }

            // Coordinate HUD Badge
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xAA06131F),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "LAT %.4f° • LON %.4f°", centerLat, centerLon),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFD6E6F2),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            // Zoom & GPS Controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            onRequestGpsUpdate()
                            currentGps?.let {
                                centerLat = it.first
                                centerLon = it.second
                            }
                        } else {
                            gpsLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC102234))
                ) {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = "Center on my GPS location",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = { zoomScale = (zoomScale * 1.6f).coerceAtMost(35000f) },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC102234))
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Zoom In", tint = Color.White)
                }

                IconButton(
                    onClick = { zoomScale = (zoomScale / 1.6f).coerceAtLeast(250f) },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC102234))
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Zoom Out", tint = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Saved Locations List
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Saved Fishing Locations (${visibleSpots.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (spotsState is UiState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (visibleSpots.isEmpty()) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "No fishing spots pinned yet. Tap anywhere on the interactive chart above or press \"Pin Spot\" to save your favorite fishing waters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visibleSpots, key = { it.id }) { spot ->
                    val catchCountAtSpot = catches.count {
                        it.spotId == spot.id || it.locationName.equals(spot.name, ignoreCase = true)
                    }
                    SavedSpotRowCard(
                        spot = spot,
                        isOwner = spot.userId == currentUserId,
                        catchCount = catchCountAtSpot,
                        onFocusOnMap = {
                            centerLat = spot.latitude
                            centerLon = spot.longitude
                        },
                        onClick = { selectedSpot = spot }
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedSpotRowCard(
    spot: FishingSpot,
    isOwner: Boolean,
    catchCount: Int,
    onFocusOnMap: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("spot_card_${spot.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onFocusOnMap,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CenterFocusStrong,
                    contentDescription = "Center map on ${spot.name}",
                    tint = if (spot.isSuccessful) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = spot.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (spot.isSuccessful) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Productive spot",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = "${spot.waterBodyType} • ${spot.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (!spot.isShared) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ) {
                        Text(
                            text = if (!spot.isShared) "Private" else if (isOwner) "Shared by You" else "Shared",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    if (isOwner) {
                        Text(
                            text = "$catchCount catches logged",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotDetailBottomSheetContent(
    spot: FishingSpot,
    isOwner: Boolean,
    associatedCatches: List<CatchRecord>,
    preferences: UserPreferences,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLogCatchHere: () -> Unit,
    onSelectCatch: (CatchRecord) -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Fishing Spot?") },
            text = { Text("Remove \"${spot.name}\" from your saved fishing locations?") },
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
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(spot.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = String.format(
                        Locale.US,
                        "%s • %.4f°, %.4f°",
                        spot.waterBodyType,
                        spot.latitude,
                        spot.longitude
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isOwner) {
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Spot")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete Spot",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (spot.isShared) Icons.Filled.Public else Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (spot.isShared) "Publicly Shared Spot" else "Strictly Private Spot",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            if (spot.isSuccessful) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "Productive Hotspot",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Text(
            text = spot.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (!spot.targetSpecies.isNullOrBlank()) {
            Text(
                text = "Target Species: ${spot.targetSpecies}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Button(
            onClick = onLogCatchHere,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Phishing, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Log a Catch at ${spot.name}")
        }

        if (isOwner) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Catches Associated With This Location (${associatedCatches.size})",
                style = MaterialTheme.typography.titleMedium
            )
            if (associatedCatches.isEmpty()) {
                Text(
                    text = "No catches linked to this spot yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                associatedCatches.take(5).forEach { catchRecord ->
                    CatchListItemCard(
                        catchRecord = catchRecord,
                        weightUnit = preferences.weightUnit,
                        lengthUnit = preferences.lengthUnit,
                        onClick = { onSelectCatch(catchRecord) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SpotEditorSheet(
    existingSpot: FishingSpot?,
    initialLat: Double,
    initialLon: Double,
    defaultPrivate: Boolean,
    isGuestMode: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (FishingSpot) -> Unit
) {
    var name by remember { mutableStateOf(existingSpot?.name ?: "") }
    var description by remember { mutableStateOf(existingSpot?.description ?: "") }
    var waterBodyType by remember { mutableStateOf(existingSpot?.waterBodyType ?: "Lake") }
    var latInput by remember {
        mutableStateOf(String.format(Locale.US, "%.5f", initialLat))
    }
    var lonInput by remember {
        mutableStateOf(String.format(Locale.US, "%.5f", initialLon))
    }
    var targetSpecies by remember { mutableStateOf(existingSpot?.targetSpecies ?: "") }
    var isSuccessful by remember { mutableStateOf(existingSpot?.isSuccessful ?: true) }
    var isShared by remember {
        mutableStateOf(if (isGuestMode) false else (existingSpot?.isShared ?: !defaultPrivate))
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (existingSpot == null) "Pin Fishing Location" else "Edit Fishing Location",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Location Name *") },
                placeholder = { Text("e.g., Submerged Willow Point") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("spot_name_input")
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                WATER_TYPES.forEach { wt ->
                    FilterChip(
                        selected = waterBodyType == wt,
                        onClick = { waterBodyType = wt },
                        label = { Text(wt) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = latInput,
                    onValueChange = { latInput = it },
                    label = { Text("Latitude *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = lonInput,
                    onValueChange = { lonInput = it },
                    label = { Text("Longitude *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = targetSpecies,
                onValueChange = { targetSpecies = it },
                label = { Text("Target Species") },
                placeholder = { Text("e.g., Smallmouth Bass, Walleye") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Location Description & Structure *") },
                placeholder = { Text("Drop-off depth, weedline edge, shore access notes...") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("spot_description_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pin as Successful Hotspot", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Highlight with gold trophy marker on map",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = isSuccessful, onCheckedChange = { isSuccessful = it })
            }

            if (!isGuestMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Explicitly Share with Community", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (isShared) {
                                "Visible to other FishLog anglers"
                            } else {
                                "Strictly private — visible ONLY to your account"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isShared, onCheckedChange = { isShared = it })
                }
            } else {
                Text(
                    text = "Saved locally on this device in Guest Mode. Create an account anytime to back up or share spots.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (errorMsg != null) {
                Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val lat = latInput.trim().toDoubleOrNull()
                        val lon = lonInput.trim().toDoubleOrNull()
                        when {
                            name.isBlank() -> errorMsg = "Please enter a spot name."
                            description.isBlank() -> errorMsg = "Please enter a spot description."
                            lat == null || lat !in -90.0..90.0 -> errorMsg = "Latitude must be between -90 and 90."
                            lon == null || lon !in -180.0..180.0 -> errorMsg = "Longitude must be between -180 and 180."
                            else -> {
                                errorMsg = null
                                onSave(
                                    FishingSpot(
                                        id = existingSpot?.id ?: "",
                                        name = name.trim(),
                                        description = description.trim(),
                                        waterBodyType = waterBodyType,
                                        latitude = lat,
                                        longitude = lon,
                                        isShared = isShared,
                                        isSuccessful = isSuccessful,
                                        targetSpecies = targetSpecies.trim(),
                                        createdAt = existingSpot?.createdAt
                                    )
                                )
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_spot_button")
                ) {
                    Text(if (existingSpot == null) "Save Spot" else "Update Spot")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
