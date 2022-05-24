package net.exclaimindustries.tools;

import java.util.List;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * AndroidUtil features some helpful convenience methods for Android.
 * 
 * @author Jonas Graudums, Nicholas Killewald
 */
public class AndroidUtil {

    /**
     * Indicates whether the specified action can be used as an intent. This
     * method queries the package manager for installed packages that can
     * respond to an intent with the specified action. If no suitable package is
     * found, this method returns false.
     * 
     * @param context The application's environment.
     * @param action The Intent action to check for availability.
     * 
     * @return True if an Intent with the specified action can be sent and
     *         responded to, false otherwise.
     */
    public static boolean isIntentAvailable(Context context, String action) {
        final Intent intent = new Intent(action);
        return isIntentAvailable(context, intent);
    }

    /**
     * Indicates whether the specified Intent has any Activity that will respond
     * to it.  This comes into play if you need to check against an Intent with
     * a more specific specifier than just the action (i.e. an ACTION_VIEW
     * Intent, where the given URI determines what starts it).
     *
     * @param context the application's environment
     * @param intent the Intent to check against
     * @return true if an Activity would respond to that Intent, false otherwise
     */
    public static boolean isIntentAvailable(Context context, Intent intent) {
        final PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> list = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    /**
     * <p>
     * Indicates whether there's any valid internet connection at all.  This
     * call doesn't care if said connection is wifi, mobile, Bluetooth, or
     * whatever.
     * </p>
     *
     * <p>
     * TODO: This might benefit from some corresponding methods that DO narrow
     * things down a bit, like making sure it's a high-bandwidth connection.
     * </p>
     *
     * @param context a Context, needed to get the ConnectivityManager
     * @return true if an internet connection exists, false otherwise
     */
    public static boolean isConnected(Context context) {
        NetworkInfo networkInfo = ((ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Enables or disables a given component class.  So, like a manifest-defined
     * BroadcastReceiver or whatnot.
     *
     * @param context the Context to which the component belongs
     * @param cls the component's class
     * @param enabled true to enable, false to disable
     */
    public static void setPackageComponentEnabled(Context context, Class<?> cls, boolean enabled) {
        ComponentName receiver = new ComponentName(context, cls);
        PackageManager pm = context.getPackageManager();

        pm.setComponentEnabledSetting(receiver,
                (enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
                PackageManager.DONT_KILL_APP);
    }
}
