package org.telegram.messenger;

public class LanguageDetector {
    // Created and used only on globalQueue; the shared model stays pinned once per process.
    private static com.google.mlkit.nl.languageid.LanguageIdentifier languageIdentifier;

    public interface StringCallback {
        void run(String str);
    }
    public interface ExceptionCallback {
        void run(Exception e);
    }

    public static boolean hasSupport() {
        return true;
    }

    public static void detectLanguage(String text, StringCallback onSuccess, ExceptionCallback onFail) {
        detectLanguage(text, onSuccess, onFail, false);
    }

    public static void detectLanguage(String text, StringCallback onSuccess, ExceptionCallback onFail, boolean initializeFirst) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                if (initializeFirst) {
                    com.google.mlkit.common.sdkinternal.MlKitContext.zza(ApplicationLoader.applicationContext);
                }
                if (languageIdentifier == null) {
                    languageIdentifier = com.google.mlkit.nl.languageid.LanguageIdentification.getClient();
                }
                languageIdentifier.identifyLanguage(text)
                    .addOnSuccessListener(str -> {
                        if (onSuccess != null) {
                            onSuccess.run(str);
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (onFail != null) {
                            onFail.run(e);
                        }
                    });
            } catch (IllegalStateException e) {
                if (!initializeFirst) {
                    detectLanguage(text, onSuccess, onFail, true);
                } else {
                    if (onFail != null) {
                        AndroidUtilities.runOnUIThread(() -> onFail.run(e));
                    }
                    FileLog.e(e, false);
                }
            } catch (Exception e) {
                if (onFail != null) {
                    AndroidUtilities.runOnUIThread(() -> onFail.run(e));
                }
                FileLog.e(e);
            } catch (Throwable t) {
                if (onFail != null) {
                    AndroidUtilities.runOnUIThread(() -> onFail.run(null));
                }
                FileLog.e(t, false);
            }
        });
    }
}
