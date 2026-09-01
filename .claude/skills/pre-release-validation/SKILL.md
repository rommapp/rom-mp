---
name: pre-release-validation
description: Run local checks before pushing code or cutting a release. Use before commits, releases, or when validating code quality. Catches issues before they reach the remote.
---

# Pre-Release Validation

Local checks for RomMP before pushing to `rommapp/rom-mp`.

---

## When to Use This Skill

**Automatically invoke:**
- Before creating a release of any type
- Before pushing significant changes
- After completing a feature implementation

**Manual invocation when:**
- User asks to "run checks" or "validate"
- Auditing code quality

---

## What This Project Actually Has

Read this before running anything, so a green result is not mistaken for
coverage it does not have.

- **No unit tests exist.** `app/src/test` and `app/src/androidTest` are empty.
  `testDebugUnitTest` reports success because it runs zero tests. Treat a
  passing test task as "no signal", never as "verified". The junit and mockk
  dependencies are declared in `app/build.gradle.kts` but nothing uses them.
- **No CI.** There is no `.github/workflows`. Every check in this file is
  local-only and nothing re-runs them on a pull request. Until that changes,
  the maintainer's machine is the only gate.
- **Single APK output.** No ABI splits are configured, so `assembleRelease`
  produces exactly one artifact at
  `app/build/outputs/apk/release/rommp-<versionName>-release.apk`.
- **Release signing is guarded.** `verifyReleaseSigningConfig` in
  `app/build.gradle.kts` fails `assembleRelease` and `bundleRelease` when
  `keystore.properties` is missing, instead of silently falling back to the
  debug key. A release build that fails with that message needs the keystore
  restored, not a workaround.

Timing on a warm daemon: `assembleDebug` is seconds, `assembleRelease` is about
55 seconds including R8, and the release APK lands around 2.9 MB. A cold daemon
adds a couple of minutes. These are small numbers by Android standards; if a
build suddenly takes many minutes, something is wrong rather than slow.

---

## Validation Tiers

### Tier 1: Quick Check
Fast validation for small changes.

```bash
./gradlew lintDebug compileDebugKotlin
```

### Tier 2: Standard Check
Before pushing.

```bash
./gradlew lintDebug compileReleaseKotlin
```

### Tier 3: Full Check
Before any release.

```bash
./gradlew lint
./gradlew assembleRelease
```

Run Tier 3 as two separate invocations. Trailing a release build behind lint in
one daemon is the configuration most likely to exhaust the heap during R8.

---

## Validation Checklist

### Code Quality
```
[ ] lint reports 0 errors (warnings reviewed, not just counted)
[ ] No new entries needed in the lint baseline
[ ] No TODO/FIXME introduced in committed code
```

### Build
```
[ ] Debug build succeeds
[ ] Release build succeeds and is release-signed, not debug-signed
[ ] APK size is in the expected range (about 2.9 MB at 0.3.1)
```

### Git Hygiene
```
[ ] Working tree clean, nothing untracked that belongs in the repo
[ ] No secrets staged (keystore.properties, release.keystore, local.properties)
[ ] Commit messages describe behaviour, not implementation steps
```

### Secrets Sweep
The signing material lives in the working tree and is gitignored. Confirm it
stayed that way before any push:

```bash
git ls-files | grep -iE 'keystore\.properties$|\.keystore$|local\.properties' || echo "clean"
```

`keystore.properties.example` is expected and is not a hit for that pattern.

### Release Signing Proof
A release APK signed with the debug key installs fine locally and cannot be
upgraded later. Verify the signer rather than assuming:

```bash
apksigner verify --print-certs \
  app/build/outputs/apk/release/rommp-*-release.apk | grep -i 'Signer #1 certificate DN'
```

The debug key reports `CN=Android Debug`. Anything else is the real key.

---

## The Lint Baseline

`app/lint-baseline.xml` holds 77 findings captured when lint was first adopted,
at 0.3.1. Two things follow from that.

Anything lint reports now is NEW and worth reading, because everything that
existed at adoption is already suppressed.

The baseline contents are NOT triaged. Unlike the annotated baseline in
argosy-launcher, nothing here has been reviewed and classified as a false
positive or an accepted deviation. It is a snapshot of debt, not a curated
exemption list. Do not cite "it is in the baseline" as evidence that a finding
is benign.

Regenerate the baseline only as a deliberate act after clearing real findings:
delete the file and re-run `lintDebug`. Never regenerate it to silence a warning
that just appeared, which is exactly the case the baseline is supposed to catch.

---

## Single Invocation, Never Parallel

Never run concurrent Gradle invocations against this project, in separate
terminals or with `&`. Concurrent invocations corrupt KSP caches, and this
project uses KSP for Hilt, Room, and Moshi. Pass multiple tasks to one
invocation and let Gradle schedule them.

Do not reach for `./gradlew clean` when a build misbehaves. It forces a full
cold rebuild for no diagnostic gain. Invalidate the specific stale output
instead.

---

## Output Interpretation

### Lint
```
0 errors, N warnings   PASS, proceed
N errors, ...          BLOCKING, fix before release
```

### Tests
```
BUILD SUCCESSFUL       NO SIGNAL, there are no tests
```
Report this honestly in the validation report rather than as a pass.

### Build
```
BUILD SUCCESSFUL       PASS
BUILD FAILED           BLOCKING
```

If `:app:minifyReleaseWithR8` dies with `java.lang.OutOfMemoryError`, the daemon
heap in `gradle.properties` (`-Xmx4096m`) was not enough for that build. Pass a
larger heap on the command line rather than editing the repo file:

```bash
./gradlew assembleRelease -Dorg.gradle.jvmargs="-Xmx8g -XX:+UseParallelGC -Dfile.encoding=UTF-8"
```

This has not been needed at the current app size. If it starts being needed,
that is a signal the app grew, not a step to add to the routine.

---

## Validation Report Template

```markdown
## Pre-Release Validation Report

### Lint
- Status: PASS/FAIL
- Errors: 0
- Warnings: N (new since baseline: N)

### Tests
- Status: NO SIGNAL (no test sources in this project)

### Build
- Status: PASS/FAIL
- APK: app/build/outputs/apk/release/rommp-X.Y.Z-release.apk
- Size: X.X MB
- Signer: <certificate DN>

### Verdict
Ready for release: YES/NO
Blockers: [list if any]
```

---

## Integration with the Release Skill

The release skill invokes this one at step 2 and does not proceed to the version
bump until Tier 3 is clean. See `.claude/skills/release/SKILL.md`.
