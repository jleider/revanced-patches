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

The full, cited style guide (from 328 of the maintainer's review comments and the merged BaconReader
patches) is `STYLE.md`; our patches follow it, with the deliberate deviations listed there.

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
- `.github/workflows` and `.releaserc` changes (fork releases are `X.Y.Z-sync.N` from `main`), and the
  `patches/build.gradle.kts` changes for unsigned/fork publishing.
- Probably "Enable debug logging" (developer tooling, `use = false`); include only with a justification.

## Suggested split

1. **Core fixes**, one commit per patch: Reddit image URLs, Imgur, Gfycat, Reddit video playback, link previews,
   login redirect, combined subreddit feeds, random subreddit, other discussions, DeviantArt embeds, Tumblr,
   HTTPS upgrade, Reddit status alert, Hide restore comment.
2. **Comments and galleries**, superseding the open MRs !6999 (Bryan Ulate: `Fix loading more comments` +
   `Fix galleries`) and !7000 (0naught: galleries only). See "Superseding !6999 and !7000" below.
3. **Fix YouTube player** with its "Block YouTube ads" option, on its own, with the option clearly described.
4. **Disable telemetry** and the remaining small patches.

## Before opening an MR (checklist)

- Rebase onto current upstream `dev` (it has moved: e.g. the shared `spoofClientPatch` signature and the shared
  Redgifs code changed), resolve conflicts, rebuild, and rerun `scripts/verify_patch.sh` and the e2e suite here.
- Patch names, `val`s and file names already match, and the explicit defaults are gone (done November 2026);
  keep it that way for new patches.
- The branch is rebased onto upstream `dev` (November 2026); rebase again before opening an MR.
- `patches/api/patches.api` lists every Sync patch getter. This checkout has no `apiDump` or ktlint task, so it
  was updated from the compiled classes (`javap`); if upstream `dev` has those tasks, run them there instead.
- Build the MR branch from `git cherry-pick`/`git checkout <fork> -- <paths>` of only the patch and extension
  files, so none of the fork-only paths above come along.
- Write the MR description as a list of user-visible fixes, one line each.
- End each MR description with a short "Testing" paragraph linking the fork's tooling
  (<https://github.com/jleider/revanced-patches/tree/main/sync-for-reddit#developing>): unit tests on recorded
  responses, an e2e scenario per patch with a negative control, the reachability check and the ART verifier.
  The draft PRs on the fork (`upstream/sync-*` branches) already carry it.

## Superseding !6999 and !7000

All three implementations use the same idea: `/comments/<post>` for galleries, comment permalinks for
"load more". Ours is offered as the consolidated version, keeping everything useful from theirs, with credit.

Taken from them:
- !6999 (Bryan Ulate): the patch name `Fix loading more comments` (already through review there) and the
  permalink approach (which they credit to RedReader).
- !7000 (0naught): post-id validation, the oauth.reddit.com host check, and "leave malformed or empty
  responses to Sync's own error handling" for galleries; their six gallery test cases, adapted into
  `ExtensionHarness.gallery()` (with the maintainer's review points addressed: explained ids and the
  non-ASCII caption, a static fixture).
- 0naught's review of !6999 found a partial-failure bug (a failed extra request silently drops a comment and
  its retry row). Ours doesn't have it: every request is all-or-nothing and a failed body goes back to Sync
  unchanged, so the "load more" row survives; `moreCommentsFailureKeepsRow()` tests it.

Where ours differs:
- One request per "load more" row in the common case (the parent thread, `limit=500`), then one permalink
  at a time only for comments that weren't in it; !6999 issues one request per hidden comment (up to 100,
  8 in parallel), which can hit Reddit's ~100 requests/minute limit on big threads.
- Uses Sync's own Volley/OkHttp stack (headers, HTTPS upgrade, logging) instead of a separate
  HttpURLConnection, and keeps Sync's "load N more" counts right.
- Covered by unit tests on recorded responses and emulator scenarios, including a very large thread.

Etiquette: before opening ours, comment on !6999 and !7000 with a link, thank the authors, say what was taken
from each and why ours is offered as the consolidated version, and offer to close ours instead if the
maintainer prefers theirs. Commits that include their work carry `Co-authored-by:` trailers:

    Co-authored-by: Bryan Ulate <bryanulatec@gmail.com>
    Co-authored-by: 0naught <2214854-0naught@users.noreply.gitlab.com>

(both from their public MR commits).

### Draft MR description (galleries and comments)

> Supersedes !6999 and !7000, consolidating them. Thanks to Bryan Ulate and 0naught: this keeps the patch
> name and permalink approach from !6999 and the gallery validation and test cases from !7000, and avoids
> the partial-failure case raised in the !6999 review.
>
> - Fix galleries: galleries open again (Reddit blocks `/api/info`; the post is read from `/comments/<id>`).
> - Fix loading more comments: "View more" works again with usually one request per row instead of one per
>   comment, so large threads don't hit Reddit's rate limit. A failed request keeps the row for a retry.

## Other Sync merge requests (checked November 2026)

- !6975 (ALSchwalm), `Fix swipe back gesture animation`: Samsung One UI 7+ black screen during swipe-to-return.
  No overlap with this branch and nearly approved; leave it to its author, don't supersede.
  Its review shows the maintainer's current preferences, worth applying to ours: `returnEarly()` instead of
  hand-written early returns; `gettingFirstMethodDeclaratively("string") { … }` and
  `getting { firstMethodDeclaratively { … } } using { otherMethod }` instead of manual class/method searches;
  a resource patch that only supports a bytecode patch is unnamed and pulled in with `dependsOn()`;
  Kotlin sequences over `getElementsByTagName` loops; descriptions such as "Disables transient blur and
  flickering on new Android versions."
- !5725 (wchill, merged) is the Redgifs fix our `Fix Redgifs API` change extends; !5692 (closed) was the
  alternative it replaced. Mention !5725 in the MR that touches it.
