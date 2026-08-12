package ai.monolith.app;

/**
 * Concrete manifest entry for the full Monolith WebView/AI runtime.
 *
 * MonolithActivity remains the proven implementation base. This subclass gives Android a distinct
 * component name for the real :core process so both the BIOS and public compatibility component
 * can hand off directly without an intermediate verification activity.
 */
public final class MonolithCoreActivity extends MonolithActivity {
}
