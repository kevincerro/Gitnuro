package com.jetpackduba.gitnuro.ui.diff

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jetpackduba.gitnuro.app.generated.resources.Res
import com.jetpackduba.gitnuro.app.generated.resources.done
import com.jetpackduba.gitnuro.extensions.handMouseClickable
import com.jetpackduba.gitnuro.theme.conflictFile
import com.jetpackduba.gitnuro.theme.monoTypography
import com.jetpackduba.gitnuro.theme.onBackgroundSecondary
import com.jetpackduba.gitnuro.ui.components.PrimaryButton
import com.jetpackduba.gitnuro.ui.components.ScrollableLazyColumn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import java.io.File

private const val MAX_CONFLICT_FILE_SIZE = 5L * 1024 * 1024

/**
 * Part of a file with conflict markers: lines that both sides have, or a conflict with the lines of each side.
 */
internal sealed interface ConflictSegment {
    data class Common(val lines: List<String>) : ConflictSegment

    data class Conflict(
        /** Position among the conflicts of the file, starting at 0 */
        val index: Int,
        val ours: List<String>,
        val theirs: List<String>,
    ) : ConflictSegment
}

internal data class ConflictFile(
    val segments: List<ConflictSegment>,
    /** Name after the "<<<<<<<" marker (usually HEAD) */
    val oursLabel: String,
    /** Name after the ">>>>>>>" marker (the branch or commit being merged) */
    val theirsLabel: String,
    val lineSeparator: String,
) {
    val conflicts: List<ConflictSegment.Conflict> = segments.filterIsInstance<ConflictSegment.Conflict>()
}

/** A line of a conflict taken for the output: its side and its index in the lines of that side */
internal data class ConflictPick(val fromOurs: Boolean, val lineIndex: Int)

/**
 * Lines taken for a conflict, in the order the user picked them (which is their order in the output).
 */
internal data class ConflictSelection(val picks: List<ConflictPick> = emptyList()) {
    val isEmpty: Boolean get() = picks.isEmpty()

    fun contains(fromOurs: Boolean, lineIndex: Int) = picks.contains(ConflictPick(fromOurs, lineIndex))

    fun count(fromOurs: Boolean) = picks.count { it.fromOurs == fromOurs }

    /** Adds the line at the end, or removes it if it was already taken */
    fun toggle(fromOurs: Boolean, lineIndex: Int): ConflictSelection {
        val pick = ConflictPick(fromOurs, lineIndex)

        return copy(picks = if (picks.contains(pick)) picks - pick else picks + pick)
    }

    /** Takes every line of a side (the ones already taken keep their place, the rest go at the end) or none */
    fun setSide(fromOurs: Boolean, linesCount: Int, selected: Boolean): ConflictSelection {
        return if (selected) {
            val missing = (0 until linesCount)
                .map { ConflictPick(fromOurs, it) }
                .filterNot { picks.contains(it) }

            copy(picks = picks + missing)
        } else {
            copy(picks = picks.filterNot { it.fromOurs == fromOurs })
        }
    }
}

/**
 * Parses the conflict markers written by git ("<<<<<<<", "=======", ">>>>>>>", and "|||||||" with diff3 style, whose
 * base lines are dropped). Returns null if the text has no (well-formed) conflicts.
 */
internal fun parseConflictFile(content: String): ConflictFile? {
    val lineSeparator = if (content.contains("\r\n")) "\r\n" else "\n"
    val lines = content.replace("\r\n", "\n").split("\n")

    val segments = mutableListOf<ConflictSegment>()
    var common = mutableListOf<String>()
    var ours = mutableListOf<String>()
    var theirs = mutableListOf<String>()
    var oursLabel = ""
    var theirsLabel = ""

    // 0: outside a conflict, 1: ours, 2: base (diff3), 3: theirs
    var state = 0
    var conflictsCount = 0

    for (line in lines) {
        when {
            state == 0 && line.startsWith("<<<<<<<") -> {
                if (common.isNotEmpty()) segments.add(ConflictSegment.Common(common))
                common = mutableListOf()
                if (oursLabel.isEmpty()) oursLabel = line.removePrefix("<<<<<<<").trim()
                state = 1
            }

            state == 1 && line.startsWith("|||||||") -> state = 2
            (state == 1 || state == 2) && line == "=======" -> state = 3
            state == 3 && line.startsWith(">>>>>>>") -> {
                if (theirsLabel.isEmpty()) theirsLabel = line.removePrefix(">>>>>>>").trim()
                segments.add(ConflictSegment.Conflict(conflictsCount++, ours, theirs))
                ours = mutableListOf()
                theirs = mutableListOf()
                state = 0
            }

            state == 0 -> common.add(line)
            state == 1 -> ours.add(line)
            state == 3 -> theirs.add(line)
            // state == 2: base lines, not needed
        }
    }

    // A conflict that never ends: the markers aren't the ones expected, don't touch the file
    if (state != 0 || conflictsCount == 0) return null

    if (common.isNotEmpty()) segments.add(ConflictSegment.Common(common))

    return ConflictFile(
        segments = segments,
        oursLabel = oursLabel.ifEmpty { "current" },
        theirsLabel = theirsLabel.ifEmpty { "incoming" },
        lineSeparator = lineSeparator,
    )
}

internal data class OutputLine(val text: String, /** null: common, true: ours, false: theirs */ val fromOurs: Boolean?)

/**
 * Result of the file: the common lines and, for each conflict, the chosen lines in the order they were picked.
 */
internal fun buildConflictOutput(file: ConflictFile, selections: List<ConflictSelection>): List<OutputLine> {
    return file.segments.flatMap { segment ->
        when (segment) {
            is ConflictSegment.Common -> segment.lines.map { OutputLine(it, null) }
            is ConflictSegment.Conflict -> {
                selections[segment.index].picks.mapNotNull { pick ->
                    val lines = if (pick.fromOurs) segment.ours else segment.theirs

                    lines.getOrNull(pick.lineIndex)?.let { OutputLine(it, pick.fromOurs) }
                }
            }
        }
    }
}

private sealed interface ResolverRow {
    data class CommonLine(val text: String) : ResolverRow
    data class ConflictHeader(val conflict: ConflictSegment.Conflict) : ResolverRow
    data class ConflictLine(val conflict: ConflictSegment.Conflict, val lineIndex: Int) : ResolverRow
}

private sealed interface ConflictLoadState {
    data object Loading : ConflictLoadState
    data class Loaded(val file: ConflictFile) : ConflictLoadState

    /** Not a text file with conflict markers (e.g. deleted by one side, binary) */
    data object NotResolvable : ConflictLoadState
}

/**
 * Visual conflict resolver like GitKraken's: the current and the incoming versions side by side, with a checkbox per
 * conflicting line to pick what goes to the output, shown below. Saving writes the output to the file and stages it.
 *
 * @param fallback shown when the file can't be resolved here (it has no conflict markers)
 */
@Composable
fun ConflictResolver(
    file: File,
    onResolved: () -> Unit,
    fallback: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember(file) { mutableStateOf<ConflictLoadState>(ConflictLoadState.Loading) }
    val selections = remember(file) { mutableStateListOf<ConflictSelection>() }
    var saveError by remember(file) { mutableStateOf<String?>(null) }

    // Text of the output. It's generated from the picked lines, and can then be edited by hand
    var output by remember(file) { mutableStateOf(TextFieldValue("")) }
    var isOutputEdited by remember(file) { mutableStateOf(false) }

    LaunchedEffect(file) {
        val loaded = withContext(Dispatchers.IO) {
            try {
                if (!file.isFile || file.length() > MAX_CONFLICT_FILE_SIZE) {
                    null
                } else {
                    val bytes = file.readBytes()
                    if (bytes.contains(0)) null else parseConflictFile(bytes.decodeToString())
                }
            } catch (_: Exception) {
                null
            }
        }

        selections.clear()

        state = if (loaded == null) {
            ConflictLoadState.NotResolvable
        } else {
            repeat(loaded.conflicts.size) { selections.add(ConflictSelection()) }
            output = TextFieldValue(generateOutputText(loaded, selections.toList()))
            isOutputEdited = false
            ConflictLoadState.Loaded(loaded)
        }
    }

    when (val currentState = state) {
        ConflictLoadState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ConflictLoadState.NotResolvable -> fallback()
        is ConflictLoadState.Loaded -> {
            val conflictFile = currentState.file

            // The selections are filled right after loading, in the same effect
            if (selections.size != conflictFile.conflicts.size) return

            ConflictResolverContent(
                conflictFile = conflictFile,
                selections = selections,
                output = output,
                isOutputEdited = isOutputEdited,
                saveError = saveError,
                onSelectionChange = { conflictIndex, selection ->
                    selections[conflictIndex] = selection

                    // The output follows the picked lines: changes made by hand are replaced
                    output = TextFieldValue(generateOutputText(conflictFile, selections.toList()))
                    isOutputEdited = false
                },
                onOutputChange = { newOutput ->
                    if (newOutput.text != output.text) isOutputEdited = true
                    output = newOutput
                },
                onSave = {
                    scope.launch {
                        // The text field works with "\n", the file keeps its own line endings
                        val content = output.text.replace("\r\n", "\n").replace("\n", conflictFile.lineSeparator)

                        val error = withContext(Dispatchers.IO) {
                            try {
                                file.writeText(content)
                                null
                            } catch (ex: Exception) {
                                ex.message ?: "The file could not be saved."
                            }
                        }

                        saveError = error

                        if (error == null) {
                            onResolved()
                        }
                    }
                },
            )
        }
    }
}

private fun generateOutputText(file: ConflictFile, selections: List<ConflictSelection>): String {
    return buildConflictOutput(file, selections).joinToString("\n") { it.text }
}

@Composable
private fun ConflictResolverContent(
    conflictFile: ConflictFile,
    selections: List<ConflictSelection>,
    output: TextFieldValue,
    isOutputEdited: Boolean,
    saveError: String?,
    onSelectionChange: (conflictIndex: Int, ConflictSelection) -> Unit,
    onOutputChange: (TextFieldValue) -> Unit,
    onSave: () -> Unit,
) {
    val oursColor = MaterialTheme.colors.primaryVariant
    val theirsColor = MaterialTheme.colors.conflictFile

    val rows = remember(conflictFile) {
        conflictFile.segments.flatMap { segment ->
            when (segment) {
                is ConflictSegment.Common -> segment.lines.map { ResolverRow.CommonLine(it) }
                is ConflictSegment.Conflict -> listOf(ResolverRow.ConflictHeader(segment)) +
                        (0 until maxOf(segment.ours.size, segment.theirs.size)).map { ResolverRow.ConflictLine(segment, it) }
            }
        }
    }

    val pendingConflicts = selections.count { it.isEmpty }

    fun isSideSelected(conflict: ConflictSegment.Conflict, fromOurs: Boolean): Boolean {
        val lines = if (fromOurs) conflict.ours else conflict.theirs

        return lines.isNotEmpty() && selections[conflict.index].count(fromOurs) == lines.size
    }

    fun setSide(conflict: ConflictSegment.Conflict, fromOurs: Boolean, selected: Boolean) {
        val lines = if (fromOurs) conflict.ours else conflict.theirs

        onSelectionChange(conflict.index, selections[conflict.index].setSide(fromOurs, lines.size, selected))
    }

    // Whole side of every conflict (the ones without lines in that side don't count)
    fun isWholeSideSelected(fromOurs: Boolean): Boolean {
        val conflicts = conflictFile.conflicts.filter { (if (fromOurs) it.ours else it.theirs).isNotEmpty() }

        return conflicts.isNotEmpty() && conflicts.all { isSideSelected(it, fromOurs) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Titles of both sides, each one with the checkbox that takes all its conflicting lines
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.surface)
                .height(34.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SideTitle(
                title = "Current",
                label = conflictFile.oursLabel,
                color = oursColor,
                checked = isWholeSideSelected(fromOurs = true),
                onToggle = { checked -> conflictFile.conflicts.forEach { setSide(it, fromOurs = true, checked) } },
                modifier = Modifier.weight(1f),
            )

            VerticalDivider()

            SideTitle(
                title = "Incoming",
                label = conflictFile.theirsLabel,
                color = theirsColor,
                checked = isWholeSideSelected(fromOurs = false),
                onToggle = { checked -> conflictFile.conflicts.forEach { setSide(it, fromOurs = false, checked) } },
                modifier = Modifier.weight(1f),
            )
        }

        // Both versions in the same list, so they scroll together and their lines stay aligned
        ScrollableLazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            itemsIndexed(rows) { _, row ->
                when (row) {
                    is ResolverRow.CommonLine -> SplitRow(
                        left = { CodeLine(text = row.text, isDimmed = true) },
                        right = { CodeLine(text = row.text, isDimmed = true) },
                    )

                    is ResolverRow.ConflictHeader -> {
                        val conflict = row.conflict

                        SplitRow(
                            left = {
                                ConflictSideHeader(
                                    text = "Conflict ${conflict.index + 1} of ${conflictFile.conflicts.size}",
                                    color = oursColor,
                                    checked = isSideSelected(conflict, fromOurs = true),
                                    onToggle = {
                                        setSide(conflict, fromOurs = true, !isSideSelected(conflict, fromOurs = true))
                                    },
                                )
                            },
                            right = {
                                ConflictSideHeader(
                                    text = "Conflict ${conflict.index + 1} of ${conflictFile.conflicts.size}",
                                    color = theirsColor,
                                    checked = isSideSelected(conflict, fromOurs = false),
                                    onToggle = {
                                        setSide(conflict, fromOurs = false, !isSideSelected(conflict, fromOurs = false))
                                    },
                                )
                            },
                        )
                    }

                    is ResolverRow.ConflictLine -> {
                        val conflict = row.conflict
                        val index = row.lineIndex
                        val selection = selections[conflict.index]

                        SplitRow(
                            left = {
                                conflict.ours.getOrNull(index)?.let { text ->
                                    SelectableCodeLine(
                                        text = text,
                                        color = oursColor,
                                        checked = selection.contains(fromOurs = true, index),
                                        onToggle = {
                                            onSelectionChange(conflict.index, selection.toggle(fromOurs = true, index))
                                        },
                                    )
                                }
                            },
                            right = {
                                conflict.theirs.getOrNull(index)?.let { text ->
                                    SelectableCodeLine(
                                        text = text,
                                        color = theirsColor,
                                        checked = selection.contains(fromOurs = false, index),
                                        onToggle = {
                                            onSelectionChange(conflict.index, selection.toggle(fromOurs = false, index))
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }

        // Output: the file that will be saved, across the whole width. It can be edited by hand
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.surface)
                .height(34.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Output",
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.onBackground,
            )

            Text(
                text = when {
                    saveError != null -> saveError
                    isOutputEdited -> "Edited by hand. Checking or unchecking lines generates the output again"
                    pendingConflicts == 0 -> "All conflicts have lines selected"
                    pendingConflicts == 1 -> "1 conflict without lines selected (nothing of it will be kept)"
                    else -> "$pendingConflicts conflicts without lines selected (nothing of them will be kept)"
                },
                style = MaterialTheme.typography.body2,
                color = if (saveError != null) MaterialTheme.colors.error else MaterialTheme.colors.onBackgroundSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .weight(1f),
            )

            PrimaryButton(
                text = "Save and mark as resolved",
                onClick = onSave,
            )
        }

        val outputScrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.8f),
        ) {
            BasicTextField(
                value = output,
                onValueChange = onOutputChange,
                textStyle = MaterialTheme.typography.body2.copy(
                    fontFamily = monoTypography(),
                    color = MaterialTheme.colors.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colors.onBackground),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(outputScrollState)
                    .padding(horizontal = 24.dp, vertical = 6.dp),
            )

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(outputScrollState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 2.dp),
            )
        }
    }
}

@Composable
private fun SplitRow(
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { left() }
        VerticalDivider()
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { right() }
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(MaterialTheme.colors.onBackground.copy(alpha = 0.12f))
    )
}

@Composable
private fun SideTitle(
    title: String,
    label: String,
    color: Color,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SquareCheckbox(checked = checked, color = color, onClick = { onToggle(!checked) })

        Text(
            text = title,
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(start = 8.dp),
        )

        Text(
            text = label,
            style = MaterialTheme.typography.body2,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 8.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun ConflictSideHeader(
    text: String,
    color: Color,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(color.copy(alpha = 0.3f))
            .handMouseClickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SquareCheckbox(checked = checked, color = color, onClick = onToggle)

        Text(
            text = text,
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun SelectableCodeLine(
    text: String,
    color: Color,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(color.copy(alpha = if (checked) 0.22f else 0.08f))
            .handMouseClickable(onClick = onToggle)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(modifier = Modifier.padding(top = 3.dp, end = 8.dp)) {
            SquareCheckbox(checked = checked, color = color, onClick = onToggle)
        }

        CodeLine(text = text, isDimmed = false)
    }
}

@Composable
private fun CodeLine(text: String, isDimmed: Boolean) {
    Text(
        // An empty line still takes its height
        text = text.ifEmpty { " " },
        style = MaterialTheme.typography.body2,
        fontFamily = monoTypography(),
        color = if (isDimmed) MaterialTheme.colors.onBackgroundSecondary else MaterialTheme.colors.onBackground,
        modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 1.dp, bottom = 1.dp),
    )
}

@Composable
private fun SquareCheckbox(checked: Boolean, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(2.dp)

    Box(
        modifier = Modifier
            .size(13.dp)
            .clip(shape)
            .border(1.dp, if (checked) color else MaterialTheme.colors.onBackgroundSecondary, shape)
            .background(if (checked) color else Color.Transparent)
            .handMouseClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                painter = painterResource(Res.drawable.done),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}
