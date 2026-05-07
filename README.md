# BetterLoginGui (BLG)

BetterLoginGui is a Paper plugin that provides a modern login/register experience using dialogs, with optional server-rules acceptance before players continue.

It integrates with **AuthMe** for account status and authentication commands while keeping all credential handling inside AuthMe.

## Features

- Dialog-based login and register flow
- Auto-open login/register on join (optional)
- Optional rules acceptance step with wait timer
- Multi-page rules support for long rule sets
- `/updaterules` command to reload rules and invalidate old acceptances when rules change
- Safe fallback messaging when the Dialog API is unavailable

## Requirements

- Paper server (API version `1.21`)
- Java 17+ runtime
- AuthMe plugin (soft dependency, but required for full auth flow)

## Installation

1. Download the BLG JAR from your release/artifact output.
2. Put it in your server `plugins/` folder.
3. Start the server once to generate default files.
4. Edit:
   - `plugins/BetterLoginGui/config.yml`
   - `plugins/BetterLoginGui/rules.txt`
5. Restart the server (or use `/updaterules` after changing `rules.txt`).

## Commands

### Player-facing

- `/openlogin` — opens the login dialog
- `/openregister` — opens the register dialog
- `/openauto [player]` — opens the right dialog for yourself, or forces login dialog for a target player

### Admin

- `/updaterules` — reloads `rules.txt`, creates `rules.txt.old`, and resets acceptance cache if content changed

### Internal commands

BLG also registers internal `blg_*` commands used by dialog buttons. These are not intended for manual use.

## Permissions

- `blg.openlogin` (default: true)
- `blg.openregister` (default: true)
- `blg.openauto` (default: true)
- `blg.openauto.others` (default: op)
- `blg.internal` (default: true)
- `blg.admin` (default: op, includes all BLG permissions)

## Configuration Notes

- `autojoinlogingui` / `auto-join-login-gui` / `auto-open-on-join`: controls auto-open behavior on join.
- `join-dialog-delay-ticks`: delay before opening dialogs after join.
- `rules.enabled`: enables rules flow before auth dialogs.
- `rules.wait-seconds`: minimum time before accept/leave is allowed.
- `rules.pages.enabled`: enables paginated rule display.
- `rules.pages.lines-per-page`: lines shown per rules page.

## Rules File

`rules.txt` supports:

- one rule per line
- `&` color codes
- comment lines starting with `#` (ignored)

When rules content changes and `/updaterules` is run, previously accepted rules are invalidated so players must accept again.
