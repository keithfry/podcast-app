# Chromecast Integration — Progress Log

## Completed

### MainActivity: FragmentActivity requirement
`MainActivity` changed from `ComponentActivity` → `AppCompatActivity` (which extends `FragmentActivity`). Required by `MediaRouteButton.showDialogForType` — crashes with `IllegalStateException` otherwise.

### PlaybackService: Session ID collision on player switch
`switchToPlayer()` was building a new `MediaLibrarySession` before releasing the old one. Both had the same default session ID (`""`), causing `IllegalStateException: Session ID must be unique`. Fixed by releasing old session first, then building new one.

### PlaybackService: CastPlayer requires mimeType
`CastPlayer.setMediaItem()` throws `IllegalArgumentException: The item must specify its mimeType`. ExoPlayer `MediaItem`s don't carry mimeType — added fallback to `audio/mpeg` when switching to `castPlayer` and `localConfiguration.mimeType` is null.

### Cast discovery: emulator limitation
Android TV emulator and phone emulator are on separate NAT networks — Cast mDNS discovery (UDP multicast 5353) does not cross NAT. Emulator-to-emulator Cast testing is not viable. Tested with physical Chromecast device instead.

### Scripts
- `scripts/tv.sh` — starts `Television_720p` AVD, builds, installs, launches app
- `scripts/device.sh local-phone` — starts/connects to `Medium_Phone_API_36.1` AVD, builds, installs, launches app
  - Fixed `bash -euo pipefail` silent exit caused by `[[ ]] && echo` pattern in pipe (false comparison exits 1, pipefail kills script). Changed to `if/fi`.
  - Fixed `emu avd name` returning trailing `OK` line corrupting AVD name match — strip with `grep -v '^OK' | xargs`.

## In Progress / Next

- Verify Cast session fully establishes after mimeType fix (physical device test)
- Handle non-MP3 mimeTypes (AAC, Opus) — detect from URL extension or stored episode metadata rather than hardcoding `audio/mpeg`
- Cast session resume on reconnect (`onSessionResumed`)
- UI: cast button state / active cast indicator
