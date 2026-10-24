# Daiji — repo rules

## Workflow

1. Before starting a feature, fetch the main branch: `git fetch origin`.
2. Work on every feature in its own git worktree, branched from `origin/HEAD`.
   Never commit directly on the main branch.
3. Verify the change compiles: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew -q :app:compileDebugKotlin`
   (Gradle 8.14 rejects newer JDKs; `local.properties` is gitignored, copy it from the main checkout).
4. Commit, push the branch, and open the PR with `gh pr create` against the main branch.
5. After you open the PR, ask a subagent to review it. Fix what it finds, then merge the PR.

## Screenshots in PRs

A PR that changes the UI carries a screenshot in its description.

1. Capture from the emulator: `adb exec-out screencap -p > <screen>.png`.
   A fixture library of zero-byte files with No-Intro names
   (`Chrono Trigger (USA).sfc`) is enough: scanner, databases and thumbnails match by name.
2. Push it to the orphan `pr-assets` branch under `<pr-branch>/<screen>.png`:
   `git worktree add ../daiji-pr-assets pr-assets`. Never merge or rebase that branch.
3. Embed it: `![<screen>](https://raw.githubusercontent.com/el-ente/daiji/pr-assets/<pr-branch>/<screen>.png)`.
