package com.orbit.core.ui.util

import android.content.Context
import android.content.ContextWrapper

/**
 * Checks whether the current context is hosted within a Hilt-managed ComponentActivity
 * or Application before calling hiltViewModel().
 * Prevents java.lang.IllegalStateException in non-Hilt Robolectric UI tests or Previews.
 */
fun isHiltAvailable(context: Context): Boolean {
    return try {
        val managerClass = Class.forName("dagger.hilt.internal.GeneratedComponentManager")
        val holderClass = Class.forName("dagger.hilt.internal.GeneratedComponentManagerHolder")

        var current: Context? = context
        while (current != null) {
            if (managerClass.isInstance(current) || holderClass.isInstance(current)) {
                return true
            }
            current = if (current is ContextWrapper) current.baseContext else null
        }
        false
    } catch (_: Throwable) {
        false
    }
}
