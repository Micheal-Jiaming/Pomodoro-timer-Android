package io.github.michealjiaming.pomodoro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PomodoroScreen(vm: TimerViewModel) {
    val palette = vm.palette
    val state = vm.state
    var customFor by remember { mutableStateOf<Mode?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Copied out of the scope: Row and Column carry the same DslMarker,
            // so the nested lambdas below cannot see BoxWithConstraints's own
            // maxWidth/maxHeight.
            val screenW = maxWidth
            val screenH = maxHeight
            // A landscape emulator window is barely 300dp tall — stacking
            // everything there pushes the last row below the fold, where nobody
            // finds it. Wide and short gets the ring beside the controls
            // instead of above them.
            val wide = screenW > screenH

            if (wide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Header(palette)
                        Spacer(Modifier.height(10.dp))
                        CountdownRing(
                            state = state,
                            palette = palette,
                            diameter = minOf(screenH * 0.62f, screenW * 0.32f, 300.dp),
                        )
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Controls(vm, state, palette, onCustom = { customFor = it })
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Header(palette)
                    Spacer(Modifier.height(14.dp))
                    CountdownRing(
                        state = state,
                        palette = palette,
                        diameter = minOf(screenW * 0.72f, screenH * 0.42f, 320.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                    Controls(vm, state, palette, onCustom = { customFor = it })
                }
            }
        }
    }

    customFor?.let { mode ->
        CustomDurationDialog(
            mode = mode,
            palette = palette,
            initial = state.totalSeconds / 60,
            onDismiss = { customFor = null },
            onConfirm = { minutes ->
                customFor = null
                vm.setDuration(minutes, mode)
            },
        )
    }

    state.prompt?.let { finished ->
        FinishedDialog(
            finished = finished,
            sessionsDone = state.sessionsDone,
            palette = palette,
            onPick = { minutes, mode -> vm.beginNext(minutes, mode) },
            onDismiss = vm::dismissPrompt,
        )
    }
}

@Composable
private fun Header(palette: Palette) {
    Text(
        text = "P O M O D O R O   T I M E R",
        color = palette.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
    )
}

/** Presets, Start/Reset, the counter and the bottom row — everything but the ring. */
@Composable
private fun ColumnScope.Controls(
    vm: TimerViewModel,
    state: TimerState,
    palette: Palette,
    onCustom: (Mode) -> Unit,
) {
    PresetRow(
        label = "WORK",
        minutes = WORK_PRESETS,
        mode = Mode.WORK,
        state = state,
        palette = palette,
        onPick = { vm.setDuration(it, Mode.WORK) },
        onCustom = { onCustom(Mode.WORK) },
    )
    Spacer(Modifier.height(8.dp))
    PresetRow(
        label = "BREAK",
        minutes = BREAK_PRESETS,
        mode = Mode.BREAK,
        state = state,
        palette = palette,
        onPick = { vm.setDuration(it, Mode.BREAK) },
        onCustom = { onCustom(Mode.BREAK) },
    )

    Spacer(Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Chip(
            text = if (state.running) "Pause" else "Start",
            background = palette.accentFor(state.mode),
            foreground = palette.onAccent,
            modifier = Modifier.weight(1.4f),
            height = 52.dp,
            fontSize = 16.sp,
            onClick = vm::toggle,
        )
        Chip(
            text = "Reset",
            background = palette.card,
            foreground = palette.fg,
            modifier = Modifier.weight(1f),
            height = 52.dp,
            fontSize = 16.sp,
            onClick = vm::reset,
        )
    }

    Spacer(Modifier.height(14.dp))

    Text(
        text = "Sessions completed: ${state.sessionsDone}",
        color = palette.muted,
        fontSize = 13.sp,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )

    Spacer(Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Chip(
            text = palette.label,
            background = palette.card,
            foreground = palette.fg,
            modifier = Modifier.width(104.dp),
            height = 44.dp,
            fontSize = 14.sp,
            onClick = vm::cycleTheme,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Keep screen on", color = palette.muted, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Switch(
                checked = vm.keepScreenOn,
                onCheckedChange = vm::updateKeepScreenOn,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = palette.onAccent,
                    checkedTrackColor = palette.accent,
                    uncheckedThumbColor = palette.muted,
                    uncheckedTrackColor = palette.card,
                    uncheckedBorderColor = palette.track,
                ),
            )
        }
    }
}

@Composable
private fun CountdownRing(state: TimerState, palette: Palette, diameter: Dp) {
    val accent = palette.accentFor(state.mode)
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = size.minDimension * 0.055f
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = palette.track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
            // Depletes clockwise from twelve o'clock, as on the desktop.
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * state.fraction,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatRemaining(state.remainingMillis),
                color = palette.fg,
                fontSize = (diameter.value * 0.21f).sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = state.mode.label +
                    if (state.running || state.remainingMillis <= 0L) "" else "  ·  PAUSED",
                color = palette.muted,
                fontSize = (diameter.value * 0.05f).coerceIn(10f, 15f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun PresetRow(
    label: String,
    minutes: List<Int>,
    mode: Mode,
    state: TimerState,
    palette: Palette,
    onPick: (Int) -> Unit,
    onCustom: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = palette.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(46.dp),
        )
        minutes.forEach { m ->
            val selected = state.mode == mode && state.totalSeconds == m * 60
            Chip(
                text = "$m min",
                background = if (selected) palette.accentFor(mode) else palette.card,
                foreground = if (selected) palette.onAccent else palette.fg,
                modifier = Modifier.weight(1f),
                onClick = { onPick(m) },
            )
        }
        Chip(
            text = "Custom",
            background = palette.card,
            foreground = palette.fg,
            modifier = Modifier.weight(1.15f),
            onClick = onCustom,
        )
    }
}

@Composable
private fun Chip(
    text: String,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    fontSize: TextUnit = 13.sp,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = foreground,
        ),
    ) {
        Text(text = text, fontSize = fontSize, maxLines = 1, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CustomDurationDialog(
    mode: Mode,
    palette: Palette,
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val noun = if (mode == Mode.BREAK) "Break" else "Session"
    var text by remember { mutableStateOf(initial.coerceIn(CUSTOM_MIN, CUSTOM_MAX).toString()) }
    val minutes = text.toIntOrNull()
    val valid = minutes != null && minutes in CUSTOM_MIN..CUSTOM_MAX

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        titleContentColor = palette.fg,
        textContentColor = palette.muted,
        title = { Text("Custom ${noun.lowercase()}") },
        text = {
            Column {
                Text("$noun length in minutes ($CUSTOM_MIN-$CUSTOM_MAX):")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { entered -> text = entered.filter { it.isDigit() }.take(3) },
                    singleLine = true,
                    isError = text.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onConfirm(minutes!!) }, enabled = valid) {
                Text("Set", color = if (valid) palette.accent else palette.muted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = palette.muted) }
        },
    )
}

@Composable
private fun FinishedDialog(
    finished: Mode,
    sessionsDone: Int,
    palette: Palette,
    onPick: (Int, Mode) -> Unit,
    onDismiss: () -> Unit,
) {
    val heading: String
    val sub: String
    if (finished == Mode.WORK) {
        heading = "Session finished — time for a break."
        sub = "You've completed $sessionsDone session" +
            (if (sessionsDone != 1) "s" else "") + " so far."
    } else {
        heading = "Break's over — ready for the next round?"
        sub = "Pick how long you want to focus for."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        titleContentColor = palette.fg,
        textContentColor = palette.muted,
        title = { Text(heading, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(sub, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                if (finished == Mode.WORK) {
                    DialogRow("Take a break:", BREAK_PRESETS, palette, palette.accentBreak) {
                        onPick(it, Mode.BREAK)
                    }
                    Spacer(Modifier.height(14.dp))
                    DialogRow(
                        "Or start the next session now:", WORK_PRESETS, palette, palette.accent,
                    ) {
                        onPick(it, Mode.WORK)
                    }
                } else {
                    DialogRow("Next session:", WORK_PRESETS, palette, palette.accent) {
                        onPick(it, Mode.WORK)
                    }
                    Spacer(Modifier.height(14.dp))
                    DialogRow(
                        "Need a bit longer?", BREAK_PRESETS.take(2), palette,
                        palette.accentBreak, prefix = "+",
                    ) {
                        onPick(it, Mode.BREAK)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now", color = palette.muted) }
        },
    )
}

@Composable
private fun DialogRow(
    label: String,
    minutes: List<Int>,
    palette: Palette,
    accent: Color,
    prefix: String = "",
    onPick: (Int) -> Unit,
) {
    Text(label, color = palette.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        minutes.forEach { m ->
            Chip(
                text = "$prefix$m min",
                background = accent,
                foreground = palette.onAccent,
                modifier = Modifier.weight(1f),
                onClick = { onPick(m) },
            )
        }
    }
}
