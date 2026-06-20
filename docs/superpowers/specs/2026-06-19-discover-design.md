# Podcast Discovery — Design Spec

**Date:** 2026-06-19  
**Branch:** feature/discover  
**Status:** Awaiting implementation

---

## Overview

Replace the Discover tab stub with a podcast search screen. Users type a keyword, the app queries iTunes Search API and Podcast Index API in parallel, merges and deduplicates results by feed URL, and displays a list. Tapping a result shows a detail screen with a Subscribe button that reuses the existing `PodcastRepository.addPodcast(feedUrl)` flow.

---

## Data Model

A new lightweight data class — not a Room entity — used only within the search flow:

```kotlin
data class PodcastSearchResult(
    val feedUrl: String,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val description: String?,
    val isSubscribed: Boolean = false
)
```

`isSubscribed` is populated by the repository checking `feedUrl` against the existing `podcasts` table. No DB writes occur until the user taps Subscribe on the detail screen.

---

## Network & Repository Layer

### New: `SearchApi`

Injected via Hilt; reuses the existing singleton `OkHttpClient` from `NetworkModule`.

**`searchItunes(query: String): List<PodcastSearchResult>`**
- `GET https://itunes.apple.com/search?media=podcast&term={query}&limit=20`
- No auth required.
- Parse response JSON with Moshi. Key fields: `feedUrl`, `collectionName`, `artistName`, `artworkUrl600`, `description`.

**`searchPodcastIndex(query: String): List<PodcastSearchResult>`**
- `GET https://api.podcastindex.org/api/1.0/search/byterm?q={query}`
- Auth headers required on every request:
  - `X-Auth-Key`: API key
  - `X-Auth-Date`: Unix timestamp (seconds) as string
  - `Authorization`: SHA-1 HMAC of `(apiKey + apiSecret + timestamp)`, hex-encoded
- Key fields: `url` (feed URL), `title`, `author`, `artwork`, `description`.

**API key storage:** `PODCAST_INDEX_KEY` and `PODCAST_INDEX_SECRET` defined in `local.properties`, injected as `BuildConfig` fields via `build.gradle`. Never committed to source control.

### New: `SearchRepository`

```kotlin
suspend fun search(query: String): List<PodcastSearchResult> {
    val (itunesDeferred, piDeferred) = coroutineScope {
        async { runCatching { searchApi.searchItunes(query) }.getOrElse { emptyList() } } to
        async { runCatching { searchApi.searchPodcastIndex(query) }.getOrElse { emptyList() } }
    }
    val merged = merge(itunesDeferred.await(), piDeferred.await())
    return merged.map { it.copy(isSubscribed = podcastDao.existsByUrl(it.feedUrl)) }
}
```

**Deduplication:** normalize feed URLs before comparing — lowercase, strip trailing slash, strip `www.`, treat `http`/`https` as equivalent. iTunes result is preferred when a duplicate exists (higher-quality artwork URLs).

**Podcast Index key absent:** if `BuildConfig.PODCAST_INDEX_KEY` is blank, `searchPodcastIndex` returns `emptyList()` immediately. iTunes results still display; no error shown to the user.

### New DAO method

```kotlin
@Query("SELECT COUNT(*) > 0 FROM podcasts WHERE feedUrl = :feedUrl")
suspend fun existsByUrl(feedUrl: String): Boolean
```

---

## UI Layer

All new files live in `ui/discover/`.

### `DiscoverViewModel`

```kotlin
sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    data class Success(val results: List<PodcastSearchResult>) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}
```

- `searchQuery: MutableStateFlow<String>` — updated on each keystroke.
- Debounce 400 ms; ignore queries shorter than 2 characters.
- On debounced query: emit `Loading`, call `searchRepository.search(query)`, emit `Success` or `Error`.

### `DiscoverScreen`

- `SearchBar` (Material3) or plain `TextField` with search icon at the top.
- Below the search bar, switches on `SearchUiState`:
  - **Idle:** prompt text — "Search for podcasts"
  - **Loading:** centered `CircularProgressIndicator`
  - **Success (empty list):** "No podcasts found for '{query}'"
  - **Success (non-empty):** `LazyColumn` of `SearchResultRow`
  - **Error:** error message + "Retry" button

### `SearchResultRow`

Matches the episode list row proportions:
- 56 dp artwork thumbnail (rounded corners, `shapes.small`), placeholder = `ic_podcast_placeholder`
- Subscribed indicator: small checkmark icon overlaid at bottom-right of artwork (same style as the cast indicator in the player)
- Right of artwork: podcast title (`titleMedium`, bold) + author (`bodySmall`)
- Tap → navigate to `PodcastDetailScreen`

### `PodcastDetailScreen`

Full-screen destination (fits existing `NavHost` pattern). Contains:
- Large artwork image
- Title (`headlineSmall`, bold)
- Author (`bodyMedium`)
- Description (`bodySmall`, scrollable, max ~5 lines before scroll)
- Subscribe button:
  - Not subscribed: filled `Button("Subscribe")` — calls `PodcastRepository.addPodcast(feedUrl)`, shows loading indicator on button while in-flight, navigates back to results on success
  - Already subscribed: outlined `Button("Subscribed")`, disabled

---

## Error Handling & Edge Cases

| Scenario | Behaviour |
|---|---|
| Both APIs fail | `Error` state with "Search unavailable — check your connection." + Retry button |
| One API fails | Other API's results used silently; no error shown |
| Empty results | "No podcasts found for '{query}'" in place of list |
| Query < 2 chars | Search not triggered; stay in `Idle` state |
| Podcast Index key blank | Branch skipped; iTunes-only results |
| Subscribe while offline | `PodcastRepository` throws; snackbar shows error message on detail screen |
| Duplicate feed URL | Dedup by normalized URL; iTunes result wins |

---

## Files Changed / Created

| Path | Change |
|---|---|
| `data/network/SearchApi.kt` | New — iTunes + Podcast Index search methods |
| `data/repository/SearchRepository.kt` | New — parallel search, merge, dedup, isSubscribed |
| `data/db/dao/PodcastDao.kt` | Add `existsByUrl()` |
| `data/di/NetworkModule.kt` | Bind `SearchApi` |
| `ui/discover/DiscoverViewModel.kt` | New |
| `ui/discover/DiscoverScreen.kt` | New — search bar + results list |
| `ui/discover/SearchResultRow.kt` | New — result row composable |
| `ui/discover/PodcastDetailScreen.kt` | New — detail + subscribe |
| `ui/PodcastNavGraph.kt` | Wire Discover tab + detail destination |
| `app/build.gradle` | Add `PODCAST_INDEX_KEY` / `SECRET` BuildConfig fields |
| `local.properties` | Add key/secret (not committed) |
| `.gitignore` | Ensure `local.properties` is ignored (already is) |

---

## Out of Scope

- Browsing by category or trending charts
- Episode previews from the detail screen
- Unlistened episode count badge (separate backlog item)
