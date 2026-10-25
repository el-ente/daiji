# Daiji — repo rules

## Workflow

1. Before starting a feature, fetch the main branch: `git fetch origin`.
2. Work on every feature in its own git worktree, branched from `origin/HEAD`.
   Never commit directly on the main branch.
3. Verify the change compiles: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew -q :app:compileDebugKotlin`
   (Gradle 8.14 rejects newer JDKs; `local.properties` is gitignored, copy it from the main checkout).
4. Commit, push the branch, and open the PR with `gh pr create` against the main branch.
5. After you open the PR, ask a subagent to review it. Fix what it finds, then merge the PR.

## Navigation tests

A PR that changes a screen's layout or focus is tested with the D-pad on the emulator before it is opened.

1. Ask an interaction designer subagent for the screen's focus map: where each key goes from each element,
   and where focus starts. The test checks the screen against that map.
2. Drive the screen with `adb shell input keyevent KEYCODE_DPAD_UP` (and `_DOWN`, `_LEFT`, `_RIGHT`, `_CENTER`, `KEYCODE_BACK`),
   with a screenshot after each key to see where focus landed.
3. Before testing keys, check that `adb shell dumpsys accessibility` shows `Crashed services:{}`. A reinstall or force stop
   leaves Daiji's key service crashed but still filtering keys, so every press lags about 500 ms and turns into a long press.
   Daiji turns it off and on about 3 s after it opens; if it stays crashed, that repair is broken.
4. Every focusable element must be reachable, and Up, Down, Left, Right, OK and Back must land where the user expects
   from each of them, as the focus map says. A key that does nothing or opens something by surprise (like the keyboard) is a bug.
5. List the paths tested and their result in the PR description.

## Screenshots in PRs

A PR that changes the UI carries a screenshot in its description.

1. Capture from the emulator: `adb exec-out screencap -p > <screen>.png`.
   A fixture library of zero-byte files with No-Intro names
   (`Chrono Trigger (USA).sfc`) is enough: scanner, databases and thumbnails match by name.
2. Push it to the orphan `pr-assets` branch under `<pr-branch>/<screen>.png`:
   `git worktree add ../daiji-pr-assets pr-assets`. Never merge or rebase that branch.
3. Embed it: `![<screen>](https://raw.githubusercontent.com/el-ente/daiji/pr-assets/<pr-branch>/<screen>.png)`.
