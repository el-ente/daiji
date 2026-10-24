# Daiji — repo rules

## Workflow

1. Before starting a feature, fetch the main branch: `git fetch origin`.
2. Work on every feature in its own git worktree, branched from `origin/HEAD`.
   Never commit directly on the main branch.
3. Verify the change compiles: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew -q :app:compileDebugKotlin`
   (Gradle 8.14 rejects newer JDKs; `local.properties` is gitignored, copy it from the main checkout).
4. Commit, push the branch, and open the PR with `gh pr create` against the main branch.
