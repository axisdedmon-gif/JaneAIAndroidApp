package ai.monolith.app;

/**
 * Concrete manifest entry for the full Monolith WebView/AI runtime.
 *
 * MonolithActivity remains the proven implementation base. This subclass gives Android a distinct
 * component name for the real :core process so the public MonolithActivity component can route
 * through the native House Dedmon access boundary first.
 */
public final class MonolithCoreActivity extends MonolithActivity {
}
