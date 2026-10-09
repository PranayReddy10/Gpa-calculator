package com.gpa.calculator.ads;

import android.app.Activity;
import android.util.Log;

import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

/** Google UMP consent (GDPR / US state privacy). Must complete before any ad request. */
public final class ConsentManager {
    private static final String TAG = "ConsentManager";

    public interface Done {
        void onDone(boolean canRequestAds);
    }

    public static void gather(Activity activity, Done done) {
        ConsentInformation info = UserMessagingPlatform.getConsentInformation(activity);
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        info.requestConsentInfoUpdate(activity, params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, formError -> {
                    if (formError != null) Log.w(TAG, "consent form: " + formError.getMessage());
                    done.onDone(info.canRequestAds());
                }),
                requestError -> {
                    Log.w(TAG, "consent info: " + requestError.getMessage());
                    done.onDone(info.canRequestAds());
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
