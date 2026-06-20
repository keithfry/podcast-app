package com.frybynite.podcastapp.ui.discover

import com.frybynite.podcastapp.data.repository.PodcastRepository
import com.frybynite.podcastapp.data.repository.SearchRepository
import com.frybynite.podcastapp.domain.model.PodcastSearchResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val searchRepo = mockk<SearchRepository>(relaxed = true)
    private val podcastRepo = mockk<PodcastRepository>()

    private fun makeVm() = DiscoverViewModel(searchRepo, podcastRepo)

    private fun result(feedUrl: String) = PodcastSearchResult(
        feedUrl = feedUrl, title = "Test", author = "Author", artworkUrl = null, description = null
    )

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `query shorter than 2 chars stays Idle`() = runTest(testDispatcher) {
        val vm = makeVm()
        vm.onQueryChanged("a")
        advanceTimeBy(500)
        assertIs<SearchUiState.Idle>(vm.uiState.value)
    }

    @Test fun `query debounces 400ms then emits Loading then Success`() = runTest(testDispatcher) {
        val deferred = CompletableDeferred<List<PodcastSearchResult>>()
        coEvery { searchRepo.search("kotlin") } coAnswers { deferred.await() }
        val vm = makeVm()

        vm.onQueryChanged("kotlin")
        advanceTimeBy(399)
        assertIs<SearchUiState.Idle>(vm.uiState.value)

        advanceTimeBy(1) // total 400ms — delay(400) completes, Loading is set
        testDispatcher.scheduler.runCurrent()
        assertIs<SearchUiState.Loading>(vm.uiState.value)

        deferred.complete(listOf(result("https://example.com/feed")))
        testDispatcher.scheduler.runCurrent()
        val state = vm.uiState.value
        assertIs<SearchUiState.Success>(state)
        assertEquals(1, state.results.size)
    }

    @Test fun `search error emits Error state`() = runTest(testDispatcher) {
        coEvery { searchRepo.search(any()) } throws Exception("network down")
        val vm = makeVm()

        vm.onQueryChanged("kotlin")
        advanceTimeBy(400)
        testDispatcher.scheduler.runCurrent()

        assertIs<SearchUiState.Error>(vm.uiState.value)
    }

    @Test fun `rapid typing cancels previous debounce`() = runTest(testDispatcher) {
        coEvery { searchRepo.search(any()) } returns emptyList()
        val vm = makeVm()

        vm.onQueryChanged("ko")
        advanceTimeBy(300)
        vm.onQueryChanged("kot")
        advanceTimeBy(300)
        vm.onQueryChanged("kotl")
        advanceTimeBy(400)
        testDispatcher.scheduler.runCurrent()

        coVerify(exactly = 1) { searchRepo.search("kotl") }
    }

    @Test fun `retry re-runs last query`() = runTest(testDispatcher) {
        coEvery { searchRepo.search("kotlin") } returns listOf(result("https://example.com/feed"))
        val vm = makeVm()

        vm.onQueryChanged("kotlin")
        advanceTimeBy(400)
        testDispatcher.scheduler.runCurrent()

        vm.retry()
        advanceTimeBy(400)
        testDispatcher.scheduler.runCurrent()

        coVerify(exactly = 2) { searchRepo.search("kotlin") }
    }

    @Test fun `selectResult stores result in selectedResult flow`() = runTest(testDispatcher) {
        val vm = makeVm()
        assertNull(vm.selectedResult.value)

        val r = result("https://example.com/feed")
        vm.selectResult(r)

        assertEquals(r, vm.selectedResult.value)
    }

    @Test fun `subscribe calls addPodcast and emits Success`() = runTest(testDispatcher) {
        coEvery { podcastRepo.addPodcast(any()) } returns Unit
        val vm = makeVm()

        vm.subscribe("https://example.com/feed")
        testDispatcher.scheduler.runCurrent()

        coVerify { podcastRepo.addPodcast("https://example.com/feed") }
        assertIs<SubscribeState.Success>(vm.subscribeState.value)
    }

    @Test fun `subscribe failure emits Error state`() = runTest(testDispatcher) {
        coEvery { podcastRepo.addPodcast(any()) } throws Exception("offline")
        val vm = makeVm()

        vm.subscribe("https://example.com/feed")
        testDispatcher.scheduler.runCurrent()

        assertIs<SubscribeState.Error>(vm.subscribeState.value)
    }
}
