package ai.monolith.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Home-screen Monolith entry point. Queries launched here enter the existing local Archive/RAG path first. */
public class MonolithSearchWidgetProvider extends AppWidgetProvider {
    private PendingIntent action(Context context, String mode, int requestCode) {
        Intent intent = new Intent(context, MonolithActivity.class);
        intent.putExtra("monolith_mode", mode);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.monolith_search_widget);
            views.setOnClickPendingIntent(R.id.monolithWidgetRoot, action(context, "search", 410));
            views.setOnClickPendingIntent(R.id.monolithWidgetSearch, action(context, "search", 411));
            views.setOnClickPendingIntent(R.id.monolithWidgetVoice, action(context, "voice", 412));
            views.setOnClickPendingIntent(R.id.monolithWidgetVision, action(context, "image", 413));
            manager.updateAppWidget(id, views);
        }
    }
}
