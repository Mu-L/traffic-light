package com.leekleak.trafficlight.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SCREEN_OFF
import android.content.Intent.ACTION_SCREEN_ON
import android.content.IntentFilter
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.leekleak.trafficlight.widget.Widget.Companion.FORCE_REFRESH
import com.leekleak.trafficlight.widget.Widget.Companion.SUBSCRIBER_ID_HASH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalAtomicApi::class, ExperimentalGlanceApi::class)
class WidgetReceiver: GlanceAppWidgetReceiver() {
    override val coroutineContext: CoroutineContext get() = Dispatchers.IO
    override val glanceAppWidget: GlanceAppWidget get() = Widget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {

        registerReceiver(context)
        /**
         * Unfortunately Glance widgets have a really stupid rate limit which stops the app from updating
         * the widget more than once every ~1min.
         *
         * The problem is that before widget creation the widget or the launcher or whatever asks the widget to update.
         * Since, of course, the widget is not yet configured, it returns early, leaving the widget empty.
         *
         * The early update also triggers the rate limit which means that after the configuration is
         * actually done the update fails!
         *
         * Very stupid, but if you just ignore and don't update widgets with no subscriberId, it works fine.
         *
         * For the record, seems like a system bug (from 2012!!!):
         *
         * https://stackoverflow.com/a/12236443
         */

        val newAppWidgetIds = appWidgetIds.filter { id ->
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(id)
            val prefs = runBlocking { getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) }

            prefs[SUBSCRIBER_ID_HASH] != null
        }.toIntArray()

        super.onUpdate(context, appWidgetManager, newAppWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SCREEN_ON -> {
                startAlarmManager(context)
            }
            ACTION_SCREEN_OFF -> {
                killAlarmManager(context)
            }
            else -> {
                super.onReceive(context, intent)
            }
        }
    }

    override fun onDisabled(context: Context?) {
        context?.let { unregisterReceiver(it) }
        super.onDisabled(context)
    }

    fun registerReceiver(context: Context) {
        if (registered.load()) return
        context.applicationContext.registerReceiver(this, IntentFilter().apply {
            addAction(ACTION_SCREEN_ON)
            addAction(ACTION_SCREEN_OFF)
        })
        registered.store(true)
    }

    fun unregisterReceiver(context: Context) {
        if (!registered.load()) return
        try {
            context.applicationContext.unregisterReceiver(this)
            registered.store(false)
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "WidgetReceiver not registered")
        }
    }

    companion object {
        private var registered = AtomicBoolean(false)

        suspend fun setForceUpdateWidgets(context: Context) {
            val glanceManager = GlanceAppWidgetManager(context)
            val ids = glanceManager.getGlanceIds(Widget::class.java)
            for (id in ids) {
                updateAppWidgetState(context, id) { prefs ->
                    prefs[FORCE_REFRESH] = true
                }
            }
        }
    }
}