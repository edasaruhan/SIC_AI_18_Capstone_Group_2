package com.suzgec.sender

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * adb ile tetikleme (test ve demo için):
 *   adb shell am broadcast -f 32 -n com.suzgec.sender/.SendReceiver --es id banka
 *   adb shell am broadcast -f 32 -n com.suzgec.sender/.SendReceiver --es id iptal   (hepsini kaldır)
 */
class SendReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        if (id == "iptal") {
            context.getSystemService(android.app.NotificationManager::class.java).cancelAll()
            Log.i("SuzgecSender", "Tüm demo bildirimleri kaldırıldı")
            return
        }
        val demo = DEMOS.firstOrNull { it.id == id }
        if (demo == null) {
            Log.w("SuzgecSender", "Bilinmeyen id: $id")
            return
        }
        send(context, demo)
        Log.i("SuzgecSender", "Gönderildi: ${demo.id} (${demo.app})")
    }
}
