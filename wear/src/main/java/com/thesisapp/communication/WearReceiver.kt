package com.thesisapp.communication

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.*
import com.google.android.gms.tasks.Task
import com.thesisapp.presentation.MainActivity
import java.nio.ByteBuffer

class WearReceiver(
    private val context: Context,
    private val onStartRecording: () -> Unit,
    private val onStopRecording: () -> Unit,
    private val onSessionIdReceived: (Int) -> Unit
) : MessageClient.OnMessageReceivedListener {

    private val TAG = "WearReceiver"

    fun register() {
        Wearable.getMessageClient(context).addListener(this)
        Log.d(TAG, "WearReceiver registered")
    }

    fun unregister() {
        Wearable.getMessageClient(context).removeListener(this)
        Log.d(TAG, "WearReceiver unregistered")
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == "/sentCommands") {
            val command = event.data.decodeToString()
            Log.d(TAG, "Received command: $command")

            // Send ACK
            Wearable.getMessageClient(context)
                .sendMessage(event.sourceNodeId, "/commandAck", "ACK".toByteArray())
                .addOnSuccessListener {
                    Log.d(TAG, "Sent ACK to phone")
                }
                .addOnFailureListener {
                    Log.e(TAG, "Failed to send ACK", it)
                }

            when (command) {
                "START" -> {
                    Log.d(TAG, "Starting recording")
                    onStartRecording()
                }
                "STOP" -> {
                    Log.d(TAG, "Stopping recording")
                    onStopRecording()
                }
                else -> Log.w(TAG, "Unknown command: $command")
            }
        }
        else if (event.path == "/sentIds"){
            val id = ByteBuffer.wrap(event.data).int
            Log.d(TAG, "Received id: $id")

            onSessionIdReceived(id)

            // Send ACK
            Wearable.getMessageClient(context)
                .sendMessage(event.sourceNodeId, "/idAck", "ACK".toByteArray())
                .addOnSuccessListener {
                    Log.d(TAG, "Sent ACK to phone")
                }
                .addOnFailureListener {
                    Log.e(TAG, "Failed to send ACK", it)
                }
        }
        else {
            Log.d(TAG, "Unknown path: ${event.path}")
        }
    }
}