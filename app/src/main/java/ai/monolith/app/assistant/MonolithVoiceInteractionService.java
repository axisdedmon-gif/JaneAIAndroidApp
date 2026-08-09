package ai.monolith.app.assistant;

import android.os.Bundle;
import android.service.voice.VoiceInteractionService;
import android.service.voice.VoiceInteractionSession;

/** Android system entry point when Monolith AI is selected as the digital assistant. */
public class MonolithVoiceInteractionService extends VoiceInteractionService {
    @Override
    public void onReady() {
        super.onReady();
        setDisabledShowContext(0);
    }

    public void openMonolithSession() {
        Bundle args = new Bundle();
        args.putString("source", "digital-assistant");
        showSession(args, VoiceInteractionSession.SHOW_WITH_ASSIST | VoiceInteractionSession.SHOW_WITH_SCREENSHOT);
    }
}
