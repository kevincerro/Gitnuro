package com.jetpackduba.gitnuro.data.git.branches

import com.jetpackduba.gitnuro.data.git.JGit
import com.jetpackduba.gitnuro.domain.interfaces.ICheckoutBranchGitAction
import com.jetpackduba.gitnuro.domain.models.Branch
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.lib.Constants
import javax.inject.Inject

class CheckoutBranchGitAction @Inject constructor(
    private val jgit: JGit,
) : ICheckoutBranchGitAction {
    override suspend operator fun invoke(repositoryPath: String, branch: Branch) = jgit.provide(repositoryPath) { git ->
        val isRemoteBranch = branch.name.startsWith(Constants.R_REMOTES)
        val localRef = if (isRemoteBranch) {
            git.repository.exactRef(Constants.R_HEADS + branch.simpleName)
        } else {
            null
        }

        if (localRef != null) {
            // The remote branch already has a local branch (creating it again would fail with "ref already exists"):
            // check the local one out and bring the commits of the remote branch that it's missing
            git.checkout()
                .setName(branch.simpleName)
                .call()

            val remoteRef = git.repository.exactRef(branch.name)

            if (remoteRef != null && remoteRef.objectId != localRef.objectId) {
                // Fast-forward only: if the local branch has commits of its own (it's ahead or both have diverged)
                // nothing is merged, it's just checked out and can be pulled/pushed as usual
                git.merge()
                    .include(remoteRef)
                    .setFastForward(MergeCommand.FastForwardMode.FF_ONLY)
                    .call()
            }
        } else {
            git.checkout().apply {
                setName(branch.name)
                if (isRemoteBranch) {
                    setCreateBranch(true)
                    setName(branch.simpleName)
                    setStartPoint(branch.name)
                    setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.SET_UPSTREAM)
                }
                call()
            }
        }

        Unit
    }
}