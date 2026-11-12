package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import java.util.Locale

class LockscreenService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val CHANNEL_ID = "PassiveVocabStudyChannel_v3"
        const val SYSTEM_ALERT_CHANNEL_ID = "LuyenDichSystemAlertChannel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_REFRESH = "com.example.LockscreenService.ACTION_REFRESH"
        const val ACTION_PREV_WORD = "com.example.LockscreenService.ACTION_PREV_WORD"
        const val ACTION_NEXT_WORD = "com.example.LockscreenService.ACTION_NEXT_WORD"
        const val ACTION_SPEAK_WORD = "com.example.LockscreenService.ACTION_SPEAK_WORD"
        const val ACTION_RESTORE_NOTIFICATION = "com.example.LockscreenService.ACTION_RESTORE_NOTIFICATION"

        fun notifyWordChanged(context: Context) {
            VocabAppWidgetProvider.updateAllWidgets(context)
            val prefs = context.getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE)
            val isPinnedEnabled = prefs.getBoolean("lockscreen_enabled", false) ||
                prefs.getBoolean("passive_notif_enabled", false)
            if (isPinnedEnabled) {
                val intent = Intent(context, LockscreenService::class.java).apply {
                    action = ACTION_REFRESH
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } catch (e: Exception) {
                    Log.w("LockscreenService", "Could not refresh LockscreenService", e)
                }
            }
        }

        fun sendSystemAlertNotification(context: Context, title: String, message: String) {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        SYSTEM_ALERT_CHANNEL_ID,
                        "Thông báo Quản lý Thư mục & Bài học",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Thông báo hệ thống khi xóa hoặc cập nhật thư mục và bài học"
                    }
                    manager.createNotificationChannel(channel)
                }

                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    2099,
                    openAppIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val notif = NotificationCompat.Builder(context, SYSTEM_ALERT_CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()

                manager.notify((System.currentTimeMillis() % 10000).toInt() + 3000, notif)
            } catch (e: Exception) {
                Log.w("LockscreenService", "Could not post system alert notification", e)
            }
        }
    }

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val handler = Handler(Looper.getMainLooper())

    private val autoRotateRunnable = object : Runnable {
        override fun run() {
            val prefs = getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE)
            val intervalSec = prefs.getInt("passive_auto_interval_sec", 0)
            val isPinned = prefs.getBoolean("lockscreen_enabled", false) ||
                prefs.getBoolean("passive_notif_enabled", false)
            if (isPinned && intervalSec > 0) {
                PassiveStudyHelper.stepWord(this@LockscreenService, delta = 1)
                updateNotificationAndWidget()
                if (prefs.getBoolean("passive_auto_speak", false)) {
                    speakCurrentPassiveWord()
                }
                handler.postDelayed(this, intervalSec * 1000L)
            }
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == Intent.ACTION_SCREEN_OFF || action == Intent.ACTION_SCREEN_ON) {
                val prefs = context?.getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE)
                val isLockEnabled = prefs?.getBoolean("lockscreen_enabled", false) ?: false
                val isPopupEnabled = prefs?.getBoolean("lockscreen_popup_enabled", true) ?: true

                if (context != null) {
                    updateNotificationAndWidget()
                }

                if (isLockEnabled && isPopupEnabled && action == Intent.ACTION_SCREEN_OFF) {
                    val activityIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_NO_USER_ACTION
                        )
                    }
                    try {
                        context?.startActivity(activityIntent)
                    } catch (e: Exception) {
                        Log.e("LockscreenService", "Failed to start MainActivity from background", e)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            tts = TextToSpeech(applicationContext, this)
        } catch (e: Exception) {
            Log.e("LockscreenService", "TTS init error", e)
        }
        startForeground(NOTIFICATION_ID, buildNotification())

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenStateReceiver, filter)
        scheduleAutoRotate()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE)
        val isPinned = prefs.getBoolean("lockscreen_enabled", false) ||
            prefs.getBoolean("passive_notif_enabled", false)
        if (!isPinned && intent?.action != ACTION_SPEAK_WORD) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_PREV_WORD -> {
                PassiveStudyHelper.stepWord(this, delta = -1)
                updateNotificationAndWidget()
                if (prefs.getBoolean("passive_auto_speak", false)) {
                    speakCurrentPassiveWord()
                }
            }
            ACTION_NEXT_WORD -> {
                PassiveStudyHelper.stepWord(this, delta = 1)
                updateNotificationAndWidget()
                if (prefs.getBoolean("passive_auto_speak", false)) {
                    speakCurrentPassiveWord()
                }
            }
            ACTION_SPEAK_WORD -> {
                speakCurrentPassiveWord()
                if (isPinned) updateNotificationAndWidget()
            }
            ACTION_RESTORE_NOTIFICATION, ACTION_REFRESH -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                updateNotificationAndWidget()
                scheduleAutoRotate()
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                updateNotificationAndWidget()
                scheduleAutoRotate()
            }
        }
        return START_STICKY
    }

    private fun scheduleAutoRotate() {
        handler.removeCallbacks(autoRotateRunnable)
        val prefs = getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE)
        val intervalSec = prefs.getInt("passive_auto_interval_sec", 0)
        if (intervalSec > 0) {
            handler.postDelayed(autoRotateRunnable, intervalSec * 1000L)
        }
    }

    private fun speakCurrentPassiveWord() {
        if (!isTtsReady || tts == null) return
        val state = PassiveStudyHelper.getCurrentWordState(this)
        val rawForeign = state.foreign
            .replace("\\(.*?\\)".toRegex(), "")
            .replace("\\uff08.*?\\uff09".toRegex(), "")
            .replace("\\[.*?\\]".toRegex(), "")
            .trim()
        if (rawForeign.isEmpty()) return
        try {
            tts?.language = if (state.language == "zh") Locale.CHINESE else Locale.US
            tts?.speak(rawForeign, TextToSpeech.QUEUE_FLUSH, null, "PassiveWord_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e("LockscreenService", "Error speaking passive word", e)
        }
    }

    private fun updateNotificationAndWidget() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
        VocabAppWidgetProvider.updateAllWidgets(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Thanh Thông Báo Học Từ Vựng (Đã Ghim)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ghim từ vựng đang học trên thanh thông báo và màn hình khóa, chỉ tắt khi tắt trong ứng dụng"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val state = PassiveStudyHelper.getCurrentWordState(this)
        val cleanDeck = state.deckTitle
            .replace(Regex("^[\\p{So}\\p{Cn}\\s]+"), "")
            .trim()
            .ifBlank { "Luyện Dịch" }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevPending = PendingIntent.getBroadcast(
            this,
            101,
            Intent(this, PassiveStudyReceiver::class.java).setAction(ACTION_PREV_WORD),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val speakPending = PendingIntent.getBroadcast(
            this,
            102,
            Intent(this, PassiveStudyReceiver::class.java).setAction(ACTION_SPEAK_WORD),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nextPending = PendingIntent.getBroadcast(
            this,
            103,
            Intent(this, PassiveStudyReceiver::class.java).setAction(ACTION_NEXT_WORD),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val restorePending = PendingIntent.getBroadcast(
            this,
            104,
            Intent(this, PassiveStudyReceiver::class.java).setAction(ACTION_RESTORE_NOTIFICATION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val collapsedView = RemoteViews(packageName, R.layout.notif_passive_vocab).apply {
            setTextViewText(R.id.notif_meta, "${state.index + 1} / ${state.total}  •  $cleanDeck")
            setTextViewText(R.id.notif_foreign, state.foreign)
            setTextViewText(R.id.notif_vi, state.vi)
            setOnClickPendingIntent(R.id.notif_btn_prev, prevPending)
            setOnClickPendingIntent(R.id.notif_btn_speak, speakPending)
            setOnClickPendingIntent(R.id.notif_btn_next, nextPending)
        }

        val expandedView = RemoteViews(packageName, R.layout.notif_passive_vocab_big).apply {
            setTextViewText(R.id.notif_meta, "TỪ VỰNG GHIM  •  ${state.index + 1} / ${state.total}")
            setTextViewText(R.id.notif_deck, cleanDeck)
            setTextViewText(R.id.notif_foreign, state.foreign)
            setTextViewText(R.id.notif_vi, state.vi)
            setOnClickPendingIntent(R.id.notif_btn_prev, prevPending)
            setOnClickPendingIntent(R.id.notif_btn_speak, speakPending)
            setOnClickPendingIntent(R.id.notif_btn_next, nextPending)
        }

        val titleText = "${state.foreign}  (${state.index + 1}/${state.total})"
        val contentText = state.vi

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setSubText(cleanDeck)
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(openPendingIntent)
            .setDeleteIntent(restorePending) // Immediately re-pins if swiped away on Android 14+
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
        return notification
    }
}

data class PassiveWordState(
    val vi: String,
    val foreign: String,
    val index: Int,
    val total: Int,
    val deckTitle: String,
    val language: String
)

object PassiveStudyHelper {
    private val defaultPairs = listOf(
        "Xin chào!" to "你好！ (Nǐ hǎo!)",
        "Cảm ơn bạn rất nhiều." to "非常感谢你。 (Fēicháng gǎnxiè nǐ.)",
        "Hôm nay công việc bạn có mệt không?" to "今天你工作累吗？ (Jīntiān nǐ gōngzuò lèi ma?)",
        "Có chí thì nên" to "有志者事竟成 (Yǒu zhì zhě shì jìng chéng)",
        "Sức khỏe tôi rất tốt!" to "我身体很好！ (Wǒ shēntǐ hěn hǎo!)"
    )

    fun parsePairsFromPrefs(context: Context): List<Pair<String, String>> {
        val prefs = context.getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE)
        val serialized = prefs.getString("serializedPairs", "") ?: ""
        if (serialized.isNotBlank()) {
            val list = serialized.split("\n").mapNotNull { line ->
                val parts = line.split("||")
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    parts[0].trim() to parts[1].trim()
                } else null
            }
            if (list.isNotEmpty()) return list
        }

        val raw = prefs.getString("rawTextContent", "") ?: ""
        if (raw.isNotBlank()) {
            val list = raw.split("\n").mapNotNull { line ->
                if (!line.contains("=") && !line.contains("#")) return@mapNotNull null
                val parts = line.split("=", "#").map { it.trim() }
                if (parts.size < 2 || parts[0].isEmpty() || parts[1].isEmpty()) return@mapNotNull null
                val p1 = parts[0]
                val p2 = parts[1]
                val hasZh1 = p1.any { it.code in 0x4E00..0x9FFF }
                val hasZh2 = p2.any { it.code in 0x4E00..0x9FFF }
                if (hasZh1 && !hasZh2) p2 to p1 else p1 to p2
            }
            if (list.isNotEmpty()) return list
        }

        return defaultPairs
    }

    fun getCurrentWordState(context: Context): PassiveWordState {
        val prefs = context.getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE)
        val pairs = parsePairsFromPrefs(context)
        val total = pairs.size.coerceAtLeast(1)
        val idx = (prefs.getInt("currentIndex", 0) % total + total) % total
        val pair = pairs.getOrElse(idx) { "Xin chào" to "你好 (Nǐ hǎo)" }
        val deckTitle = (prefs.getString("deckTitle", "") ?: "").ifBlank { "Luyện Dịch • Học thụ động" }
        val lang = prefs.getString("targetLanguage", "zh") ?: "zh"
        return PassiveWordState(
            vi = pair.first,
            foreign = pair.second,
            index = idx,
            total = total,
            deckTitle = deckTitle,
            language = lang
        )
    }

    fun stepWord(context: Context, delta: Int) {
        val prefs = context.getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE)
        val pairs = parsePairsFromPrefs(context)
        if (pairs.isEmpty()) return
        val total = pairs.size
        val current = prefs.getInt("currentIndex", 0)
        val next = ((current + delta) % total + total) % total
        prefs.edit().putInt("currentIndex", next).apply()
    }
}

class PassiveStudyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            LockscreenService.ACTION_PREV_WORD -> {
                PassiveStudyHelper.stepWord(context, delta = -1)
                VocabAppWidgetProvider.updateAllWidgets(context)
                forwardToServiceIfNeeded(context, action)
            }
            LockscreenService.ACTION_NEXT_WORD -> {
                PassiveStudyHelper.stepWord(context, delta = 1)
                VocabAppWidgetProvider.updateAllWidgets(context)
                forwardToServiceIfNeeded(context, action)
            }
            LockscreenService.ACTION_SPEAK_WORD -> {
                forwardToServiceIfNeeded(context, action, forceStart = true)
            }
            LockscreenService.ACTION_RESTORE_NOTIFICATION -> {
                // Re-pin immediately if user swiped notification while feature is still active
                forwardToServiceIfNeeded(context, action)
            }
        }
    }

    private fun forwardToServiceIfNeeded(context: Context, action: String, forceStart: Boolean = false) {
        val prefs = context.getSharedPreferences("StudyStatePrefs", Context.MODE_PRIVATE)
        val isPinned = prefs.getBoolean("lockscreen_enabled", false) ||
            prefs.getBoolean("passive_notif_enabled", false)
        if (isPinned || forceStart) {
            val svcIntent = Intent(context, LockscreenService::class.java).apply {
                this.action = action
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svcIntent)
                } else {
                    context.startService(svcIntent)
                }
            } catch (e: Exception) {
                Log.w("PassiveStudyReceiver", "Could not start LockscreenService for $action", e)
            }
        }
    }
}
