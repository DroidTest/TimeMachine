/*
 * AlarmService.java
 * Copyright (C)2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.core.app.JobIntentService;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import android.util.Log;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.activities.CentralMap;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.Graticule;
import net.exclaimindustries.geohashdroid.util.HashBuilder;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.geohashdroid.util.KnownLocation;
import net.exclaimindustries.geohashdroid.util.UnitConverter;
import net.exclaimindustries.tools.AndroidUtil;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * <p>
 * <code>AlarmService</code> is a background service that retrieves the current stock
 * value around 9:30am ET (that is, a reasonable time after the opening of the
 * New York Stock Exchange, at which time the DJIA opening value is known).
 * It makes requests to {@link StockService}, which then stores the result away
 * in the cache so that later instances of hashing will have that data available
 * right away.
 * </p>
 * 
 * <p>
 * This WILL try to start itself at boot time (assuming we get the boot intent).
 * </p>
 *
 * @author Nicholas Killewald
 *
 */
public class AlarmService extends JobIntentService {
    
    private static final String DEBUG_TAG = "AlarmService";

    private AlarmManager mAlarmManager;
    private NotificationManagerCompat mNotificationManager;
    
    private NotificationCompat.Builder mNotificationBuilder;

    private int[] mNotifyIds;
    
    /**
     * Broadcast intent for the alarm that tells StockService that it's time to
     * go fetch a stock.  At that time, it'll retrieve stock data for "today"
     * and "yesterday".  In this case, "today" and "yesterday" are both relative
     * to when stock data is expected to exist for the actual "today"; for
     * instance, if this is called on a Saturday, "today" will be Friday (the
     * NYSE isn't open on Saturday, so Friday's open value is used) and
     * "yesterday" will also be Friday (both 30W and non-30W users get the same
     * hash data on Saturdays and Sundays).
     */
    private static final String STOCK_ALARM = "net.exclaimindustries.geohashdroid.STOCK_ALARM";

    /**
     * Broadcast intent for the alarm that tells StockService to try again on
     * a failed check due to the stock not being posted yet.  In practice, the
     * resulting action will be the same as STOCK_ALARM (cache the stocks). 
     * This is needed because otherwise it'd be considered the same intent,
     * meaning the single-shot alarm would cancel the first one.
     *
     * Do note, this intent should NOT be scheduled to be repeating.
     */
    private static final String STOCK_ALARM_RETRY = "net.exclaimindustries.geohashdroid.STOCK_ALARM_RETRY";

    /**
     * Intent sent when the network's come back up.  This tells the service to
     * shut off the receiver and otherwise behave as if it were a STOCK_ALARM.
     */
    private static final String STOCK_ALARM_NETWORK_BACK = "net.exclaimindustries.geohashdroid.STOCK_ALARM_NETWORK_BACK";
    
    /**
     * Directed intent to tell StockService to set the alarms.
     */
    public static final String STOCK_ALARM_ON = "net.exclaimindustries.geohashdroid.STOCK_ALARM_ON";

    /**
     * Directed intent to tell StockService to cancel the alarms.
     */
    public static final String STOCK_ALARM_OFF = "net.exclaimindustries.geohashdroid.STOCK_ALARM_OFF";

    /**
     * Directed intent to tell CentralMap to go directly to this Info.
     */
    public static final String START_INFO = "net.exclaimindustries.geohashdroid.START_INFO";
    /**
     * Directed intent to tell CentralMap to go directly to this Info, and it's
     * also a globalhash, and this helps make it different enough from the other
     * intent that isn't a globalhash such that PendingIntent won't overwrite
     * one with the other.
     */
    public static final String START_INFO_GLOBAL = "net.exclaimindustries.geohashdroid.START_INFO_GLOBAL";

    /**
     * Notification group for all non-globalhash notifications, if the user has
     * the options set such that a bunch of them can show up.
     */
    public static final String NOTIFICATION_GROUP_LOCAL = "net.exclaimindustries.geohashdroid.NOTIFICATION_GROUP_LOCAL";

    private static final int ALARM_CONNECTIVITY_JOB = 1;
    private static final int LOCAL_NOTIFICATION = 1;
    private static final int GLOBAL_NOTIFICATION = 2;

    private static final int SERVICE_JOB_ID = 1000;

    /**
     * <p>
     * This receiver listens for network connectivity changes in case we ran
     * into a problem with network connectivity and wanted to know if that
     * changed.
     * </p>
     *
     * <p>
     * This is only used in Android SDKs that don't have JobScheduler.  This
     * whole thing was deprecated in Android N.
     * </p>
     */
    public static class NetworkReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action == null) return;

            if(action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                Log.d(DEBUG_TAG, "Network status update!");
                if(AndroidUtil.isConnected(context)) {
                    Log.d(DEBUG_TAG, "The network is back up!");

                    // NETWORK'D!!!
                    Intent i = new Intent(context, AlarmService.class);
                    i.setAction(STOCK_ALARM_NETWORK_BACK);
                    enqueueWork(context, i);
                }
            }
        }
    }

    /**
     * This kicks in on connectivity changes in the event that JobScheduler is
     * available (Android 21 or higher).  All what it does is wake up the
     * fetcher.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static class AlarmServiceJobService extends JobService {
        @Override
        public boolean onStartJob(JobParameters params) {
            // Since we should only get this when we have a network connection,
            // send out the Intent that says we're back.
            Intent i = new Intent(this, AlarmService.class);
            i.setAction(STOCK_ALARM_NETWORK_BACK);
            enqueueWork(this, i);

            // And we're done now!  No thread to spin up or anything.
            return false;
        }

        @Override
        public boolean onStopJob(JobParameters params) {
            // Man, I really hope sending out one Intent doesn't require so much
            // time that onStopJob gets triggered.
            return false;
        }
    }
    
    /**
     * This wakes up the service when the party alarm starts.
     */
    public static class StockAlarmReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(DEBUG_TAG, "STOCK ALARM!!!  Action is " + intent.getAction());

            // Fire off the Intent to start up the service.  That'll handle all
            // of whatever we need handled.
            Intent i = new Intent(context, AlarmService.class);
            i.setAction(intent.getAction());
            enqueueWork(context, i);
        }
    }
    
    /**
     * This listens for any update from StockService, throwing out anything that
     * isn't related to the alarm.
     */
    public static class StockReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Check the Intent for the alarm flag.  We'll just straight give up
            // if it's not an alarm, since we don't really care.
            Bundle stuff = intent.getBundleExtra(StockService.EXTRA_STUFF);
            int flags = 0;
            if(stuff != null) {
                flags = stuff.getInt(StockService.EXTRA_REQUEST_FLAGS, 0);
            }

            if((flags & StockService.FLAG_ALARM) != 0)
            {
                Log.d(DEBUG_TAG, "StockService returned with an alarming response!");
                
                // It's ours!  Send it to the wakeful part!
                intent.setClass(context, AlarmService.class);
                enqueueWork(context, intent);
            }
        }
    }

    /**
     * When bootup happens, this makes sure AlarmService is ready to go if the
     * user's got that set up.
     */
    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action == null) return;

            if(action.equals(Intent.ACTION_BOOT_COMPLETED)) {
                // It's boot time!  We might need to flip on the party alarm!
                Log.i(DEBUG_TAG, "Gooooooood morning, Geohashland!  It's boot time in " + TimeZone.getDefault().getDisplayName() + "!");

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                if(prefs.getBoolean(GHDConstants.PREF_STOCK_ALARM, false)) {
                    // Set the alarm!
                    Log.i(DEBUG_TAG, "The stock alarm is now being started...");
                    Intent i = new Intent(context, AlarmService.class);
                    i.setAction(AlarmService.STOCK_ALARM_ON);
                    enqueueWork(context, i);
                } else {
                    Log.i(DEBUG_TAG, "The stock alarm is off, nothing's being started.");
                }
            }
        }
    }

    /**
     * This makes a 9:30am ET Calendar for today's date.  Note that even if a
     * Calendar is supplied, what will be returned will be in America/New_York,
     * using the date it is in New York right now.
     *
     * @param source if not null, use this as the base, rather than build up a
     *               new Calendar from scratch
     * @return a new Calendar for 9:30am ET for today's (or the supplied) date
     */
    private Calendar makeNineThirty(@Nullable Calendar source) {
        Calendar base;

        if(source == null) {
            base = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
        } else {
            base = (Calendar)source.clone();
            base.setTimeZone(TimeZone.getTimeZone("America/New_York"));
        }

        base.set(Calendar.HOUR_OF_DAY, 9);
        base.set(Calendar.MINUTE, 30);
        base.set(Calendar.SECOND, 0);
        base.set(Calendar.MILLISECOND, 0);

        return base;
    }

    /**
     * Makes a new Calendar that represents the most recent probable date that
     * a stock would exist.  It does so by comparing the current time to 9:30am
     * ET of the same day.  If it's before 9:30am (and would thus be before we
     * can confidently say the NYSE has opened and a value reported), this will
     * rewind it by one day.  If it's after 9:30am, the date will remain the
     * same.  Note that the only important part of this is the date; the actual
     * time and time zone of the returned value are not guaranteed, though
     * chances are it'll be in the same time zone as what is given (or the
     * default time zone if not given).
     *
     * This implicitly assumes that source is today, if given.  This won't
     * return an accurate date if, say, source is next week.
     *
     * @param source if not null, use this as the base, rather than whatever
     *               the system considers the current time.
     * @return a new Calendar whose date is the most recent date a stock is
     *         likely to exist.
     */
    @NonNull
    private Calendar getMostRecentStockDate(@Nullable Calendar source) {
        Calendar base;

        if(source == null) {
            base = Calendar.getInstance();
        } else {
            base = (Calendar)source.clone();
        }

        // First, get 9:30 for today.
        Calendar nineThirty = makeNineThirty(base);

        // Then, compare it to the base.
        if(base.before(nineThirty)) {
            // It's before 9:30am!  Rewind!
            base.add(Calendar.DAY_OF_MONTH, -1);
        }

        // And that should be that!
        return base;
    }

    private void showNotification(@NonNull Calendar date) {
        // The notification in this case just says when there's an active
        // network transaction going.  We don't need to bug the user that we're
        // waiting for a network connection, as chances are, the user's also
        // waiting for one, and doesn't need us reminding them of this fact.
        mNotificationBuilder.setContentText(
                getString(R.string.notification_detail,
                        DateFormat
                            .getDateInstance(DateFormat.MEDIUM)
                            .format(date.getTime())));
        
        mNotificationManager.notify(R.id.alarm_notification, mNotificationBuilder.build());
    }
    
    private void clearNotification() {
        mNotificationManager.cancel(R.id.alarm_notification);
    }
    
    private void snooze() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, 30);
        
        Intent alarmIntent = new Intent(this, StockAlarmReceiver.class);
        alarmIntent.setAction(STOCK_ALARM_RETRY);

        // Even if the user's added us to the whitelist, we won't be able to use
        // the plain set call in Marshmallow or higher.  Not with Doze to worry
        // about.
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mAlarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    cal.getTimeInMillis(),
                    PendingIntent.getBroadcast(this, 0, alarmIntent, 0));
        } else {
            mAlarmManager.set(AlarmManager.RTC_WAKEUP,
                    cal.getTimeInMillis(),
                    PendingIntent.getBroadcast(this, 0, alarmIntent, 0));
        }
    }

    /**
     * <p>
     * Sets up the next stock alarm (9:30am ET).  We need to do this rather than
     * use setRepeating because Doze ruined that for us.
     * </p>
     *
     * <p>
     * This is a convenience method to always pass false to {@link #setNextAlarm(boolean)}.
     * That is, this may set an alarm for later today if it isn't 9:30am ET yet.
     * </p>
     *
     * @see #setNextAlarm(boolean)
     */
    private void setNextAlarm() {
        setNextAlarm(false);
    }

    /**
     * Sets up the next stock alarm (9:30am ET).  We need to do this rather than
     * use setRepeating because Doze ruined that for us.
     *
     * @param definitelyTomorrow true to always set the alarm for tomorrow, even if it's before 9:30am ET
     */
    private void setNextAlarm(boolean definitelyTomorrow) {
        // We're aiming at 9:30am ET (with any applicable DST adjustments).  The
        // NYSE opens at 9:00am ET, but in the interests of possible clock
        // discrepancies and such (not to mention any delays in the stock
        // reporting sites being updated), we'll wait the extra half hour.  The
        // alarm should be the NEXT available 9:30am ET.  If the user wants to
        // take a chance and get a stock value closer to 9:00am ET than that,
        // well, they can do it themselves.
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
        Calendar alarmTime = makeNineThirty(cal);

        if(definitelyTomorrow || alarmTime.before(cal)) {
            alarmTime.add(Calendar.DAY_OF_MONTH, 1);
        }

        Intent alarmIntent = new Intent(STOCK_ALARM);
        alarmIntent.setClass(this, StockAlarmReceiver.class);

        Log.d(DEBUG_TAG, "Setting a wakeup alarm for " + alarmTime.getTime().toString());

        // Because there's no Doze-friendly version of setRepeating (grr), we
        // have to re-set an allow-idle alarm every time.
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mAlarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    alarmTime.getTimeInMillis(),
                    PendingIntent.getBroadcast(this, 0, alarmIntent, 0));
        } else {
            mAlarmManager.set(AlarmManager.RTC_WAKEUP,
                    alarmTime.getTimeInMillis(),
                    PendingIntent.getBroadcast(this, 0, alarmIntent, 0));
        }

    }
    
    private void sendRequest(@NonNull Graticule g) {
        // The Graticule will be one of the dummies, as all we really care about
        // is if it's 30W or not.  And we don't really care about it THAT much,
        // just enough to put the right string in the notification.  Otherwise,
        // StockService works it out.
        Calendar cal = Calendar.getInstance();

        // However, if it IS 30W, we want to generate a stock cache value for
        // TOMORROW, as the 30W Rule allows us to know what tomorrow's hash is,
        // and we WANT that owing to when it triggers.
        if(g.uses30WRule()) cal.add(Calendar.DATE, 1);
        cal = getMostRecentStockDate(cal);
        
        Intent request = new Intent(this, StockService.class);
        request.setAction(StockService.ACTION_STOCK_REQUEST)
            .putExtra(StockService.EXTRA_GRATICULE, g)
            .putExtra(StockService.EXTRA_DATE, cal)
            .putExtra(StockService.EXTRA_REQUEST_ID, cal.getTimeInMillis() / 1000)
            .putExtra(StockService.EXTRA_REQUEST_FLAGS, StockService.FLAG_ALARM)
            .putExtra(StockService.EXTRA_RESPOND_TO, StockReceiver.class);
        
        // The notification goes up first.
        showNotification(Info.makeAdjustedCalendar(cal, g));
        
        // THEN we send the request.
        StockService.enqueueWork(this, request);
    }

    /**
     * Convenience method for enqueuing work in to this service.
     */
    public static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, AlarmService.class, SERVICE_JOB_ID, work);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Init these now, at create time.  The service MIGHT not die between
        // calls, after all.  Maybe.
        mAlarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        mNotificationManager = NotificationManagerCompat.from(this);
        
        // Ready the notification!  The detail text will be set by date, of
        // course.  Also, we can go ahead and make this a public Notification.
        // It's not really sensitive.
        mNotificationBuilder = new NotificationCompat.Builder(this, GHDConstants.CHANNEL_STOCK_PREFETCHER)
            .setSmallIcon(R.drawable.ic_stat_file_file_download)
            .setContentTitle(getString(R.string.notification_title))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        mNotifyIds = getNotifyIds();
    }
    
    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        String action = intent.getAction();
        if(action == null) return;

        switch(intent.getAction()) {
            case STOCK_ALARM_OFF:
                // We've been told to stop all alarms!
                Log.d(DEBUG_TAG, "Got STOCK_ALARM_OFF!");
                mAlarmManager.cancel(PendingIntent.getBroadcast(this, 0, new Intent(STOCK_ALARM).setClass(this, StockAlarmReceiver.class), 0));
                mAlarmManager.cancel(PendingIntent.getBroadcast(this, 0, new Intent(STOCK_ALARM_RETRY).setClass(this, StockAlarmReceiver.class), 0));
                stopWaitingForNetwork();
                clearNotification();
                break;
            case STOCK_ALARM_ON:
                Log.d(DEBUG_TAG, "Got STOCK_ALARM_ON!");
                // At init time, set the alarm.
                setNextAlarm();

                // AlarmManager sends out broadcasts, and the receiver we've got
                // will wake the service back up, so we can stop everything right
                // now.
                break;
            case STOCK_ALARM:
            case STOCK_ALARM_RETRY:
            case STOCK_ALARM_NETWORK_BACK:
            case StockService.ACTION_STOCK_RESULT:
                // Aha!  NOW we've got something!
                Log.d(DEBUG_TAG, "AlarmService has business to attend to!");

                // If we've been told the network just came back, we can shut off
                // the network receiver.  If we're still in trouble network-wise,
                // it'll go right back on when we check in a second.
                if(intent.getAction().equals(STOCK_ALARM_NETWORK_BACK)) {
                    Log.d(DEBUG_TAG, "The network came back!  Yay!");
                    stopWaitingForNetwork();
                }

                // If we just got the stock alarm, we need to reschedule right away.
                if(intent.getAction().equals(STOCK_ALARM)) {
                    Log.d(DEBUG_TAG, "Rescheduling next STOCK_ALARM...");
                    setNextAlarm(true);
                }

                // If we got the REAL stock alarm while still waiting on the RETRY
                // alarm (i.e. the server kept reporting the stock wasn't posted all
                // day until the next 9:30), we should stop the retry alarm.  It'll
                // get set back up if the stock is STILL unavailable, and by
                // shutting it down here, we preferably avoid acting on two alarms
                // at the same time.
                mAlarmManager.cancel(PendingIntent.getBroadcast(this, 0, new Intent(STOCK_ALARM_RETRY).setClass(this, StockAlarmReceiver.class), 0));

                // StockService takes care of all the network connectivity checks
                // and other things that the alarm-checking StockService used to
                // take care of.  It'll also tell us if the stock hasn't been
                // posted just yet.  So, we can count on that for error checking.
                if(intent.getAction().equals(StockService.ACTION_STOCK_RESULT)) {
                    Log.d(DEBUG_TAG, "Just got a stock result!");

                    Bundle bun = intent.getBundleExtra(StockService.EXTRA_STUFF);
                    bun.setClassLoader(getClassLoader());

                    int result = bun.getInt(StockService.EXTRA_RESPONSE_CODE, StockService.RESPONSE_NOT_POSTED_YET);
                    Graticule g = bun.getParcelable(StockService.EXTRA_GRATICULE);

                    if(result == StockService.RESPONSE_NO_CONNECTION) {
                        // No connection means we just set up the receiver and wait.
                        // And wait.  And wait.
                        Log.d(DEBUG_TAG, "No network connection available, waiting until we get one...");

                        waitForNetwork();

                        clearNotification();
                        return;
                    }

                    if(result == StockService.RESPONSE_NOT_POSTED_YET) {
                        // Not posted yet means we hit the snooze and try again in a
                        // half hour or so.  Good night!
                        Log.d(DEBUG_TAG, "Stock wasn't posted yet, snoozing for a half hour...");
                        snooze();
                        clearNotification();
                        return;
                    }

                    if(result == StockService.RESPONSE_NETWORK_ERROR) {
                        // A network error that ISN'T "no connection" is usually
                        // really bad.  But, with Doze in effect, that might mean
                        // something weird with how it denies us network access, so
                        // let's just snooze for now.
                        Log.w(DEBUG_TAG, "Network reported an error, snoozing for a half hour...");
                        snooze();
                        clearNotification();
                        return;
                    }

                    if(result == StockService.RESPONSE_OKAY) {
                        // An okay response means the Graticule IS good.  If not,
                        // fix StockService.
                        if(g == null) {
                            Log.w(DEBUG_TAG, "g is somehow null in AlarmService?");
                            clearNotification();
                        } else if(g.uses30WRule()) {
                            // If the response we just checked for was a 30W one and
                            // it came back okay, then we fire off a check for the
                            // non-30W one.
                            Log.d(DEBUG_TAG, "That was the 30W response, going up to non-30W...");
                            sendRequest(GHDConstants.DUMMY_TODAY);
                        } else {
                            // If, however, we got the non-30W back, then our job is
                            // done!  Yay!
                            Log.d(DEBUG_TAG, "The 30W response!  We're done!");
                            clearNotification();

                            // And since it's done, we can go off to the part where
                            // we deal with KnownLocations!
                            doKnownLocations();
                        }
                    }
                } else {
                    // If it's NOT a result, that means we're starting a new check
                    // at a 30W hash for some reason.  Doesn't matter what reason.
                    // We just need to do it.
                    Log.d(DEBUG_TAG, "That wasn't a result, so asking for a 30W...");
                    sendRequest(GHDConstants.DUMMY_YESTERDAY);
                }
                break;
            default:
                // Stop doing this!
                Log.w(DEBUG_TAG, "Told to start on unknown action " + intent.getAction() + ", ignoring...");
                break;
        }
    }

    /**
     * Convenient container for all the data we need for matches.
     */
    private static class KnownLocationMatchData implements Comparable<KnownLocationMatchData> {
        public final KnownLocation knownLocation;
        public final Info bestInfo;
        public final double distance;

        public KnownLocationMatchData(@NonNull KnownLocation kl, @NonNull Info info, double dist) {
            knownLocation = kl;
            bestInfo = info;
            distance = dist;
        }

        @Override
        public int compareTo(@NonNull KnownLocationMatchData another) {
            // We want to sort this by how close it is.  The LOWEST number
            // should go first (that's the closest one).
            return Double.compare(distance, another.distance);
        }
    }

    private void doKnownLocations() {
        // First things first, clear out any old notifications.  If those are
        // still around, they're from previous days, so they're no longer valid.
        mNotificationManager.cancel(R.id.alarm_known_location);
        mNotificationManager.cancel(R.id.alarm_known_location_global);
        mNotificationManager.cancel(R.id.alarm_known_location_group);

        for(int id : mNotifyIds) {
            mNotificationManager.cancel(id);
        }

        String notifyPref = PreferenceManager.getDefaultSharedPreferences(this).getString(GHDConstants.PREF_KNOWN_NOTIFICATION, GHDConstants.PREFVAL_KNOWN_NOTIFICATION_ONLY_ONCE);

        // If the user doesn't want notifications, we can skip the rest of this.
        if(notifyPref.equals(GHDConstants.PREFVAL_KNOWN_NOTIFICATION_NEVER)) return;

        List<KnownLocation> locations = KnownLocation.getAllKnownLocations(this);

        // If there are no KnownLocations, give up now.
        if(locations.isEmpty()) return;

        List<KnownLocationMatchData> matched = new LinkedList<>();
        List<KnownLocationMatchData> matchedGlobal = new LinkedList<>();

        // There are some odd time zone implications here if "today" just comes
        // from Calendar.getInstance(), in that it sometimes might wind up
        // being "yesterday" if, for instance, you're in AEST (9:30am ET becomes
        // 11:30pm AEST).  So we need to adjust what date we're reporting if
        // we're looking at a 30W known location.  Anything 30W should be
        // "tomorrow", as the reason the 30W Rule was invented in the first
        // place was because at the open of the NYSE, it's already too late to
        // do anything with it out there.
        Calendar today = makeNineThirty(null);
        Calendar tomorrow = (Calendar)today.clone();
        tomorrow.add(Calendar.DATE, 1);

        Info global = HashBuilder.getStoredInfo(this, today, null);

        for(KnownLocation kl : locations) {
            // Every KnownLocation has a method to do this.  Maybe it's a wee
            // bit inefficient and inelegant, but it does the job.
            Info best;

            try {
                best = kl.getClosestInfo(this, (kl.is30w() ? tomorrow : today));
            } catch (IllegalArgumentException iae) {
                // This shouldn't happen under normal operation, but if this is
                // the debug build and the party alarm's been triggered,
                // makeNineThirty might refer to tomorrow (i.e. if the time zone
                // is anywhere west of EST/EDT), which may not have a valid
                // stock yet.  In that case, silently drop it and continue
                // onward.  Chances are we'll skip all the known locations past
                // this one anyway.
                continue;
            }

            if(kl.isCloseEnough(best.getFinalDestinationLatLng())) {
                KnownLocationMatchData data = new KnownLocationMatchData(kl, best, kl.getDistanceFrom(best));
                matched.add(data);
            }

            // The Globalhash will be handled as a separate notification,
            // because frankly, that's sort of special.
            if(global != null && kl.isCloseEnough(global.getFinalDestinationLatLng())) {
                KnownLocationMatchData data = new KnownLocationMatchData(kl, global, kl.getDistanceFrom(global));
                matchedGlobal.add(data);
            }
        }

        // Did we get anything?  Anything AT ALL?
        if(!matched.isEmpty()) {
            // A match!  First, we need the group summary notification...
            NotificationCompat.Builder groupBuilder = new NotificationCompat.Builder(this, GHDConstants.CHANNEL_NEARBY_POINTS)
                    .setGroupSummary(true)
                    .setGroup(NOTIFICATION_GROUP_LOCAL)
                    .setSmallIcon(R.drawable.ic_stat_av_new_releases)
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .setLights(Color.WHITE, 500, 2000)
                    .setContentText(getResources().getQuantityString(R.plurals.known_locations_alarm_group_text, matched.size(), matched.size()))
                    .setContentTitle(getResources().getQuantityString(R.plurals.known_locations_alarm_group_title, matched.size(), matched.size()))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
            mNotificationManager.notify(R.id.alarm_known_location_group, groupBuilder.build());

            // In any case, the matched selections need to be sorted out for
            // some reason.
            Collections.sort(matched);

            // So now we have a list of what matched.  From there, let's sort
            // out what notifications need to go up, if any.  There's a
            // preference for this sort of thing, and we already checked it
            // earlier.
            switch(notifyPref) {
                case GHDConstants.PREFVAL_KNOWN_NOTIFICATION_ONLY_ONCE: {
                    // Only once.  That is, classic style.
                    launchNotification(matched, START_INFO, R.id.alarm_known_location, R.string.known_locations_alarm_title, LOCAL_NOTIFICATION);
                    break;
                }
                case GHDConstants.PREFVAL_KNOWN_NOTIFICATION_PER_GRATICULE: {
                    // Once per Graticule.  Well, now we need to sift through
                    // the matches and separate them out by Graticule.  However,
                    // since we still want to limit the number of notifications,
                    // we still want only the Graticules whose matching Known
                    // Locations are the closest to their respective points.
                    // That'll make more sense when you read the code.  I hope.

                    // Our list of matches is already sorted, so the order in
                    // which we add Graticules matches which Graticules have the
                    // closest matches.  LinkedHashMap is what we want.
                    Map<Graticule, List<KnownLocationMatchData>> byGraticule = new LinkedHashMap<>();

                    for(KnownLocationMatchData single : matched) {
                        Graticule matchGrat = single.bestInfo.getGraticule();
                        if(!byGraticule.containsKey(matchGrat)) {
                            // We haven't added this Graticule yet.  Let's add
                            // it to the map.
                            byGraticule.put(matchGrat, new LinkedList<>());
                        }

                        // Add it in!
                        byGraticule.get(matchGrat).add(single);
                    }

                    // Okay, now we have an in-order map associating Graticules
                    // to a list of corresponding KnownLocations, sorted by
                    // their respective distances from the best Infos they have.
                    // For convenience, let's make a list of lists.  By
                    // "convenience", I mean the Map interface doesn't make it
                    // clear how to iterate it in such a way that I can remove
                    // specific indexed entries on the fly, which I guess makes
                    // sense because that's not what a Map is there to do.
                    List<List<KnownLocationMatchData>> byGraticuleList = new LinkedList<>();

                    for(Map.Entry<Graticule, List<KnownLocationMatchData>> entry : byGraticule.entrySet()) {
                        byGraticuleList.add(entry.getValue());
                    }

                    // From here on out, the logic is mostly the same as in the
                    // per-location part.  Sort of.  I hope it's not too obvious
                    // I wrote and commented that part first.
                    int i;
                    for(i = 0; i < mNotifyIds.length - 1; i++) {
                        if(byGraticuleList.isEmpty()) break;

                        List<KnownLocationMatchData> match = byGraticuleList.remove(0);
                        launchNotification(match, START_INFO, mNotifyIds[i], R.string.known_locations_alarm_title, LOCAL_NOTIFICATION);

                    }

                    // If anything's left in that list-of-lists, flatten it out
                    // before sending it on its way.  Note that we don't have
                    // stream() at our disposal due to API level.
                    if(!byGraticuleList.isEmpty()) {
                        List<KnownLocationMatchData> remaining = new LinkedList<>();
                        for(List<KnownLocationMatchData> match : byGraticuleList) {
                            remaining.addAll(match);
                        }
                        launchNotification(remaining, START_INFO, mNotifyIds[i], R.string.known_locations_alarm_title, LOCAL_NOTIFICATION);
                    }

                    break;
                }
                case GHDConstants.PREFVAL_KNOWN_NOTIFICATION_PER_LOCATION: {
                    // Once per matched location?  Well, sure, but that might
                    // throw up a lot of notifications.  So, let's limit that
                    // number to however many IDs we have in reserve.  We're not
                    // monsters, after all.  Note that we're working off of one
                    // LESS than the length of notifyIds.  You'll see why in a
                    // sec.  Trust me.
                    int i;
                    for(i = 0; i < mNotifyIds.length - 1; i++) {
                        if(matched.isEmpty()) break;

                        List<KnownLocationMatchData> single = new LinkedList<>();
                        single.add(matched.remove(0));
                        // Weird how this isn't causing the @IdRes annotation to
                        // throw a fit...
                        launchNotification(single, START_INFO, mNotifyIds[i], R.string.known_locations_alarm_title, LOCAL_NOTIFICATION);
                    }

                    // If matched didn't wind up empty, add the remainder of it
                    // to the notifications.  If it was one entry, it'll be a
                    // plain ol' notification like the others.  If it was more,
                    // it'll be a spillover, just like in only-once mode.
                    if(!matched.isEmpty())
                        launchNotification(matched, START_INFO, mNotifyIds[i], R.string.known_locations_alarm_title, LOCAL_NOTIFICATION);

                    break;
                }
            }
        }

        // Now, the Globalhash notification gets tossed up regardless of the
        // user's preferences (apart from "Never").  And for now, always a
        // single notification.
        if(!matchedGlobal.isEmpty()) {
            Collections.sort(matchedGlobal);
            launchNotification(matchedGlobal, START_INFO_GLOBAL, R.id.alarm_known_location_global, R.string.known_locations_alarm_title_global, GLOBAL_NOTIFICATION);
        }
    }

    private void launchNotification(@NonNull List<KnownLocationMatchData> matched,
                                    @NonNull String action,
                                    @IdRes int notificationId,
                                    @StringRes int titleId,
                                    int requestCode) {
        // First one's the winner!  We know this because this is a private
        // method so we all know matched WAS sorted ahead of time, right?
        // RIGHT?  Seriously, do so.
        NotificationCompat.Builder builder = getFreshNotificationBuilder(matched, titleId);

        if(requestCode == LOCAL_NOTIFICATION)
            builder.setGroup(NOTIFICATION_GROUP_LOCAL);

        Bundle bun = new Bundle();
        bun.putParcelable(StockService.EXTRA_INFO, matched.get(0).bestInfo);

        Intent intent = new Intent(this, CentralMap.class)
                .setAction(action)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(StockService.EXTRA_STUFF, bun);

        builder.setContentIntent(PendingIntent.getActivity(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT));

        mNotificationManager.notify(notificationId, builder.build());
    }

    private NotificationCompat.Builder getFreshNotificationBuilder(@NonNull List<KnownLocationMatchData> data,
                                                             @StringRes int titleId) {
        KnownLocationMatchData match = data.get(0);
        String contentText = getString(R.string.known_locations_alarm_distance,
                UnitConverter.makeDistanceString(this, UnitConverter.DISTANCE_FORMAT_SHORT, (float)match.distance),
                match.knownLocation.getName());
        String summaryText = getResources().getQuantityString(R.plurals.known_locations_alarm_more, data.size() - 1, data.size() - 1);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, GHDConstants.CHANNEL_NEARBY_POINTS)
                .setSmallIcon(R.drawable.ic_stat_av_new_releases)
                .setAutoCancel(true)
                .setOngoing(false)
                .setLights(Color.WHITE, 500, 2000)
                .setContentText(contentText)
                .setContentTitle(getString(titleId, DateFormat.getDateInstance(DateFormat.MEDIUM).format(match.bestInfo.getDate())))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

        // If there's more than one known location nearby, make the notification
        // expandable with a bit of extra text mentioning just how many more.
        if(data.size() > 1) {
            builder.setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(contentText)
                    .setSummaryText(summaryText));
        }

        return builder;
    }

    private void waitForNetwork() {
        // SDK check!  We'll go with JobScheduler if we can.
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // JobScheduler time!  It's fancier!
            JobScheduler js = (JobScheduler)getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if(js != null) {
                JobInfo job = new JobInfo.Builder(
                        ALARM_CONNECTIVITY_JOB,
                        new ComponentName(this, AlarmServiceJobService.class))
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build();
                js.schedule(job);
            } else {
                Log.e(DEBUG_TAG, "Couldn't get a JobScheduler instance when scheduling the connectivity check!  THIS IS BAD, AND WE WON'T GET NETWORK UPDATES!");
            }
        } else {
            // Otherwise, just use the ol' package component.
            AndroidUtil.setPackageComponentEnabled(this, NetworkReceiver.class, true);
        }
    }

    private void stopWaitingForNetwork() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            JobScheduler js = (JobScheduler)getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if(js != null) {
                js.cancel(ALARM_CONNECTIVITY_JOB);
            } else {
                Log.e(DEBUG_TAG, "Couldn't get a JobScheduler instance when stopping the connectivity check!  THIS IS BAD!");
            }
        } else {
            AndroidUtil.setPackageComponentEnabled(this, NetworkReceiver.class, false);
        }
    }

    private int[] getNotifyIds() {
        // We can't put IDs in an XML-defined integer-array, so we have to grab
        // them like this.  Because I like using auto-generated IDs for things
        // like this, that's why, and I might change the number of notification
        // IDs at some point down the line.
        List<Integer> idList = new LinkedList<>();
        int lastId;
        int curIndex = 0;
        do {
            lastId = getResources().getIdentifier("alarm_known_location_multi_" + curIndex, "id", this.getPackageName());
            if(lastId != 0) {
                idList.add(lastId);
                curIndex++;
            }
        } while(lastId != 0);

        int[] notifyIds = new int[idList.size()];
        curIndex = 0;
        for(Integer in : idList) {
            notifyIds[curIndex] = in;
            curIndex++;
        }

        return notifyIds;
    }
}
