/*
 * SettingsActivity.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.activities;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.backup.BackupManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import androidx.annotation.StringRes;
import android.widget.Toast;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.services.AlarmService;
import net.exclaimindustries.geohashdroid.services.WikiService;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.HashBuilder;
import net.exclaimindustries.tools.QueueService;

import java.util.List;

/**
 * <p>
 * So, the actual Android class is already called "{@link PreferenceActivity}",
 * it turns out.  So let's call this one <code>PreferencesScreen</code>, because
 * it got really confusing to call it <code>PreferencesActivity</code> like it
 * used to be.
 * </p>
 *
 * <p>
 * Note that this doesn't inherit from BaseGHDThemeActivity.  It has to
 * implement all of that itself.
 * </p>
 */
public class PreferencesScreen extends PreferenceActivity {
    private boolean mStartedInNight = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mStartedInNight = prefs.getBoolean(GHDConstants.PREF_NIGHT_MODE, false);

        // We have to do this BEFORE any layouts are set up.
        if(mStartedInNight)
            setTheme(R.style.Theme_GeohashDroidDark);
        else
            setTheme(R.style.Theme_GeohashDroid);

        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If the nightiness has changed since we paused, do a recreate.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if(prefs.getBoolean(GHDConstants.PREF_NIGHT_MODE, false) != mStartedInNight)
            recreate();
    }

    /**
     * This largely comes from Android Studio's default Setting Activity wizard
     * thingamajig.  It conveniently updates preferences with summaries.
     */
    private static Preference.OnPreferenceChangeListener mSummaryUpdater = (preference, newValue) -> {
        updateSummary(preference, newValue);
        return true;
    };

    private static void updateSummary(Preference preference, Object newValue) {
        // The basic stringy version of the value.
        String stringValue = newValue.toString();

        if(preference instanceof ListPreference) {
            // For list preferences, look up the correct display value in
            // the preference's 'entries' list.
            ListPreference listPreference = (ListPreference) preference;
            int index = listPreference.findIndexOfValue(stringValue);

            // Set the summary to reflect the new value.
            preference.setSummary(
                    index >= 0
                            ? listPreference.getEntries()[index]
                            : null);
        } else {
            // Eh, just use the string value.  That's simple enough.
            preference.setSummary(stringValue);
        }
    }

    /**
     * Also from Android Studio, this attaches a preference to the summary
     * updater.
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(mSummaryUpdater);

        // Trigger the listener immediately with the preference's current value.
        mSummaryUpdater.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        // Let's Honeycomb these preferences right up.  Headers!
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return(fragmentName.equals(MapPreferenceFragment.class.getName())
            || fragmentName.equals(WikiPreferenceFragment.class.getName())
            || fragmentName.equals(OtherPreferenceFragment.class.getName()));
    }

    /**
     * These are your garden-variety map preferences, assuming your garden is on
     * the map somewhere.
     */
    public static class MapPreferenceFragment extends PreferenceFragment {
        private static final String KNOWN_NOTIFICATION_REMINDER_DIALOG = "KnownNotificationReminderDialog";

        /**
         * This {@link DialogFragment} reminds the user that we're not monsters
         * and therefore won't spam them with hundreds of notifications if they
         * really have that many known locations.
         */
        public static class KnownNotificationLimitDialogFragment extends DialogFragment {
            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                String setting = getArguments().getString(GHDConstants.PREF_KNOWN_NOTIFICATION, "");

                @StringRes int dialogText;

                switch(setting) {
                    case GHDConstants.PREFVAL_KNOWN_NOTIFICATION_PER_GRATICULE:
                        dialogText = R.string.pref_knownnotification_pergraticule_reminder;
                        break;
                    case GHDConstants.PREFVAL_KNOWN_NOTIFICATION_PER_LOCATION:
                        dialogText = R.string.pref_knownnotification_perlocation_reminder;
                        break;
                    default:
                        // Really, this shouldn't happen, but if the dialog is
                        // being summoned anyway, may as well...
                        dialogText = R.string.pref_knownnotification_fallback_reminder;
                        break;
                }

                return new AlertDialog.Builder(getActivity()).setMessage(dialogText)
                        .setTitle(R.string.pref_knownnotification_reminder_title)
                        .setNegativeButton(R.string.stop_reminding_me_label, (dialog, which) -> {
                            dismiss();

                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putBoolean(GHDConstants.PREF_STOP_BUGGING_ME_KNOWN_NOTIFICATION_LIMIT, true);
                            editor.apply();

                            BackupManager bm = new BackupManager(getActivity());
                            bm.dataChanged();
                        })
                        .setPositiveButton(R.string.gotcha_label, (dialog, which) -> dismiss())
                        .create();
            }
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_map);

            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

            bindPreferenceSummaryToValue(findPreference(GHDConstants.PREF_DIST_UNITS));
            bindPreferenceSummaryToValue(findPreference(GHDConstants.PREF_COORD_UNITS));
            bindPreferenceSummaryToValue(findPreference(GHDConstants.PREF_STARTUP_BEHAVIOR));

            // This one needs special handling due to its onPreferenceChange
            // being overridden elsewhere.
            Preference knownNotification = findPreference(GHDConstants.PREF_KNOWN_NOTIFICATION);
            updateSummary(knownNotification, prefs.getString(knownNotification.getKey(), GHDConstants.PREFVAL_KNOWN_NOTIFICATION_ONLY_ONCE));

            // The known locations manager is just another Activity.
            findPreference("_knownLocations").setOnPreferenceClickListener(preference -> {
                Intent i = new Intent(getActivity(), KnownLocationsPicker.class);
                startActivity(i);
                return true;
            });

            // The known locations notification preference needs a quick
            // reminder popup.
            knownNotification.setOnPreferenceChangeListener((preference, newValue) -> {
                // First off, ignore this entirely if the user told us to
                // stop bugging them or if the new setting doesn't need a
                // warning.
                boolean stopBugging = prefs.getBoolean(GHDConstants.PREF_STOP_BUGGING_ME_KNOWN_NOTIFICATION_LIMIT, false);

                // Maybe I should invest in shorter variable names.
                if(!stopBugging
                        && (newValue.equals(GHDConstants.PREFVAL_KNOWN_NOTIFICATION_PER_GRATICULE)
                            || newValue.equals(GHDConstants.PREFVAL_KNOWN_NOTIFICATION_PER_LOCATION))) {
                    // Notify!
                    DialogFragment frag = new KnownNotificationLimitDialogFragment();
                    Bundle args = new Bundle();
                    args.putString(GHDConstants.PREF_KNOWN_NOTIFICATION, newValue.toString());
                    frag.setArguments(args);
                    frag.show(getFragmentManager(), KNOWN_NOTIFICATION_REMINDER_DIALOG);
                }

                // We're also doing the summary update ourselves, as this
                // takes over the onPreferenceChange part that
                // bindPreferenceSummaryToValue needs to function.
                updateSummary(preference, newValue);

                return true;
            });
        }

        @Override
        public void onStop() {
            BackupManager bm = new BackupManager(getActivity());
            bm.dataChanged();

            super.onStop();
        }
    }

    /**
     * These are the preferences you'll be seeing way too often if you keep
     * getting your wiki password wrong.
     */
    public static class WikiPreferenceFragment extends PreferenceFragment {
        /**
         * This keeps track of whether or not the wiki username and/or password
         * have changed.  If so, we need to ask WikiService to resume itself, as
         * the user might've come here to resolve a bad login error.
         */
        private boolean mHasChanged = false;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_wiki);

            // Unfortunately, we can't use the otherwise-common binding method
            // for username and password, owing to the extra boolean we need to
            // track.  Worse, since we don't want to update the summary for
            // password (for obvious reasons), we can't even share the same
            // object between the two preferences.  Well, we CAN, but that won't
            // really buy us much in terms of efficiency.
            Preference usernamePref = findPreference(GHDConstants.PREF_WIKI_USER);
            usernamePref.setOnPreferenceChangeListener((preference, newValue) -> {
                preference.setSummary(newValue.toString());
                mHasChanged = true;
                return true;
            });
            usernamePref.setSummary(PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(GHDConstants.PREF_WIKI_USER, ""));

            findPreference(GHDConstants.PREF_WIKI_PASS).setOnPreferenceChangeListener((preference, newValue) -> {
                mHasChanged = true;
                return true;
            });
        }

        @Override
        public void onStop() {
            // If something changed, tell WikiService to kick back in.  Don't
            // worry; if WikiService isn't paused, this won't do anything, and
            // if it's stopped for any other reason, it'll stop again when this
            // comes in.
            if(mHasChanged) {
                mHasChanged = false;
                Intent i = new Intent(getActivity(), WikiService.class);
                i.putExtra(QueueService.COMMAND_EXTRA, QueueService.COMMAND_RESUME);
                getActivity().startService(i);
            }

            BackupManager bm = new BackupManager(getActivity());
            bm.dataChanged();

            super.onStop();
        }
    }

    /**
     * These preferences are outcasts, and nobody likes them.
     */
    public static class OtherPreferenceFragment extends PreferenceFragment {
        private static final String WIPE_DIALOG = "wipeDialog";
        private static final String RESET_BUGGING_ME_DIALOG = "resetBuggingMe";

        /**
         * This is the {@link DialogFragment} that shows up when the user wants
         * to wipe the stock cache, just to make really really sure the user
         * really wants to do so.
         */
        public static class WipeCacheDialogFragment extends DialogFragment {
            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                return new AlertDialog.Builder(getActivity()).setMessage(R.string.pref_stockwipe_dialog_text)
                        .setTitle(R.string.pref_stockwipe_title)
                        .setPositiveButton(R.string.dialog_stockwipe_yes, (dialog, which) -> {
                            // Well, you heard the orders!
                            dismiss();

                            if(HashBuilder.deleteCache(getActivity())) {
                                Toast.makeText(
                                        getActivity(),
                                        R.string.toast_stockwipe_success,
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(
                                        getActivity(),
                                        R.string.toast_stockwipe_failure,
                                        Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton(R.string.dialog_stockwipe_no, (dialog, which) -> dismiss())
                        .create();
            }
        }

        public static class ResetBuggingMeDialogFragment extends DialogFragment {
            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                return new AlertDialog.Builder(getActivity()).setMessage(R.string.pref_reset_butting_me_dialog_text)
                        .setTitle(R.string.pref_reset_bugging_me_title)
                        .setPositiveButton(R.string.dialog_reset_bugging_me_yes, (dialog, which) -> {
                            // Well, you heard the orders!
                            dismiss();

                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                            SharedPreferences.Editor editor = prefs.edit();

                            // This list will grow and grow as I keep adding
                            // in new prompts until I get sick of it and
                            // come up with a more efficient way to do this.
                            editor.putBoolean(GHDConstants.PREF_STOP_BUGGING_ME_PREFETCH_WARNING, false);
                            editor.putBoolean(GHDConstants.PREF_STOP_BUGGING_ME_KNOWN_NOTIFICATION_LIMIT, false);
                            editor.apply();

                            Toast.makeText(
                                    getActivity(),
                                    R.string.toast_reset_bugging_me_success,
                                    Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(R.string.dialog_reset_bugging_me_no, (dialog, which) -> dismiss())
                        .create();
            }
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_other);

            bindPreferenceSummaryToValue(findPreference(GHDConstants.PREF_STOCK_CACHE_SIZE));

            // The stock alarm preference needs to enable/disable the alarm as
            // need be.
            findPreference(GHDConstants.PREF_STOCK_ALARM).setOnPreferenceChangeListener((preference, newValue) -> {
                if(newValue instanceof Boolean) {
                    Boolean set = (Boolean) newValue;

                    Intent i = new Intent(getActivity(), AlarmService.class);

                    if(set) {
                        // ON!  Start the service!
                        i.setAction(AlarmService.STOCK_ALARM_ON);
                    } else {
                        // OFF!  Stop the service and cancel all alarms!
                        i.setAction(AlarmService.STOCK_ALARM_OFF);
                    }

                    AlarmService.enqueueWork(getActivity(), i);
                }

                return true;
            });

            // Cache wiping is more a button than a preference, per se.
            findPreference("_stockWipe").setOnPreferenceClickListener(preference -> {
                DialogFragment frag = new WipeCacheDialogFragment();
                frag.show(getFragmentManager(), WIPE_DIALOG);
                return true;
            });

            // As is the reminder unremindening.
            findPreference("_resetBuggingMe").setOnPreferenceClickListener(preference -> {
                DialogFragment frag = new ResetBuggingMeDialogFragment();
                frag.show(getFragmentManager(), RESET_BUGGING_ME_DIALOG);
                return true;
            });
        }

        @Override
        public void onStop() {
            BackupManager bm = new BackupManager(getActivity());
            bm.dataChanged();

            super.onStop();
        }
    }
}
