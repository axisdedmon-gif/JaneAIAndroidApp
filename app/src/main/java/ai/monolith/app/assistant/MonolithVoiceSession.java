package ai.monolith.app.assistant;

import android.app.assist.AssistContent;
import android.app.assist.AssistStructure;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSession.AssistState;

import ai.monolith.app.MonolithActivity;

/** Receives the active app hierarchy from Android and hands the snapshot to the Monolith interface. */
public class MonolithVoiceSession extends VoiceInteractionSession {
    private final Context appContext;

    public MonolithVoiceSession(Context context) {
        super(context);
        appContext = context.getApplicationContext();
    }

    /** Android 10+ assist delivery path. */
    @Override
    public void onHandleAssist(AssistState state) {
        super.onHandleAssist(state);
        if (state != null) {
            AssistSnapshotStore.save(appContext, state.getAssistStructure(), state.isFocused());
        }
    }

    /** Android 6 through Android 9 compatibility path. */
    @Override
    @SuppressWarnings("deprecation")
    public void onHandleAssist(Bundle data, AssistStructure structure, AssistContent content) {
        super.onHandleAssist(data, structure, content);
        AssistSnapshotStore.save(appContext, structure, true);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onHandleAssistSecondary(Bundle data, AssistStructure structure, AssistContent content, int index, int count) {
        super.onHandleAssistSecondary(data, structure, content, index, count);
        if (structure != null) AssistSnapshotStore.save(appContext, structure, false);
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        Intent launch = new Intent(getContext(), MonolithActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        launch.putExtra("monolith_mode", "assistant_context");
        getContext().startActivity(launch);
    }
}
