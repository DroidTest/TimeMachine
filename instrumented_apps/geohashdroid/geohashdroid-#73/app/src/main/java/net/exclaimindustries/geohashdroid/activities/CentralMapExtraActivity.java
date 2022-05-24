/*
 * CentralMapExtraActivity.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.activities;

import android.Manifest;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.fragments.CentralMapExtraFragment;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.tools.AndroidUtil;

import java.text.DateFormat;

/**
 * Similar to <code>CentralMapExtraFragment</code>, <code>CentralMapExtraActivity</code>
 * encompasses the common methods to all Activities that hold those Fragments on
 * phone layouts.  Turns out those Activities are really very simple and share
 * quite a lot in common.
 */
public abstract class CentralMapExtraActivity extends BaseGHDThemeActivity
        implements CentralMapExtraFragment.CloseListener {
    /**
     * The key for the Intent extra containing the Info object.  This should
     * very seriously NOT be null.  If it is, you did something very wrong.
     */
    public static final String INFO = "info";

    private CentralMapExtraFragment mFrag;
    private FusedLocationProviderClient mFusedProviderClient;

    protected Info mInfo;

    private LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            // When we get a location, let the fragment know.  We're sort of
            // acting like an ersatz ExpeditionMode at this point.
            mFrag.onLocationChanged(locationResult.getLastLocation());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(getLayoutResource());

        // Grab the fragment.  We know it's there, it's right there in the
        // layout.
        FragmentManager manager = getFragmentManager();
        mFrag = (CentralMapExtraFragment) manager.findFragmentById(getFragmentResource());

        // We'd BETTER have an Intent.
        Intent intent = getIntent();

        // And that intent BETTER have an Info.
        mInfo = intent.getParcelableExtra(INFO);

        // Since the fragment's part of the layout, we can't set an argument
        // anymore.  So, just update the Info.
        mFrag.setCloseListener(this);
        mFrag.setInfo(mInfo);

        // We'll need a location provider.  CentralMap's won't be around.
        mFusedProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // Start listening now.  We used to have to wait for the client, but
        // nowadays, nope.
        startListening();
    }

    @Override
    protected void onStop() {
        // Listener goes down!
        stopListening();

        super.onStop();
    }

    /**
     * Returns the resource of the options menu this Activity uses.
     *
     * @return a menu's resource ID
     */
    protected abstract int getMenuResource();

    /**
     * Returns the resource of the Fragment this Activity deals with.
     *
     * @return a Fragment's resource ID
     */
    protected abstract int getFragmentResource();

    /**
     * Returns the resource of the layout this Activity wants.
     *
     * @return a layout's resource ID
     */
    protected abstract int getLayoutResource();

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(getMenuResource(), menu);

        // Now, some menu items may not be available if we can't get to them.
        // Like, for instance, Send To Maps.  If Google Maps (or anything that
        // can receive the Intent) isn't there, we can't do that.
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse("geo:0,0?q=loc:0,0"));
        if(!AndroidUtil.isIntentAvailable(this, i))
            menu.removeItem(R.id.action_send_to_maps);

        // Or the Radar intent.
        if(!AndroidUtil.isIntentAvailable(this, GHDConstants.ACTION_SHOW_RADAR))
            menu.removeItem(R.id.action_send_to_radar);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_wiki: {
                // Wiki time!  Wiki time!  It's time for wiki time!
                if(mInfo != null) {
                    // Since we're in the activity version of Detailed Info, we
                    // know we're just starting the wiki activity.
                    Intent i = new Intent(this, WikiActivity.class);
                    i.putExtra(WikiActivity.INFO, mInfo);
                    startActivity(i);
                } else {
                    Toast.makeText(this, R.string.error_no_data_to_wiki, Toast.LENGTH_LONG).show();
                }

                return true;
            }
            case R.id.action_preferences: {
                // We've got preferences, so we've got an Activity.
                Intent i = new Intent(this, PreferencesScreen.class);
                startActivity(i);
                return true;
            }
            case R.id.action_send_to_maps: {
                if(mInfo != null) {
                    // To the map!
                    Intent i = new Intent();
                    i.setAction(Intent.ACTION_VIEW);

                    // Assemble the location.  This is a simple latitude,longitude
                    // setup.
                    String location = mInfo.getLatitude() + "," + mInfo.getLongitude();

                    // Then, toss the location out the door and hope whatever map
                    // we're using is paying attention.
                    i.setData(Uri.parse("geo:0,0?q=loc:"
                            + location
                            + "("
                            + this.getString(
                            R.string.send_to_maps_point_name,
                            DateFormat.getDateInstance(DateFormat.LONG).format(
                                    mInfo.getCalendar().getTime())) + ")&z=15"));
                    startActivity(i);
                } else {
                    Toast.makeText(this, R.string.error_no_data_to_maps, Toast.LENGTH_LONG).show();
                }

                return true;
            }
            case R.id.action_send_to_radar: {
                if(mInfo != null) {
                    Intent i = new Intent(GHDConstants.ACTION_SHOW_RADAR);
                    i.putExtra("latitude", (float) mInfo.getLatitude());
                    i.putExtra("longitude", (float) mInfo.getLongitude());
                    startActivity(i);
                } else {
                    Toast.makeText(this, R.string.error_no_data_to_radar, Toast.LENGTH_LONG).show();
                }

                return true;
            }
        }

        return false;
    }

    @Override
    public void extraFragmentClosing(CentralMapExtraFragment fragment) {
        // Easy enough, just finish the Activity.
        finish();
    }

    @Override
    public void extraFragmentDestroying(CentralMapExtraFragment fragment) {
        // Nothing happens here; we're already on our way out.
    }

    private void startListening() {
        // While starting up, we also report to the fragment what happened with
        // the permissions checks.
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mFrag.permissionsDenied(false);
            LocationRequest lRequest = LocationRequest.create();
            lRequest.setInterval(1000);
            lRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

            mFusedProviderClient.requestLocationUpdates(lRequest, mLocationCallback, null);
        } else {
            mFrag.permissionsDenied(true);
        }
    }

    private void stopListening() {
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mFusedProviderClient.removeLocationUpdates(mLocationCallback);
        }
    }
}
