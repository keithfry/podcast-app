package com.podcastapp.ui.podcasts

import app.cash.turbine.test
import com.podcastapp.data.repository.PodcastRepository
import com.podcastapp.domain.model.Podcast
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastListViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val repo = mockk<PodcastRepository>(relaxed = true)

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `podcasts emits from repo`() = runTest {
        val podcast = Podcast("http://feed.xml", "Test", "Author", "Desc", null)
        every { repo.podcasts } returns flowOf(listOf(podcast))
        val vm = PodcastListViewModel(repo)
        vm.podcasts.test {
            assertEquals(listOf(podcast), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `addPodcast calls repo`() = runTest {
        every { repo.podcasts } returns flowOf(emptyList())
        coEvery { repo.addPodcast(any()) } returns Unit
        val vm = PodcastListViewModel(repo)
        vm.addPodcast("http://feed.xml")
        coVerify { repo.addPodcast("http://feed.xml") }
    }

    @Test fun `error is null initially`() = runTest {
        every { repo.podcasts } returns flowOf(emptyList())
        val vm = PodcastListViewModel(repo)
        assertNull(vm.error.value)
    }

    @Test fun `error set when addPodcast throws`() = runTest {
        every { repo.podcasts } returns flowOf(emptyList())
        coEvery { repo.addPodcast(any()) } throws Exception("Network error")
        val vm = PodcastListViewModel(repo)
        vm.addPodcast("http://bad.url")
        assertTrue(vm.error.value != null)
    }

    @Test fun `dismissError clears error`() = runTest {
        every { repo.podcasts } returns flowOf(emptyList())
        coEvery { repo.addPodcast(any()) } throws Exception("Network error")
        val vm = PodcastListViewModel(repo)
        vm.addPodcast("http://bad.url")
        vm.dismissError()
        assertNull(vm.error.value)
    }
}
