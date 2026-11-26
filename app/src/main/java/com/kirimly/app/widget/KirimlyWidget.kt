package com.kirimly.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.kirimly.app.MainActivity
import com.kirimly.app.R
import com.kirimly.app.ui.MainViewModel

/** Home-screen widget with two pill buttons: Send and Receive. */
class KirimlyWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_kirimly).apply {
            setOnClickPendingIntent(R.id.widget_send, openTab(context, MainViewModel.ROUTE_SEND, 1))
            setOnClickPendingIntent(R.id.widget_receive, openTab(context, MainViewModel.ROUTE_RECEIVE, 2))
        }
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    private fun openTab(context: Context, route: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("kirimly://widget/$route"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainViewModel.EXTRA_OPEN_TAB, route)
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
