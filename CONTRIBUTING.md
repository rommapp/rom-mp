# Contributing to RomMP

RomMP is a gamepad-first Android music player for the game soundtracks in a
self-hosted RomM library. PRs are welcome, including AI-assisted ones. What
matters is that you own what you submit, meaning you have personally reviewed
it and understand what it does.

## Building

You need JDK 17 or newer and an Android SDK with platform 35. The Gradle
wrapper handles everything else.

```
./gradlew assembleDebug
```

The debug variant installs alongside a release build, since it carries the
`.debug` application ID suffix. Point it at any RomM server that exposes the
music API.

Useful tasks:

```
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug            # Android Lint
./gradlew detekt               # Kotlin static analysis
```

### Release signing is maintainer-only

Release builds are signed from a `keystore.properties` at the repository root
alongside the keystore it names. Both files are gitignored and are not shared,
so contributors build and test the debug variant. `assembleRelease` and
`bundleRelease` fail with an explanation when `keystore.properties` is absent
rather than silently falling back to the debug key, and CI never runs a release
task for the same reason. Nothing in a PR should need one.

## Code style

- `.editorconfig` sets the formatting baseline: four-space indent, LF endings,
  a final newline, and a 120 column limit in Kotlin.
- `./gradlew detekt` is the style gate. Run it before pushing.
- Self-documenting code over commentary. A KDoc block above a non-obvious
  public declaration is welcome; narration inside a function body is not.
  Rationale for a change belongs in the PR description.
- User-facing text goes through `res/values/strings.xml`, never straight into a
  `text =` or `contentDescription =` argument. Lint treats translation gaps as
  errors, and that only holds if nothing bypasses the catalogue.
- Every interactive element has to work with touch and with a gamepad. A
  control that only responds to one of the two is incomplete.
- File, network, and database work runs off the main thread. A spinner over a
  blocked main thread is a bug, not a loading state.

## What CI checks

Every push to `main` and every PR against it runs four jobs, all on debug
variants:

| Job | Task | Artifact |
| --- | --- | --- |
| build | `assembleDebug` | the debug APK |
| test | `testDebugUnitTest` | test reports |
| lint | `lintDebug` | the lint HTML report |
| detekt | `detekt` | detekt reports |

### Do not regenerate the lint baseline

`app/lint-baseline.xml` holds 77 untriaged findings from before the baseline
was created. It exists so that new warnings stand out, and regenerating it
would fold your new warning into the pile and hide it. Fix the finding, or
suppress that single occurrence with a reason. Shrinking the baseline by fixing
old findings is a welcome PR on its own; growing it is not.

## Opening a PR

- Branch from `main` and keep the PR to one concern. A refactor bundled with a
  behavior change is two PRs.
- Fill in the PR template. The behavior-change inventory, the hot-path
  declaration, and the testing evidence are the parts reviewers read first. A
  PR with "None" under behavior changes is making a claim that gets checked.
- Test against a real RomM server on a real device and say which server version
  and which device in the testing evidence. Most bugs in a client app are
  server-specific or device-specific, and neither is visible from the code.
- Squash-merge is the house style. Keep PR titles in lowercase-imperative
  scope-prefixed form, such as `player: ...`, `library: ...`, `auth: ...`.
- If you used AI to help, say so in the PR along with how much it did. That
  just tells the reviewer where to focus. Same policy as the rest of the
  rommapp org.
