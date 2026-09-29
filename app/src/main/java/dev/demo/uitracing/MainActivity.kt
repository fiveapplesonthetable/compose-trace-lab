package dev.demo.uitracing

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeOptionalTracing()
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF6750A4))) { TraceLab() } }
    }

    // Reflection keeps the demo buildable without the CL-specific AARs. When present,
    // call the SDK entry points described by the guide; errors are visible in logcat.
    private fun initializeOptionalTracing() {
        try {
            val source = Class.forName("dev.perfetto.sdk.PerfettoDataSource")
            val backend = source.getField("BACKEND_SYSTEM").getInt(null)
            source.getMethod("initialize", Int::class.javaPrimitiveType).invoke(null, backend)
            val hierarchy = Class.forName("androidx.compose.ui.tracing.perfetto.UiHierarchyTracing")
            val install = hierarchy.methods.firstOrNull {
                (it.name == "install" || it.name == "init") &&
                    it.parameterTypes.contentEquals(arrayOf(android.content.Context::class.java))
            } ?: error("UiHierarchyTracing.install(Context) API not found")
            install.invoke(null, applicationContext)
            Log.i("TraceLab", "Perfetto UI hierarchy tracing initialized")
        } catch (e: ClassNotFoundException) {
            Log.i("TraceLab", "Optional tracing class ${e.message} not found; add both AARs under app/libs. UI demo remains usable")
        } catch (e: Exception) {
            Log.e("TraceLab", "Unable to initialize optional tracing", e)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TraceLab() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var counter by remember { mutableIntStateOf(0) }
    var checked by remember { mutableStateOf(true) }
    var slider by remember { mutableFloatStateOf(0.35f) }
    var showDetails by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val rowLabels = remember { (1..100).map { "Composable row $it" } }
    val screens = listOf("Home", "Gallery", "Feed", "Grid", "Forms", "Motion", "Flow")

    Scaffold(
        topBar = {
            Column(Modifier.fillMaxWidth().background(Color(0xFFF7F4FA)).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Compose Trace Lab", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Compose · Flow · system tracing", color = Color.Gray, fontSize = 12.sp)
                    }
                    Text("LAB", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                PrimaryScrollableTabRow(selectedTabIndex = selectedTab) {
                    screens.forEachIndexed { index, label ->
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label) })
                    }
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> Overview(Modifier.padding(padding), counter, { counter++ }, checked, { checked = it }, slider, { slider = it }, showDetails, { showDetails = it }, { showDialog = true })
            1 -> Components(Modifier.padding(padding), query, { query = it }, { showDialog = true })
            2 -> LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("feed-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Scrolling content", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                items(rowLabels) { label -> Surface(Modifier.fillMaxWidth().clickable { counter++ }, shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp) { Text("$label · tap to recompose", Modifier.padding(18.dp)) } }
            }
            3 -> LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().padding(padding).testTag("tile-grid"), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) { Text("Two-column lazy grid", Modifier.padding(8.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                gridItems((1..60).toList()) { n -> Surface(Modifier.fillMaxWidth().height(110.dp).clickable { counter++ }, shape = RoundedCornerShape(14.dp), color = if (n % 2 == 0) Color(0xFFEDE7F6) else Color(0xFFE1F2F0)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) { Text("Tile $n", fontWeight = FontWeight.Bold); Text("Tap · counter $counter", fontSize = 12.sp) } } }
            }
            4 -> FormsScreen(Modifier.padding(padding))
            5 -> MotionScreen(Modifier.padding(padding), slider) { slider = it }
            else -> FlowScreen(Modifier.padding(padding))
        }
        if (showDialog) AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Hierarchy dialog") },
            text = { Text("Dialog and overlay nodes are included in the capture workload.") },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("Close") } },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun FlowScreen(modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val count = remember { MutableStateFlow(0) }
    val countValue by count.asStateFlow().collectAsStateWithLifecycle()
    val events = remember { MutableSharedFlow<String>(extraBufferCapacity = 8) }
    var latestEvent by remember { mutableStateOf("No one-off events yet") }
    var streamProgress by remember { mutableIntStateOf(0) }
    LaunchedEffect(events) { events.collect { latestEvent = it } }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Coroutines and Flow", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("StateFlow is state: a new collector immediately receives the latest value.")
        Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("StateFlow count: $countValue", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { count.update { it + 1 } }, modifier = Modifier.testTag("flow-increment")) { Text("Update state") }
                    OutlinedButton(onClick = { scope.launch { count.update { it + 1 } } }) { Text("Update in coroutine") }
                }
            }
        }
        Text("SharedFlow event: $latestEvent", Modifier.testTag("flow-event"))
        OutlinedButton(onClick = { events.tryEmit("Button tapped at ${System.currentTimeMillis() % 100000}") }) { Text("Emit transient event") }
        Text("Cold flow stream: $streamProgress / 5")
        Button(onClick = {
            scope.launch {
                flow { repeat(5) { emit(it + 1); delay(250) } }
                    .flowOn(Dispatchers.Default)
                    .collect { streamProgress = it }
            }
        }) { Text("Start cold flow") }
        Text("Try: switch tabs while the stream runs, then return. This screen leaves composition, so its local state resets. Hoist state to a ViewModel to keep it. A cold flow starts again when collected.", fontSize = 13.sp, color = Color.DarkGray)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FormsScreen(modifier: Modifier) {
    var name by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var choice by remember { mutableStateOf("Standard") }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Form controls", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().testTag("name-field"), label = { Text("Name") }, supportingText = { Text("Typing drives text and state updates") })
        Text("Preview: ${name.ifBlank { "Your name" }}")
        ExposedDropdownMenuBox(expanded, { expanded = it }) {
            OutlinedTextField(choice, {}, Modifier.menuAnchor().fillMaxWidth().testTag("choice-field"), readOnly = true, label = { Text("Layout preset") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
            ExposedDropdownMenu(expanded, { expanded = false }) { listOf("Standard", "Compact", "Expanded").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { choice = it; expanded = false }) } }
        }
        var selected by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected, { selected = !selected }, modifier = Modifier.semantics { contentDescription = "Radio selection"; stateDescription = if (selected) "Selected" else "Not selected" }); Text("Radio selection") }
        Row(verticalAlignment = Alignment.CenterVertically) { var enabled by remember { mutableStateOf(true) }; Text("Enabled", Modifier.weight(1f)); Switch(enabled, { enabled = it }, modifier = Modifier.semantics { contentDescription = "Enabled"; stateDescription = if (enabled) "On" else "Off" }) }
        Button(onClick = { name = "Submitted: ${name.ifBlank { "Compose" }}" }, modifier = Modifier.testTag("submit-form")) { Text("Submit form") }
    }
}

@Composable
private fun MotionScreen(modifier: Modifier, slider: Float, setSlider: (Float) -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Motion and graphics", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Slider drives Canvas line and animated color changes.")
        Slider(slider, setSlider, modifier.semantics { contentDescription = "Motion chart value"; stateDescription = "${(slider * 100).toInt()} percent" }.testTag("motion-slider"))
        MiniChart(slider)
        var reveal by remember { mutableStateOf(true) }
        OutlinedButton(onClick = { reveal = !reveal }) { Text("Toggle animated card") }
        AnimatedVisibility(reveal) { Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFEDE7F6)) { Column(Modifier.fillMaxWidth().padding(20.dp)) { Text("Animated content", fontWeight = FontWeight.Bold); Text("Enter/exit transition nodes") } } }
        MiniChart(1f - slider)
    }
}

@Composable
private fun Overview(
    modifier: Modifier, counter: Int, increment: () -> Unit, checked: Boolean, setChecked: (Boolean) -> Unit,
    slider: Float, setSlider: (Float) -> Unit, showDetails: Boolean, setDetails: (Boolean) -> Unit, showDialog: () -> Unit
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("overview-scroll"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFEDE7F6)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Recomposition playground", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Tap controls and watch the hierarchy change.", color = Color.DarkGray)
                }
                Text("$counter", Modifier.semantics { contentDescription = "Counter value $counter" }, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = increment, modifier = Modifier.weight(1f).testTag("increment")) { Text("Recompose") }
            OutlinedButton(onClick = showDialog, modifier = Modifier.weight(1f).testTag("open-dialog")) { Text("Open dialog") }
        }
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Stateful controls", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Show animated detail", Modifier.weight(1f))
                    Switch(checked = showDetails, onCheckedChange = setDetails, modifier = Modifier.semantics { contentDescription = "Show animated detail"; stateDescription = if (showDetails) "On" else "Off" }.testTag("detail-switch"))
                }
                AnimatedVisibility(showDetails) { Text("AnimatedVisibility content", Modifier.fillMaxWidth().padding(8.dp).background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp)).padding(12.dp)) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Enabled", Modifier.weight(1f)); Checkbox(checked, setChecked, modifier = Modifier.semantics { contentDescription = "Enabled checkbox"; stateDescription = if (checked) "Checked" else "Unchecked" }.testTag("enabled-checkbox")) }
                Text("Animated value: ${(slider * 100).toInt()}%")
                Slider(value = slider, onValueChange = setSlider, modifier = Modifier.semantics { contentDescription = "Animated value"; stateDescription = "${(slider * 100).toInt()} percent" }.testTag("slider"))
            }
        }
        MiniChart(slider)
        AndroidViewCard()
        RepeatContent()
    }
}

@Composable
private fun Components(modifier: Modifier, query: String, setQuery: (String) -> Unit, showDialog: () -> Unit) {
    val options = listOf("Buttons", "Cards", "Text fields", "Progress indicators", "Chips", "Canvas", "AndroidView", "Dialog", "LazyColumn")
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Compose component gallery", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(query, setQuery, Modifier.fillMaxWidth().testTag("search-field"), label = { Text("Filter components") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { AssistChip(onClick = {}, label = { Text("AssistChip") }); SuggestionChip(onClick = {}, label = { Text("Suggestion") }) }
        LinearProgressIndicator(progress = { 0.68f }, Modifier.fillMaxWidth())
        CircularProgressIndicator(Modifier.size(32.dp))
        options.filter { it.contains(query, ignoreCase = true) }.forEach { label ->
            Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f)); TextButton(onClick = showDialog) { Text("Inspect") }
                }
            }
        }
    }
}

@Composable
private fun MiniChart(value: Float) {
    val animated by animateFloatAsState(value, label = "chart")
    val tint by animateColorAsState(if (value > 0.5f) Color(0xFF6750A4) else Color(0xFF008577), label = "chart-color")
    Canvas(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF1EFF5)).semantics { contentDescription = "Animated canvas chart" }) {
        val points = 8
        val baseline = size.height * .8f
        val dx = size.width / (points - 1)
        for (i in 0 until points - 1) {
            val a = Offset(i * dx, baseline - (i % 3 + animated) * size.height * .18f)
            val b = Offset((i + 1) * dx, baseline - ((i + 1) % 3 + animated) * size.height * .18f)
            drawLine(tint, a, b, strokeWidth = 5.dp.toPx())
        }
    }
}

@Composable
private fun AndroidViewCard() {
    var taps by remember { mutableIntStateOf(0) }
    Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Classic Android View inside Compose", fontWeight = FontWeight.SemiBold)
            AndroidView(
                factory = { context -> TextView(context).apply { textSize = 16f; val padding = (12 * resources.displayMetrics.density).toInt(); setPadding(padding, padding, padding, padding) } },
                update = { view ->
                    view.text = "Android TextView · tapped $taps times"
                },
                modifier = Modifier.fillMaxWidth().clickable { taps++ }
                    .semantics { contentDescription = "Android TextView · tapped $taps times" }
                    .testTag("android-view")
            )
        }
    }
}

@Composable
private fun RepeatContent() {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Nested layout samples", fontWeight = FontWeight.SemiBold)
        (1..4).forEach { index ->
            Row(Modifier.fillMaxWidth().alpha(if (index == 4) .85f else 1f).background(Color(0xFFF7F4FA), RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF6750A4)))
                Column(Modifier.weight(1f).padding(start = 12.dp)) { Text("Nested item $index", fontWeight = FontWeight.Medium); Text("Row + Column + Surface", fontSize = 12.sp, color = Color.Gray) }
                Text("•••", color = Color.Gray)
            }
        }
    }
}
