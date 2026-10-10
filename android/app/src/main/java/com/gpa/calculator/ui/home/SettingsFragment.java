package com.gpa.calculator.ui.home;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.gpa.calculator.BuildConfig;
import com.gpa.calculator.R;
import com.gpa.calculator.ads.AdsManager;
import com.gpa.calculator.ads.ConsentManager;
import com.gpa.calculator.data.Prefs;
import com.gpa.calculator.data.Repository;
import com.gpa.calculator.data.remote.Analytics;
import com.gpa.calculator.data.remote.CloudSync;
import com.gpa.calculator.data.remote.ConfigStore;
import com.gpa.calculator.data.remote.model.RemoteConfig;
import com.gpa.calculator.databinding.FragmentSettingsBinding;
import com.gpa.calculator.ui.common.Anim;
import com.gpa.calculator.ui.common.Haptics;
import com.gpa.calculator.ui.common.RatePrompt;
import com.gpa.calculator.ui.common.ShareUtil;

public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding b;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentSettingsBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        Prefs p = Prefs.get(requireContext());
        RemoteConfig cfg = ConfigStore.get(requireContext()).config();

        // ---- theme
        int mode = p.themeMode();
        b.themeGroup.check(mode == AppCompatDelegate.MODE_NIGHT_NO ? R.id.theme_light : mode == AppCompatDelegate.MODE_NIGHT_YES ? R.id.theme_dark : R.id.theme_system);
        b.themeGroup.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            int m = id == R.id.theme_light ? AppCompatDelegate.MODE_NIGHT_NO : id == R.id.theme_dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (m == p.themeMode()) return;
            p.setThemeMode(m);
            Haptics.tick(group);
            // Fade the window out, then let AppCompat recreate with the new theme.
            View root = requireActivity().getWindow().getDecorView();
            root.animate().alpha(0f).setDuration(160).withEndAction(() -> {
                AppCompatDelegate.setDefaultNightMode(m);
                root.setAlpha(1f);
            }).start();
        });

        b.switchDynamic.setEnabled(Build.VERSION.SDK_INT >= 31);
        b.switchDynamic.setChecked(p.dynamicColors());
        b.switchDynamic.setOnCheckedChangeListener((v, on) -> {
            p.setDynamicColors(on);
            requireActivity().recreate();
        });
        b.switchHaptics.setChecked(p.hapticFeedback());
        b.switchHaptics.setOnCheckedChangeListener((v, on) -> { p.setHapticFeedback(on); if (on) Haptics.confirm(v); });

        // ---- notifications (OneSignal)
        boolean pushAvailable = com.gpa.calculator.push.PushManager.isAvailable();
        b.rowNotifications.setVisibility(pushAvailable ? View.VISIBLE : View.GONE);
        b.dividerNotifications.setVisibility(pushAvailable ? View.VISIBLE : View.GONE);
        b.switchNotifications.setChecked(p.pushEnabled() && com.gpa.calculator.push.PushManager.hasPermission());
        b.switchNotifications.setOnCheckedChangeListener((v, on) -> {
            if (on && !com.gpa.calculator.push.PushManager.hasPermission()) {
                com.gpa.calculator.push.PushManager.requestPermission(requireActivity(), granted -> {
                    if (b == null || granted) return;
                    b.switchNotifications.setChecked(false);
                    Snackbar.make(b.getRoot(), R.string.push_denied, Snackbar.LENGTH_LONG)
                            .setAction(R.string.open_settings, x -> com.gpa.calculator.push.PushManager.openSystemSettings(requireActivity()))
                            .show();
                });
            } else {
                com.gpa.calculator.push.PushManager.setEnabled(requireContext(), on);
            }
        });

        // ---- data
        b.switchHistory.setChecked(p.saveHistory());
        b.switchHistory.setOnCheckedChangeListener((v, on) -> p.setSaveHistory(on));
        b.switchCloud.setChecked(p.cloudSync());
        b.switchCloud.setOnCheckedChangeListener((v, on) -> {
            p.setCloudSync(on);
            if (on) CloudSync.get(requireContext()).pushPending();
        });
        b.rowRestore.setOnClickListener(v -> CloudSync.get(requireContext()).pull((ok, count, msg) -> {
            if (b == null) return;
            Snackbar.make(b.getRoot(), ok ? getString(R.string.restored, count) : msg, Snackbar.LENGTH_LONG).show();
        }));
        b.rowClear.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_clear_history)
                .setMessage(R.string.clear_history_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> Repository.get(requireContext()).clearHistory())
                .show());
        b.switchAnalytics.setChecked(p.analyticsOptIn());
        b.switchAnalytics.setOnCheckedChangeListener((v, on) -> p.setAnalyticsOptIn(on));

        if (ConsentManager.isPrivacyOptionsRequired(requireActivity())) {
            b.dividerPrivacy.setVisibility(View.VISIBLE);
            b.rowPrivacyOptions.setVisibility(View.VISIBLE);
            b.rowPrivacyOptions.setOnClickListener(v -> ConsentManager.showPrivacyOptions(requireActivity()));
        }

        // ---- ads
        updateAdsCard();
        b.btnWatchAd.setOnClickListener(v -> {
            Haptics.tick(v);
            AdsManager.get(requireContext()).showRewarded(requireActivity(), (rewarded, message) -> {
                if (b == null) return;
                Snackbar.make(b.getRoot(), message, Snackbar.LENGTH_LONG).show();
                updateAdsCard();
            });
        });
        AdsManager.get(requireContext()).preloadRewarded();

        // ---- about
        b.rowRate.setOnClickListener(v -> RatePrompt.launch(requireActivity()));
        b.rowShare.setOnClickListener(v -> {
            String text = cfg.app.share_text == null || cfg.app.share_text.isEmpty() ? getString(R.string.share_text_default) : cfg.app.share_text;
            ShareUtil.shareText(requireContext(), text);
            Analytics.get(requireContext()).log("share_app");
        });
        b.rowPrivacy.setOnClickListener(v -> {
            String url = cfg.app.privacy_url == null || cfg.app.privacy_url.isEmpty() ? "https://play.google.com/store/apps/details?id=" + requireContext().getPackageName() : cfg.app.privacy_url;
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
        });
        b.rowContact.setVisibility(cfg.app.contact_email == null || cfg.app.contact_email.isEmpty() ? View.GONE : View.VISIBLE);
        b.rowContact.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + cfg.app.contact_email))
                    .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " v" + BuildConfig.VERSION_NAME);
            try { startActivity(i); } catch (Exception ignored) {}
        });
        b.version.setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"));
        b.version.setOnLongClickListener(v -> { showDiagnostics(); return true; });
        Anim.staggerChildren((ViewGroup) b.version.getParent(), 40);
    }

    private void updateAdsCard() {
        AdsManager ads = AdsManager.get(requireContext());
        boolean show = ConfigStore.get(requireContext()).config().ads.enabled && ConfigStore.get(requireContext()).config().ads.rewarded_enabled;
        b.cardAds.setVisibility(show ? View.VISIBLE : View.GONE);
        long rem = ads.adFreeRemainingMs();
        if (rem > 0) {
            long min = rem / 60000;
            String txt = min >= 60 ? (min / 60) + " h " + (min % 60) + " min" : min + " min";
            b.adsSub.setText(getString(R.string.settings_ad_free_until, txt));
            b.adsSub.setVisibility(View.VISIBLE);
        } else {
            b.adsSub.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    // ------------------------------------------------------------------ diagnostics (long-press version)
    private String diagnosticsText() {
        android.content.Context c = requireContext();
        com.gpa.calculator.data.remote.ConfigStore store = com.gpa.calculator.data.remote.ConfigStore.get(c);
        RemoteConfig cfg = store.config();
        StringBuilder sb = new StringBuilder();
        sb.append("SERVER\n");
        sb.append("URL: ").append(com.gpa.calculator.data.remote.ApiClient.baseUrl()).append('\n');
        sb.append("App key: ").append(BuildConfig.APP_API_KEY.startsWith("change-me") ? "NOT SET (still the default)" : "set").append('\n');
        if (cfg.fetched_at > 0) {
            sb.append("Config: loaded ").append(android.text.format.DateUtils.getRelativeTimeSpanString(cfg.fetched_at)).append('\n');
        } else {
            sb.append("Config: never loaded (using built-in defaults)\n");
        }
        if (store.lastError() != null) sb.append("Last error: ").append(store.lastError()).append('\n');
        sb.append("Device registered: ").append(Prefs.get(c).deviceToken() != null ? "yes" : "no").append('\n');
        sb.append("\nADS\n").append(AdsManager.get(c).diagnostics()).append('\n');
        sb.append("\nPUSH\n");
        sb.append("OneSignal: ").append(com.gpa.calculator.push.PushManager.isAvailable() ? "started" : "off (no app ID)")
                .append(", permission: ").append(com.gpa.calculator.push.PushManager.hasPermission() ? "granted" : "not granted").append('\n');
        sb.append("\nApp ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")");
        return sb.toString();
    }

    private void showDiagnostics() {
        androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Diagnostics")
                .setMessage(diagnosticsText())
                .setPositiveButton(R.string.ok, null)
                .setNeutralButton("Test server", null)
                .setNegativeButton(R.string.copy, (x, w) -> ShareUtil.copy(b.getRoot(), diagnosticsText()))
                .show();
        // keep the dialog open while testing and refresh its text with the result
        d.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
            d.setMessage("Contacting " + com.gpa.calculator.data.remote.ApiClient.baseUrl() + " …");
            com.gpa.calculator.data.remote.ConfigStore.get(requireContext()).refresh(() -> {
                if (isAdded() && d.isShowing()) d.setMessage(diagnosticsText());
            });
        });
    }
}
