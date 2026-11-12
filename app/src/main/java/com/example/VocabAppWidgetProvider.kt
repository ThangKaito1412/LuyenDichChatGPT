package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import android.widget.Toast

class VocabAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, VocabAppWidgetProvider::class.java)
                val ids = manager.getAppWidgetIds(componentName)
                if (ids != null && ids.isNotEmpty()) {
                    for (id in ids) {
                        updateAppWidget(context, manager, id)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun requestPinWidget(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val appWidgetManager = context.getSystemService(AppWidgetManager::class.java)
                val myProvider = ComponentName(context, VocabAppWidgetProvider::class.java)
                if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported) {
                    appWidgetManager.requestPinAppWidget(myProvider, null, null)
                    Toast.makeText(
                        context,
                        "Hãy xác nhận 'Thêm' để ghim Tiện ích Từ vựng ra Màn hình chính!",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
            }
            Toast.makeText(
                context,
                "Nhấn giữ ngoài Màn hình chính điện thoại ➔ Chọn 'Tiện ích (Widgets)' ➔ Kéo 'Luyện Dịch' ra màn hình!",
                Toast.LENGTH_LONG
            ).show()
        }

        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val state = PassiveStudyHelper.getCurrentWordState(context)
            val views = RemoteViews(context.packageName, R.layout.widget_vocab_card)

            views.setTextViewText(R.id.widget_deck_title, "📚 ${state.deckTitle}")
            views.setTextViewText(R.id.widget_counter, "${state.index + 1}/${state.total}")
            views.setTextViewText(R.id.widget_foreign, state.foreign)
            views.setTextViewText(R.id.widget_vi, state.vi)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openPending = PendingIntent.getActivity(
                context,
                200,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_content_box, openPending)

            val prevPending = PendingIntent.getBroadcast(
                context,
                201,
                Intent(context, PassiveStudyReceiver::class.java).setAction(LockscreenService.ACTION_PREV_WORD),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val speakPending = PendingIntent.getBroadcast(
                context,
                202,
                Intent(context, PassiveStudyReceiver::class.java).setAction(LockscreenService.ACTION_SPEAK_WORD),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val nextPending = PendingIntent.getBroadcast(
                context,
                203,
                Intent(context, PassiveStudyReceiver::class.java).setAction(LockscreenService.ACTION_NEXT_WORD),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            views.setOnClickPendingIntent(R.id.widget_btn_prev, prevPending)
            views.setOnClickPendingIntent(R.id.widget_btn_speak, speakPending)
            views.setOnClickPendingIntent(R.id.widget_btn_next, nextPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
