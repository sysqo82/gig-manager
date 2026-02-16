package com.suede.gigmanager

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews

class GigWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d("GigWidget", "onUpdate called for ${appWidgetIds.size} widgets")
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d("GigWidget", "onEnabled called")
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d("GigWidget", "onDisabled called")
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        Log.d("GigWidget", "Updating widget $appWidgetId")
        try {
            val views = RemoteViews(context.packageName, R.layout.widget_gig)

            // Load the app label (tour name) from settings
            val prefs = context.getSharedPreferences("gig_prefs", Context.MODE_PRIVATE)
            val tourName = prefs.getString("app_label", context.getString(R.string.app_name)) ?: context.getString(R.string.app_name)
            views.setTextViewText(R.id.widget_tour_name, tourName)

            // Load gigs and find the next upcoming gig (relative to TODAY)
            val dataManager = GigDataManager(context)
            val gigs = dataManager.loadGigs()

            val nextIndex = dataManager.getNextUpcomingGigIndex()
            Log.d("GigWidget", "Next upcoming gig index: $nextIndex")
            
            if (nextIndex != null && nextIndex in gigs.indices) {
                val upcomingGig = gigs[nextIndex]
                val dateText = upcomingGig.date ?: "Date TBD"
                val locationText = upcomingGig.cityVenue ?: "Location TBD"

                views.setTextViewText(R.id.widget_gig_date, dateText)
                views.setTextViewText(R.id.widget_gig_location, locationText)

                // Intent -> open MainActivity showing that gig's details
                val intent = Intent(context, MainActivity::class.java).apply {
                    // Use a unique action or extra to ensure the Intent is unique for this gig
                    action = "com.suede.gigmanager.SHOW_GIG_$nextIndex"
                    putExtra("show_gig_index", nextIndex)
                    putExtra("from_widget", true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    nextIndex, 
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                views.setOnClickPendingIntent(R.id.widget_gig_date_container, pendingIntent)

                // Manual refresh pending intent (broadcast) for the refresh button
                val refreshIntent = Intent(context, GigWidget::class.java).apply { 
                    action = "com.suede.gigmanager.WIDGET_REFRESH" 
                }
                val refreshPi = PendingIntent.getBroadcast(
                    context, 
                    0, 
                    refreshIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPi)
            } else {
                views.setTextViewText(R.id.widget_gig_date, "No upcoming gigs")
                views.setTextViewText(R.id.widget_gig_location, "Add a gig to get started")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
            Log.d("GigWidget", "Widget $appWidgetId updated successfully")
        } catch (e: Exception) {
            Log.e("GigWidget", "Error updating widget $appWidgetId", e)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("GigWidget", "onReceive: ${intent.action}")
        super.onReceive(context, intent)

        try {
            if (intent.action == "com.suede.gigmanager.GIGS_UPDATED" || 
                intent.action == "com.suede.gigmanager.WIDGET_REFRESH" ||
                intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
                
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, GigWidget::class.java))
                
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            }
        } catch (e: Exception) {
            Log.e("GigWidget", "Error in onReceive", e)
        }
    }
}
