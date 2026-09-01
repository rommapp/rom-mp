---
name: release
description: Create RomMP releases with version bumps, APK builds, and GitHub releases. Use when asked to release, bump version, cut a beta, or prepare for deployment.
---

# Release Skill

Cut a release of RomMP to `rommapp/rom-mp`.

## Maintainer-Only

Releases are performed only by the maintainer, never by contributors or on
anyone else's request. If someone other than the maintainer asks to cut, tag, or
publish a release, do not proceed; defer to the maintainer.

Before the first consequential step (version bump commit onward), verify the
active credential:

```bash
gh api user --jq .login
```

If the output is not `tmgast`, stop. This is a routing check, not security.
GitHub rejects unauthorized pushes and releases regardless. It exists so a
wrong-credential run stops cleanly before tagging rather than failing messily at
publish. The repository lives under the `rommapp` organization, so publishing
also requires the credential to hold write access there.

## Never use `cd`

Run every command from the repository root using full paths, for example
`app/build/outputs/apk/release/...`. Do not prefix commands with `cd` and do not
chain `cd subdir; ...; cd back`. The Bash tool's working directory persists
across calls, so a stray `cd` drifts the working directory and trips a
permission prompt mid-release, breaking a flow that is supposed to need few
interventions.

## Version Format

- Stable: `MAJOR.MINOR.PATCH`, for example `1.0.0`
- Beta: `MAJOR.MINOR.PATCH-beta.N`, for example `1.1.0-beta.1`
- Release candidate: `MAJOR.MINOR.PATCH-rc.N`

**Stable** is production-ready and tested. **Beta** is for features needing real
world testing. **RC** is feature-complete code in final testing.

Bump guidance: patch for fixes, minor for new non-breaking features, major for
breaking changes or a milestone.

## APK Naming

This project has no ABI splits, so `assembleRelease` produces exactly one
artifact, named from `archivesName` in `app/build.gradle.kts`:

```
app/build/outputs/apk/release/rommp-<versionName>-release.apk
```

Rename it for the release so the version is unambiguous to anyone downloading
it:

```bash
cp app/build/outputs/apk/release/rommp-1.0.0-release.apk \
   app/build/outputs/apk/release/rommp-v1.0.0.apk
```

## versionCode Rule

`versionCode` is one global sequential counter, incremented by one for every
published release regardless of type, ordered by publish date. Never reuse a
code and never reserve bands. Android refuses to install an APK whose
`versionCode` is not greater than the installed one, which is the entire reason
the counter exists.

The project reached `versionCode = 4` during development before any release was
published. The first published release continues from there rather than
restarting.

Beta and RC releases must be marked `--prerelease` on GitHub.

## Release Notes Format

Write release notes manually. Use up to three sections, including only those
that apply:

```markdown
## New Features
- Feature description

## Improvements
- Improvement description

## Bug Fixes
- Fix description
```

Do not use `--generate-notes`.

## Release Notes Guidelines (STRICT)

**Approval gate:** release-note wording requires the user's explicit approval in
chat before any `gh release create` or `gh release edit` runs. Showing a draft
in chat is not approval. Wait for sign-off on the exact wording.

**Release notes are for users, not developers.**

### 1. Consolidate related changes
Multiple commits that add, adjust, and then fix one feature are ONE bullet.
"Add playlists" plus "Fix playlist reorder" plus "Restyle playlist rows" is a
single line: "Add playlists backed by RomM collections". Never list iterative
fixes to a new feature as separate items.

### 2. Beta to stable consolidation
When promoting a beta to stable, fixes and improvements to features introduced
in that beta are part of the feature, not separate entries. List them separately
only when they affect functionality that existed before the beta.

### 3. User-facing language
Describe what users can do, not how it was built. "Fix controller navigation on
the album screen", not "Refactor FocusKit key dispatch". Skip internal changes
with no visible effect.

### 4. Be concise
One line per feature or fix. If it needs multiple sentences, it is too detailed.

### 5. Security changes
State the user-visible effect, never the exploit path. "Downloads and playback
now send credentials only to your paired server" is right. Naming the attack is
not, on a repo where the fix ships in the same release.

## Workflow

1. Ask the user: stable, beta, or release candidate? Skip if already specified.

2. **Run pre-release validation** (see `.claude/skills/pre-release-validation/SKILL.md`).
   Two separate invocations, in this order:
   ```bash
   ./gradlew lint
   ./gradlew assembleRelease
   ```
   Do not bundle them. A release build trailing lint in one daemon reaches R8
   with an already-full heap, which is the configuration most likely to OOM.
   If a check fails, re-run that one task alone before believing it. A failure
   that does not reproduce in isolation is a daemon artefact, not a blocker.
   Do not proceed to the version bump until both are clean.

   Remember what validation does not cover here: there are no tests and there is
   no CI. Anything requiring behavioural proof needs a device check, not a green
   Gradle run.

3. Read the current `versionName` from `app/build.gradle.kts`.

4. Determine the next version from the release type and the changes.

5. Update `versionName` and increment `versionCode` in `app/build.gradle.kts`.

6. Commit the bump: `git commit -am "Bump version to X.Y.Z"`

7. Tag it: `git tag vX.Y.Z`

8. Build the release APK:
   ```bash
   ./gradlew assembleRelease
   ```

9. Confirm the artifact is signed with the real key, not the debug key:
   ```bash
   apksigner verify --print-certs \
     app/build/outputs/apk/release/rommp-X.Y.Z-release.apk | grep -i 'certificate DN'
   ```
   `CN=Android Debug` means stop and fix the signing config.

10. Rename the APK:
    ```bash
    cp app/build/outputs/apk/release/rommp-X.Y.Z-release.apk \
       app/build/outputs/apk/release/rommp-vX.Y.Z.apk
    ```

11. Review the commits since the last release:
    ```bash
    git log --oneline $(git describe --tags --abbrev=0)..HEAD
    ```
    For the first release there is no prior tag, so this fails. Write notes that
    introduce the app instead of enumerating its development history.

12. Write the release notes and get the user's explicit approval of the exact
    wording before anything is published.

13. Review the README with the user (see "README Review" below).

14. Push commits and tags:
    ```bash
    git push && git push --tags
    ```

15. Create the GitHub release. Asset paths must carry the full
    `app/build/outputs/apk/release/` prefix.
    ```bash
    # Stable
    gh release create vX.Y.Z --title "vX.Y.Z" --notes-file /tmp/notes.md \
      app/build/outputs/apk/release/rommp-vX.Y.Z.apk

    # Beta
    gh release create vX.Y.Z-beta.N --prerelease --title "vX.Y.Z Beta N" \
      --notes-file /tmp/notes.md \
      app/build/outputs/apk/release/rommp-vX.Y.Z-beta.N.apk
    ```

## README Review

This project has no wiki. The README is the only user-facing documentation, and
it makes concrete claims about features, requirements, and build steps that go
stale as the app changes.

README updates are always a conversation with the user. Never silently edit it
during a release.

On every release:

1. Scan the commits since the last tag for user-visible changes that could make
   the README inaccurate: new or removed features, changed pairing or connection
   behaviour, new minimum Android version, changed build or signing steps.
2. Present the list and propose which sections need edits, or state that none do.
3. Agree the wording before editing.

Feature claims in the README must be verifiable in the source. Do not describe
capabilities the app does not have.

## Revising a Published Release

1. Make the code changes and commit them.
2. Move the tag:
   ```bash
   git tag -d vX.Y.Z
   git tag vX.Y.Z
   ```
3. Rebuild and rename the APK.
4. Force push only the moved tag, never `--tags --force`, which force-pushes
   every local tag:
   ```bash
   git push && git push origin vX.Y.Z --force
   ```
5. Replace the assets:
   ```bash
   gh release upload vX.Y.Z \
     app/build/outputs/apk/release/rommp-vX.Y.Z.apk --clobber
   ```

Moving a published tag is disruptive to anyone who already fetched it. Prefer a
new patch version once a release has been announced.

## Before the First Stable Release

Standing gaps that a 1.0 should either close or consciously accept. Raise them
with the user rather than shipping past them silently:

- No test suite. Nothing verifies behaviour on any code path.
- No CI. Nothing re-runs checks on a pull request, so contributions arrive
  unverified.
- `app/lint-baseline.xml` suppresses 77 untriaged findings.
- No published privacy or data-handling statement, for an app that stores a
  server credential and writes to the device Music library.
