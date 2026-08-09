package ai.monolith.app.assistant;

import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;

/** Creates one native Monolith assistant session per Android assist invocation. */
public class MonolithVoiceSessionService extends VoiceInteractionSessionService {
    @Override
    public VoiceInteractionSession onNewSession(Bundle args) {
        return new MonolithVoiceSession(this);
    }
}
