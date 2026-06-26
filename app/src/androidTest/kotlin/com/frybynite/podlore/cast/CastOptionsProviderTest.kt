package com.frybynite.podlore.cast

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class CastOptionsProviderTest {

    private val provider = CastOptionsProvider()
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun getCastOptionsReturnsNonNull() {
        val options = provider.getCastOptions(context)
        assertNotNull(options)
    }

    @Test fun getCastOptionsSetsReceiverAppId() {
        val options = provider.getCastOptions(context)
        assertNotNull(options)
    }

    @Test fun getAdditionalSessionProvidersReturnsNull() {
        assertNull(provider.getAdditionalSessionProviders(context))
    }
}
