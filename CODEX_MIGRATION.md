# CloudStreamHub — Antigravity → Codex migration

This package converts the repository's AI-assistant configuration to a Codex-native layout.

## Files in this package

```text
AGENTS.md
.codex/
  config.toml
  agents/
    repo-mapper.toml
    provider-debugger.toml
    implementer.toml
    verifier.toml
    reviewer.toml
    fixer.toml
  rules/
    default.rules
.agents/
  skills/
    debug-provider/SKILL.md
    add-provider/SKILL.md
    review-working-tree/SKILL.md
    implement-approved-plan/SKILL.md
    verify-implementation/SKILL.md
    fix-approved-review/SKILL.md
```

## Install

Extract the package into the CloudStreamHub repository root and overwrite the matching `AGENTS.md` / skill files.

After extraction, restart the Codex session so `AGENTS.md`, project config, agents, rules, and skills are reloaded.

The old `.agents/agents/` folder is not used by Codex custom-agent discovery. After you confirm the Codex setup works, you may remove that legacy Antigravity-only folder.

## Verify discovery

From the repository root:

```powershell
codex --ask-for-approval never "Summarize the active CloudStreamHub project instructions, available custom agents, and available project skills."
```

Inside an interactive Codex session, `/skills` should show the project skills.

## Verify command policy

Example:

```powershell
codex execpolicy check --pretty --rules .codex/rules/default.rules -- git push
```

Expected decision: `forbidden`.

For a read-only command:

```powershell
codex execpolicy check --pretty --rules .codex/rules/default.rules -- git status --short
```

Expected decision: `allow`.

## How to use it

Normally, just describe the software task.

Examples:

- `FilmMakinesi provider çalışmıyor. Kök nedeni bul, düzelt, test et ve doğrula.`
- `Yeni provider ekle: ...`
- `Bu branch'teki değişiklikleri review et.`
- `Health issue mantığında aynı state için tekrar yorum atılıyor mu kontrol et ve gerekiyorsa düzelt.`

The root `AGENTS.md` instructs the main Codex agent to route work automatically.

You can force a reusable workflow when needed:

- `$debug-provider`
- `$add-provider`
- `$review-working-tree`
- `$implement-approved-plan`
- `$verify-implementation`
- `$fix-approved-review`

You do not need to select a custom subagent manually for normal work.

## Model policy

This package intentionally does **not** pin a model or reasoning effort in custom-agent TOML files.

Reason: the agents inherit the current Codex model/reasoning configuration, so the project does not break or become stale when you change models later.
