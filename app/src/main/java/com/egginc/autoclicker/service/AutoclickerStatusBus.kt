package com.egginc.autoclicker.service

/**
 * Lightweight status bus to decouple AutoclickerService from OverlayService UI.
 */
object AutoclickerStatusBus {
    @Volatile
    private var listener: ((String) -> Unit)? = null

    @Volatile
    private var lastStatus: String = ""

    fun publish(status: String) {
        lastStatus = status
        listener?.invoke(status)
    }

    fun setListener(newListener: ((String) -> Unit)?) {
        listener = newListener
        if (newListener != null && lastStatus.isNotEmpty()) {
            newListener.invoke(lastStatus)
        }
    }
}
