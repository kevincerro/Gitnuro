package com.jetpackduba.gitnuro.data.git

import com.jetpackduba.gitnuro.common.ProcessEnvironment
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.FS_POSIX
import java.io.File

/**
 * macOS/Linux FS whose processes (hooks, LFS/external filters...) get the full PATH: apps launched from the desktop
 * only get a minimal one, so hooks calling node, git-lfs, gpg... would fail.
 */
class PosixFs : FS_POSIX {
    constructor() : super()

    private constructor(src: FS) : super(src)

    override fun newInstance(): FS = PosixFs(this)

    override fun runInShell(cmd: String, args: Array<out String>): ProcessBuilder {
        return ProcessEnvironment.apply(super.runInShell(cmd, args))
    }

    override fun discoverGitExe(): File? {
        return ProcessEnvironment.resolve("git")?.let { File(it) } ?: super.discoverGitExe()
    }
}
