package com.jetpackduba.gitnuro.ui.tree_files

import kotlin.math.max

fun <T> entriesToTreeEntry(
    showAsTree: Boolean,
    entries: List<T>,
    treeContractedDirs: List<String>,
    sortDescending: Boolean = false,
    onGetEntryPath: (T) -> String,
): List<TreeItem<T>> {
    // In the tree, like GitKraken, each level shows its directories before its files
    val pathsComparator = PathsComparator(directoriesFirst = showAsTree, descending = sortDescending)

    // The paths are split only once (not in every comparison): these lists can have thousands of files
    val sortedEntries = entries
        .map { entry -> SplitPath(entry, onGetEntryPath(entry)) }
        .sortedWith { entry1, entry2 -> pathsComparator.compare(entry1.parts, entry2.parts) }

    if (!showAsTree) {
        return sortedEntries.map { entry -> TreeItem.File(entry.data, entry.parts.last(), entry.path, 0) }
    }

    val contractedDirs = treeContractedDirs.toHashSet()
    val addedDirs = HashSet<String>()
    val treeItems = ArrayList<TreeItem<T>>(sortedEntries.size)

    for (entry in sortedEntries) {
        val parts = entry.parts
        val dirPath = StringBuilder()
        var isParentDirectoryContracted = false

        for (index in 0 until parts.lastIndex) {
            if (index > 0) {
                dirPath.append('/')
            }
            dirPath.append(parts[index])

            val path = dirPath.toString()
            val isContracted = path in contractedDirs

            // Each directory is added once, the first time one of its files shows up
            if (addedDirs.add(path)) {
                treeItems.add(TreeItem.Dir(!isContracted, parts[index], path, index))
            }

            if (isContracted) {
                // Nothing inside a contracted directory is shown
                isParentDirectoryContracted = true
                break
            }
        }

        if (!isParentDirectoryContracted) {
            treeItems.add(TreeItem.File(entry.data, parts.last(), entry.path, parts.lastIndex))
        }
    }

    return treeItems
}

private class SplitPath<T>(val data: T, val path: String) {
    val parts: List<String> = path.split("/")
}

/**
 * Compares paths part by part. With [directoriesFirst], when one path continues into a directory and the other ends
 * in a file at the same level, the directory goes first (regardless of [descending], which only reverses names).
 */
private class PathsComparator(
    private val directoriesFirst: Boolean = false,
    private val descending: Boolean = false,
) : Comparator<List<String>> {
    override fun compare(path1Parts: List<String>, path2Parts: List<String>): Int {
        val maxIndex = max(path1Parts.count(), path2Parts.count())

        for (i in 0 until maxIndex) {
            val part1 = path1Parts.getOrNull(i) ?: return -1
            val part2 = path2Parts.getOrNull(i) ?: return 1

            if (directoriesFirst && part1 != part2) {
                val isDirectory1 = i < path1Parts.lastIndex
                val isDirectory2 = i < path2Parts.lastIndex

                if (isDirectory1 != isDirectory2) {
                    return if (isDirectory1) -1 else 1
                }
            }

            val comparison = part1.compareTo(part2, ignoreCase = true)
                .takeIf { it != 0 }
                ?: part1.compareTo(part2)

            if (comparison != 0) {
                return if (descending) -comparison else comparison
            }
        }

        return 0
    }
}

sealed interface TreeItem<out T> {
    val fullPath: String
    val displayName: String
    val depth: Int

    data class Dir(
        val isExpanded: Boolean,
        override val displayName: String,
        override val fullPath: String,
        override val depth: Int,
    ) : TreeItem<Nothing>

    data class File<T>(
        val data: T,
        override val displayName: String,
        override val fullPath: String,
        override val depth: Int,
    ) : TreeItem<T>
}

/**
 * Returns every directory (and parent directory) that contains at least one of the given paths.
 */
fun allDirectoriesOf(filePaths: List<String>): List<String> {
    val directories = LinkedHashSet<String>()

    for (filePath in filePaths) {
        var separatorIndex = filePath.indexOf('/')

        while (separatorIndex >= 0) {
            directories.add(filePath.substring(0, separatorIndex))
            separatorIndex = filePath.indexOf('/', separatorIndex + 1)
        }
    }

    return directories.toList()
}

data class TreeExpansionState(
    val canExpandAll: Boolean = false,
    val canCollapseAll: Boolean = false,
)

/**
 * "Expand all" only makes sense if some directory is collapsed and "Collapse all" if some directory is expanded.
 */
fun treeExpansionState(filePaths: List<String>, contractedDirectories: List<String>): TreeExpansionState {
    val directories = allDirectoriesOf(filePaths)
    val contracted = contractedDirectories.toSet()

    return TreeExpansionState(
        canExpandAll = directories.any { it in contracted },
        canCollapseAll = directories.any { it !in contracted },
    )
}
