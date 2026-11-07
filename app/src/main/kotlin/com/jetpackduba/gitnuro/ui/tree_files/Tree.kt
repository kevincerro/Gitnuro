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
    val sortedEntries = entries.sortedWith { entry1, entry2 ->
        pathsComparator.compare(onGetEntryPath(entry1), onGetEntryPath(entry2))
    }

    if (!showAsTree) {
        return sortedEntries.map { entry -> entryToTreeFile(entry, onGetEntryPath(entry)) }
    }

    return sortedEntries
        .asSequence()
        .flatMap { entry ->
            val filePath = onGetEntryPath(entry)
            val parts = filePath.split("/")

            parts.mapIndexed { index, partName ->
                if (index == parts.lastIndex) {
                    val isParentDirectoryContracted = treeContractedDirs.any { contractedDir ->
                        filePath.startsWith("$contractedDir/")
                    }

                    if (isParentDirectoryContracted) {
                        null
                    } else {
                        TreeItem.File(entry, partName, filePath, index)
                    }
                } else {
                    val dirPath = parts.slice(0..index).joinToString("/")
                    val isParentDirectoryContracted = treeContractedDirs.any { contractedDir ->
                        dirPath.startsWith("$contractedDir/") &&
                                dirPath != contractedDir
                    }
                    val isExactDirectoryContracted = treeContractedDirs.any { contractedDir ->
                        dirPath == contractedDir
                    }

                    when {
                        isParentDirectoryContracted -> null
                        isExactDirectoryContracted -> TreeItem.Dir(false, partName, dirPath, index)
                        else -> TreeItem.Dir(true, partName, dirPath, index)
                    }
                }
            }
        }
        .filterNotNull()
        .distinct()
        .toList()
}

private fun <T> entryToTreeFile(data: T, path: String): TreeItem.File<T> {
    val parts = path.split("/")
    val displayName = parts.last()

    return TreeItem.File(data, displayName, path, 0)
}

/**
 * Compares paths part by part. With [directoriesFirst], when one path continues into a directory and the other ends
 * in a file at the same level, the directory goes first (regardless of [descending], which only reverses names).
 */
private class PathsComparator(
    private val directoriesFirst: Boolean = false,
    private val descending: Boolean = false,
) : Comparator<String> {
    override fun compare(path1: String, path2: String): Int {
        val path1Parts = path1.split("/")
        val path2Parts = path2.split("/")

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
    return filePaths
        .flatMap { filePath ->
            val parts = filePath.split("/").dropLast(1)
            parts.indices.map { index -> parts.slice(0..index).joinToString("/") }
        }
        .distinct()
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
