package com.focusbyrj.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/** Package targeting selects a recipient; this signature permission authenticates senders. */
internal object InternalBroadcasts {
    fun register(context: Context, receiver: BroadcastReceiver, filter: IntentFilter) {
        ContextCompat.registerReceiver(context, receiver, filter,
            "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", null,
            ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}
