package com.kamerado.voidpass

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application class.
 *
 * Provides an application-scoped coroutine scope used for
 * operations that should outlive any single Activity — specifically
 * the 30-second clipboard-clear timer.
 *
 * Register in AndroidManifest.xml:
 *   <application android:name=".VaultApplication" ...>
 */
class VaultApplication : Application() {

    /**
     * Lives for the entire process lifetime.
     * SupervisorJob means one failing child doesn't cancel the others.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}