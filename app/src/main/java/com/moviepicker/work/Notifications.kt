package com.moviepicker.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.moviepicker.MainActivity
import com.moviepicker.R
import com.moviepicker.domain.TitleKey

object Notifications {
    private const val CHANNEL_WATCHLIST = "watchlist"
    private const val CHANNEL_NEW = "new_for_you"
    private const val NEW_FOR_YOU_ID = 1001
    const val EXTRA_TYPE = "title_type"
    const val EXTRA_ID = "title_id"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_WATCHLIST, "Выход из «Хочу посмотреть»", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Премьеры, цифровые релизы и новые сезоны из вашего списка" }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_NEW, "Новинки для вас", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Свежие фильмы и сериалы, похожие на то, что вам нравится" }
        )
    }

    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showWatchlist(context: Context, key: TitleKey, title: String, text: String) =
        show(context, CHANNEL_WATCHLIST, key.hashCode(), title, text, key)

    fun showNewForYou(context: Context, title: String, text: String, key: TitleKey?) =
        show(context, CHANNEL_NEW, NEW_FOR_YOU_ID, title, text, key)

    @SuppressLint("MissingPermission")
    private fun show(context: Context, channel: String, id: Int, title: String, text: String, key: TitleKey?) {
        if (!canPost(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (key != null) {
                putExtra(EXTRA_TYPE, key.type.path)
                putExtra(EXTRA_ID, key.id)
            }
        }
        val pending = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
