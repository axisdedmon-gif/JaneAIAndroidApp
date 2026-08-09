package com.example.janeai;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class JaneWidgetProvider extends AppWidgetProvider {
    private PendingIntent makeIntent(Context context, String mode, int requestCode) {
        Intent intent = new Intent(context, HudMainActivity.class);
        intent.putExtra("jane_mode", mode);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.jane_widget);

            views.setOnClickPendingIntent(R.id.widgetRoot, makeIntent(context, "home", 100));
            views.setOnClickPendingIntent(R.id.widgetTalk, makeIntent(context, "home", 101));
            views.setOnClickPendingIntent(R.id.widgetMic, makeIntent(context, "voice", 102));
            views.setOnClickPendingIntent(R.id.widgetImage, makeIntent(context, "image", 103));

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }
}
