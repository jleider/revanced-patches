# Contributing these patches upstream

Notes for offering the Sync for Reddit work to ReVanced. Researched November 2026 from the public GitLab
project, its merge-request history and `CONTRIBUTING.md`; re-check before submitting, since this drifts.

## Where and how

- Upstream is **GitLab**: <https://gitlab.com/ReVanced/revanced-patches> (the GitHub repo was taken down by a
  DMCA notice in March 2026). Open merge requests against **`dev`**; MRs against `main` are closed unmerged.
- No CLA and no MR template. There is no CI on MRs, so maintainers build locally: the branch must build cleanly.
- MRs are squash-merged with a conventional commit title, e.g. `feat(Sync for Reddit): Add \`Fix Tumblr\` patch`
  or `fix(Sync for Reddit - Fix galleries): …`.
- Asking in the ReVanced Discord `#development` channel first is common and recommended for big MRs.
- One maintainer reviews and merges nearly everything (`oSumAtrIX` on GitLab); expect his style comments.

## What gets merged quickly

- Small, focused fixes merge in a day or two (e.g. the Redgifs fix for the Reddit clients).
- A large single-app MR can merge quickly when it is clean: one commit per patch, a clear description listing
  every user-visible fix, documented patches, `patches/api/patches.api` updated (the BaconReader MR with 16
  patches is the model).
- What stalls: unrelated commits in the branch, the wrong target branch, style problems needing many review
  rounds, and anything unlocking paid features. Ad blocking and telemetry-disabling patches are accepted
  (`CONTRIBUTING.md` explicitly welcomes ad blocking; a TikTok "Disable telemetry" patch was merged).

## Conventions reviewers enforce

- Patch name, the `val` name and the `.kt` file name match (`Fix Tumblr` → `val fixTumblrPatch` in `FixTumblrPatch.kt`).
- Descriptions say what the patch does, in third person present tense, ending with a period; not how it works.
- Fingerprints never use obfuscated names, and live in `Fingerprints.kt`. Prefer the declarative APIs
  (`composingFirstMethod`/`gettingFirstMethodDeclaratively` with `instructions()`), take indices from the match,
  use `returnEarly()` where it fits.
- Don't spell out defaults (`use = true`, `required = false`); no needless version pinning in `compatibleWith`.
- Extensions are Java. Comments end with a period. Format with ktlint.

## What to leave out of upstream MRs

These are fork-only and should not be in an upstream MR:

- `sync-for-reddit/` (scripts, e2e, fixtures, snapshots, this file, CLAUDE.md) and `.claude/`.
- `extensions/syncforreddit/src/test/` (JVM stubs and harnesses; upstream has no extension test tree).
- `.github/workflows` changes and the `patches/build.gradle.kts` changes for unsigned/fork publishing.
- Probably "Enable debug logging" (developer tooling, `use = false`); include only with a justification.

## Suggested split

1. **Core fixes**, one commit per patch: Reddit image URLs, Imgur, Gfycat, Reddit video playback, link previews,
   login redirect, combined subreddit feeds, random subreddit, other discussions, DeviantArt embeds, Tumblr,
   HTTPS upgrade, Reddit status alert, Hide restore comment.
2. **Comments and galleries**: other contributors had open MRs for `Fix loading more comments` and `Fix galleries`
   (November 2026). Compare and coordinate with them, or contribute to theirs, instead of opening a competing MR.
3. **Fix YouTube player** with its "Block YouTube ads" option, on its own, with the option clearly described.
4. **Disable telemetry** and the remaining small patches.

## Before opening an MR (checklist)

- Rebase onto current upstream `dev` (it has moved: e.g. the shared `spoofClientPatch` signature and the shared
  Redgifs code changed), resolve conflicts, rebuild, and rerun `scripts/verify_patch.sh` and the e2e suite here.
- Patch names, `val`s and file names already match, and the explicit defaults are gone (done November 2026);
  keep it that way for new patches.
- `patches/api/patches.api` lists every Sync patch getter. This checkout has no `apiDump` or ktlint task, so it
  was updated from the compiled classes (`javap`); if upstream `dev` has those tasks, run them there instead.
- Build the MR branch from `git cherry-pick`/`git checkout <fork> -- <paths>` of only the patch and extension
  files, so none of the fork-only paths above come along.
- Write the MR description as a list of user-visible fixes, one line each.
