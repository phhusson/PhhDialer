package me.phh.dialer

import android.app.Service
import android.content.Intent
import android.os.IBinder

class EuiccService : Service() {
    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}