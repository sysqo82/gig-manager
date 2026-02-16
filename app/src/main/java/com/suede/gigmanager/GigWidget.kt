package com.suede.gigmanager

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.format.DateTimeFormatter

class GigWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_gig)

        // Load the app label (tour name) from settings
        val prefs = context.getSharedPreferences("gig_prefs", Context.MODE_PRIVATE)
        val tourName = prefs.getString("app_label", context.getString(R.string.app_name)) ?: context.getString(R.string.app_name)
        views.setTextViewText(R.id.widget_tour_name, tourName)

        // Load gigs and find the next upcoming gig
        val dataManager = GigDataManager(context)
        val gigs = dataManager.loadGigs()

        if (gigs.isNotEmpty()) {
            // Find the first non-completed gig
            val upcomingGig = gigs.firstOrNull { gig ->
                gig.isComplete != true && !gig.date.isNullOrEmpty()
            }

            if (upcomingGig != null) {
                // Display the upcoming gig date and location
                val dateText = upcomingGig.date ?: "Date TBD"
                val locationText = upcomingGig.cityVenue ?: "Location TBD"
                
                views.setTextViewText(R.id.widget_gig_date, dateText)
                views.setTextViewText(R.id.widget_gig_location, locationText)

                // Find the index of this gig for passing to MainActivity
                val gigIndex = gigs.indexOf(upcomingGig)

                // Create an intent to launch MainActivity with the gig details
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = "android.appwidget.action.APPWIDGET_UPDATE"
                    putExtra("show_gig_index", gigIndex)
                    putExtra("from_widget", true)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    gigIndex, // use gigIndex as request code to differentiate multiple widgets
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                // Make the date area clickable
                views.setOnClickPendingIntent(R.id.widget_gig_date_container, pendingIntent)
            } else {
                // No upcoming gigs
                views.setTextViewText(R.id.widget_gig_date, "No upcoming gigs")
                views.setTextViewText(R.id.widget_gig_location, "Add a gig to get started")
            }
        } else {
            // No gigs at all
            views.setTextViewText(R.id.widget_gig_date, "No gigs yet")
            views.setTextViewText(R.id.widget_gig_location, "Create a gig to see it here")
        }

        // Update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        // Refresh widget when gigs are updated
        if (intent.action == "com.suede.gigmanager.GIGS_UPDATED") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, GigWidget::class.java))
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }
}
