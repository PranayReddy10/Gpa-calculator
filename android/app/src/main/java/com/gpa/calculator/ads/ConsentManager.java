package com.gpa.calculator.ads;

import android.app.Activity;
import android.util.Log;

import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

/** Google UMP consent (GDPR / US state privacy). Must complete before any ad request. */
public final class ConsentManager {
    private static final String TAG = AdsManager.TAG;

    /** Last UMP error, shown in the diagnostics screen. */
    public static String lastError = null;

    public interface Done {
        void onDone(boolean canRequestAds);
    }

    public static void gather(Activity activity, Done done) {
        ConsentInformation info = UserMessagingPlatform.getConsentInformation(activity);
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        info.requestConsentInfoUpdate(activity, params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, formError -> {
                    if (formError != null) {
                        lastError = formError.getMessage();
                        Log.w(TAG, "consent form: " + lastError);
                    }
                    Log.i(TAG, "consent status=" + info.getConsentStatus() + " canRequestAds=" + info.canRequestAds());
                    done.onDone(info.canRequestAds());
                }),
                requestError -> {
                    // Happens with Google's test app ID or when no consent message is set up in
                    // AdMob > Privacy & messaging. No form can be shown either way, so don't block
                    // ads for the whole session; users in regions that need consent get the form
                    // once a message is published in AdMob.
                    lastError = requestError.getMessage();
                    Log.w(TAG, "consent info failed (" + requestError.getErrorCode() + "): " + lastError + " -> continuing with ads");
                    done.onDone(true);
                });
    }

    public static boolean isPrivacyOptionsRequired(Activity activity) {
        return UserMessagingPlatform.getConsentInformation(activity).getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    public static void showPrivacyOptions(Activity activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, formError -> {
            if (formError != null) Log.w(TAG, "privacy options: " + formError.getMessage());
        });
    }
}
