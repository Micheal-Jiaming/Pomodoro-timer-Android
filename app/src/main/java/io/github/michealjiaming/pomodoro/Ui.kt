/*
 * The entire user interface: PomodoroScreen first, then the pieces it is built
 * from, then the two dialogs that can appear over it.
 *
 * Three things to know before reading on.
 *
 * Nothing in this file holds timer state. TimerViewModel owns all of it; these
 * composables read it and call back, which is why almost every function here takes
 * a TimerState and a Palette rather than reaching for a global.
 *
 * Sizes are derived from the measured screen rather than fixed, so one set of code
 * serves a tall phone and a short landscape window. That is why ratios like 0.62f
 * appear instead of dp constants, and each one is explained where it is used.
 *
 * Every import below is androidx.compose or androidx.ui. There is no third-party UI
 * library anywhere in the app, which is part of why it qualifies for F-Droid — see
 * the Publishing section of the project document.
 */
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

/**
 * The whole screen: the countdown ring, the controls, and the two dialogs that
 * can sit on top of them.
 *
 * vm owns every piece of timer state; this composable keeps only one thing of its
 * own, which mode's Custom dialog is open. Everything else is read back out of
 * `vm.state` and `vm.palette`, so a state change in the view model is what redraws
 * the screen.
 *
 * There are two whole layouts rather than one flexible column, picked by comparing
 * width against height — see the comment on `wide` below for the reason.
 */
@Composable
fun PomodoroScreen(vm: TimerViewModel) {
    val palette = vm.palette
    val state = vm.state
    // null means no Custom dialog is open; a Mode means the dialog is open and
    // will apply its result to that mode. Holding the mode rather than a boolean
    // is what lets one dialog serve both the WORK and BREAK rows.
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
                        // Three limits, whichever bites first. 62% of the height
                        // leaves room for the header above it; 32% of the width
                        // keeps the ring inside its half of the split, since the
                        // controls take the other; and 300.dp is a hard ceiling so
                        // the ring does not balloon absurdly on a tablet.
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
                    // The same three limits, retuned for a tall screen. Width is
                    // now the generous one (72%, since the ring has the full width
                    // to itself) and height the tight one (42%, because the
                    // controls sit below rather than beside it).
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

/**
 * The small title above the ring. The letter-spacing is baked into the string as
 * literal spaces rather than applied as a text style, which is crude but keeps it
 * identical to the desktop app's title.
 */
@Composable
private fun Header(palette: Palette) {
    Text(
        text = "P O M O D O R O   T I M E R",
        color = palette.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
    )
}

/**
 * Presets, Start/Reset, the counter and the bottom row — everything but the ring.
 *
 * state and palette are passed in rather than read from vm again so that the whole
 * block is drawn from one consistent snapshot. onCustom is called with the mode
 * whose *Custom* button was pressed, and the caller is responsible for opening the
 * dialog; this composable does not know dialogs exist.
 *
 * Declared as an extension on `ColumnScope` because it uses `Modifier.align` for
 * the centred counter, which only exists inside a Column. It therefore cannot be
 * called from a Row or a bare Box — both call sites wrap it in a Column.
 */
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

    // Start/Reset are the primary actions, so they are taller and larger-texted
    // than the presets, and Start is given 1.4 against Reset's 1 so the button you
    // press constantly is the bigger target. One button toggles between Start and
    // Pause rather than showing both, which is what keeps the pair from becoming
    // three buttons.
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

    // The bottom row. The theme button gets a fixed 104.dp rather than a weight,
    // because its label changes with the theme ("Black", "Paper", "Mist") and a
    // weighted button would visibly resize each time you cycled it. Everything in
    // this row was once below the fold on a short landscape screen — see the
    // wide/tall split at the top of the file for the fix.
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
            // Every colour is overridden. Material's defaults come from its own
            // theme, which would ignore the app's palette and leave this switch
            // looking wrong in Paper and Mist.
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

/**
 * The countdown ring and the time inside it.
 *
 * diameter is the finished outside size of the circle; the caller works it out
 * from the screen, so every measurement in here is a fraction of it rather than a
 * fixed dp value. That is what lets the same code fill a phone in portrait and a
 * short landscape window without a second set of numbers.
 *
 * Two arcs are drawn on top of each other: a full circle in the track colour, then
 * the remaining time in the accent colour on top of it. `state.fraction` supplies
 * the proportion still to run, so the accent arc shrinks as the session goes on.
 */
@Composable
private fun CountdownRing(state: TimerState, palette: Palette, diameter: Dp) {
    val accent = palette.accentFor(state.mode)
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            // Ring thickness, 5.5% of the diameter. Everything else here derives
            // from it: the arc has to be inset by half the stroke width, because
            // a stroke straddles the path it is drawn along and would otherwise
            // be clipped by the edge of the canvas.
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
            // MM:SS at 21% of the diameter. Taking the number from `diameter.value`
            // — a dp figure — and using it as sp means these digits scale with the
            // ring instead of with the system font-size setting. The upside is that
            // the text always fits inside the circle at any ring size; the cost is
            // that a user who has enlarged their system font does not get larger
            // digits here.
            Text(
                text = formatRemaining(state.remainingMillis),
                color = palette.fg,
                fontSize = (diameter.value * 0.21f).sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            // Three distinct states share one line. WORK or BREAK always shows;
            // "PAUSED" is appended only when a session is set up but not running
            // AND has time left. The remainingMillis test is what stops a finished
            // session (0 left, not running) from being labelled paused, which would
            // read as though it could be resumed.
            //
            // 5% of the diameter, clamped to 10-15sp: without the clamp this label
            // becomes illegible on a small ring and comically large on a big one.
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

/**
 * One row of duration buttons: a right-aligned label, a button per preset, and a
 * *Custom* button on the end.
 *
 * label is the row heading ("WORK" or "BREAK"); minutes are the preset lengths in
 * minutes; mode is which kind of session this row sets. onPick receives the chosen
 * number of minutes, onCustom takes no argument because the caller already knows
 * which row it built.
 *
 * A button is highlighted only when the row's mode is the *current* mode and its
 * length matches the current total. Both halves matter: without the mode test, the
 * 15-minute work button and the 15-minute break button would light up together.
 */
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
        // Fixed 46.dp and right-aligned, so "WORK" and "BREAK" end at the same
        // x-position and the button columns line up between the two rows. A wrapped
        // or weighted label would leave the rows visibly ragged.
        Text(
            text = label,
            color = palette.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(46.dp),
        )
        minutes.forEach { m ->
            // totalSeconds is compared in seconds, so the preset's minutes are
            // converted rather than the state being divided — integer division
            // would make 90 seconds look like a selected 1-minute preset.
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

/**
 * The one button style the whole app uses, so that presets, Start/Reset, the theme
 * switch and the dialog buttons cannot drift apart visually.
 *
 * background and foreground are passed in rather than derived, because a chip is
 * used both as a plain card-coloured button and as an accent-coloured one, and the
 * caller is the only thing that knows which. The defaults — height 44.dp and
 * fontSize 13.sp — suit the preset rows; the Start/Reset pair overrides both to
 * 52.dp and 16.sp to read as the primary action.
 *
 * Horizontal content padding is cut to 4.dp, well below Material's default, and
 * the label is held to one line. Together those are what let "45 min" fit in a
 * narrow column without wrapping or being ellipsised.
 */
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

/**
 * Asks for a session length in minutes.
 *
 * mode only affects the wording ("Custom session" against "Custom break") — the
 * accepted range is the same for both. initial is the number the field starts at,
 * in minutes, normally the current session length. onConfirm is called with a
 * value already known to be inside `CUSTOM_MIN..CUSTOM_MAX`; onDismiss is called
 * for Cancel, for a tap outside, and for the system back gesture.
 *
 * Validation runs in three places on purpose: unparsable or out-of-range input
 * turns the field red, disables *Set*, and is checked once more inside the click
 * handler. The last one is belt-and-braces, since a disabled button cannot fire.
 */
@Composable
private fun CustomDurationDialog(
    mode: Mode,
    palette: Palette,
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val noun = if (mode == Mode.BREAK) "Break" else "Session"
    // The field holds a String, not an Int, because a text field must be able to
    // be empty while someone is retyping. `initial` is clamped on the way in so a
    // nonsense starting value cannot appear in the box.
    var text by remember { mutableStateOf(initial.coerceIn(CUSTOM_MIN, CUSTOM_MAX).toString()) }
    // Parsed fresh on every recomposition. minutes is null while the field is empty
    // or unparsable, which is why `valid` has to null-check before the range test.
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
                // Non-digits are dropped as they are typed, so a stray letter or
                // minus sign never reaches the parser. take(3) caps the entry at
                // three characters, which is enough for CUSTOM_MAX of 180 —
                // raising CUSTOM_MAX above 999 would need this number raised too,
                // or input would be silently truncated.
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

/**
 * The prompt shown when a session runs out, offering what to do next.
 *
 * finished is the mode that just *ended*, not the one being offered — so after
 * work it leads with breaks, and after a break it leads with work. sessionsDone is
 * only used in the wording. onPick is called with a length in minutes and the mode
 * to start, and every button starts a *new* session immediately rather than
 * extending the finished one. onDismiss ("Not now") leaves the timer idle.
 *
 * There is no confirmButton: the choices themselves are the confirmation, so a
 * separate "OK" would have nothing left to do.
 */
@Composable
private fun FinishedDialog(
    finished: Mode,
    sessionsDone: Int,
    palette: Palette,
    onPick: (Int, Mode) -> Unit,
    onDismiss: () -> Unit,
) {
    // Wording is built here rather than in the layout below so the two branches sit
    // side by side and can be compared. The counter is only mentioned after work,
    // because a finished break has not completed anything to count.
    val heading: String
    val sub: String
    if (finished == Mode.WORK) {
        heading = "Session finished — time for a break."
        // Pluralised by hand: "1 session" but "2 sessions". The test is != 1 rather
        // than > 1 so that a count of 0 would also read "sessions", though in
        // practice this dialog never appears at 0.
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
                    // Only the two shortest breaks are offered here, and the "+"
                    // prefix reads as "a bit more break". Note what it does NOT
                    // do: it starts a fresh break of that length rather than
                    // adding time to the one that just ended, because onPick
                    // routes to beginNext like every other button in this dialog.
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

/**
 * A labelled row of duration buttons inside the finished-session dialog.
 *
 * Unlike [PresetRow] nothing here is ever highlighted as selected, because these
 * buttons choose what to start next rather than reflecting a current setting. That
 * is also why accent is a parameter: the caller colours the row green for breaks or
 * red for work, so the two rows are told apart by colour instead of by selection.
 *
 * prefix is prepended to each label — empty for normal rows, "+" for the
 * extend-the-break row.
 */
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
