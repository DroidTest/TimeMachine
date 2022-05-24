/*
 * KnownLocationsPicker.java
 * Copyright (C) 2016 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.activities;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.backup.BackupManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.VisibleRegion;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.KnownLocation;
import net.exclaimindustries.geohashdroid.util.KnownLocationPinData;
import net.exclaimindustries.geohashdroid.util.UnitConverter;

import org.opensextant.geodesy.Angle;
import org.opensextant.geodesy.Geodetic2DArc;
import org.opensextant.geodesy.Geodetic2DPoint;
import org.opensextant.geodesy.Latitude;
import org.opensextant.geodesy.Longitude;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * KnownLocationsPicker is another map-containing Activity.  This one allows the
 * user to set "known locations", which can trigger notifications if the day's
 * hashpoint is near one of them.  This isn't CentralMap, mind; it doesn't have
 * the entire CentralMapMode architecture or stock-grabbing functionality.  And
 * for that, I'm thankful.
 */
public class KnownLocationsPicker
        extends BaseMapActivity
        implements GoogleMap.OnMapLongClickListener,
                   GoogleMap.OnMarkerClickListener,
                   GoogleMap.OnInfoWindowClickListener,
                   Handler.Callback {
    private static final String DEBUG_TAG = "KnownLocationsPicker";

    // These get passed into the dialog.
    private static final String NAME = "name";
    private static final String LATLNG = "latLng";
    private static final String RANGE = "range";
    private static final String RESTRICT = "restrict";
    private static final String EXISTING = "existing";
    private static final String ADDRESS = "address";

    // This is for restoring the map from an instance bundle.
    private static final String CLICKED_MARKER = "clickedMarker";
    private static final String LAST_ADDRESSES = "lastAddresses";
    private static final String RELOADING = "reloading";

    private static final String EDIT_DIALOG = "editDialog";

    /** Response codes from LocationSearchTask. */
    public enum LookupErrorCode {
        /**
         * All is well, results are to follow.
         */
        OKAY,
        /**
         * All is well, but there were no results.
         */
        NO_RESULTS,
        /**
         * An I/O error occurred (probably no network connection).
         */
        IO_ERROR,
        /**
         * No geocoder is installed (and it's weird that we got this far).
         */
        NO_GEOCODER,
        /**
         * Some manner of internal error occurred.
         */
        INTERNAL_ERROR,
        /**
         * This search was actually canceled, so ignore it.
         */
        CANCELED
    }

    /**
     * This dialog pops up when either adding or editing a KnownLocation.
     */
    public static class EditKnownLocationDialog extends DialogFragment {
        private LatLng mLocation;
        private KnownLocation mExisting;
        private Address mAddress;

        @Override
        @SuppressLint("InflateParams")
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // It's either this or we make a callback mechanism for something
            // that literally only gets used once.
            if(!(getActivity() instanceof KnownLocationsPicker))
                throw new IllegalStateException("An EditKnownLocationDialog can only be instantiated from the KnownLocationsPicker activity!");

            final KnownLocationsPicker pickerActivity = (KnownLocationsPicker)getActivity();

            // The arguments MUST be defined, else we're not doing anything.
            Bundle args = getArguments();
            if(args == null || !args.containsKey(RANGE) || !args.containsKey(LATLNG) || !args.containsKey(NAME)) {
                throw new IllegalArgumentException("Missing arguments to EditKnownLocationDialog!");
            }

            // Time to inflate!
            LayoutInflater inflater = ((LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE));

            View dialogView = inflater.inflate(R.layout.edit_known_location_dialog, null);

            String name;
            int range;
            boolean restrict;

            // Right!  Go to the arguments first.
            name = args.getString(NAME);
            range = convertRangeToPosition(args.getDouble(RANGE));
            restrict = args.getBoolean(RESTRICT);
            mExisting = args.getParcelable(EXISTING);
            mAddress = args.getParcelable(ADDRESS);
            mLocation = args.getParcelable(LATLNG);

            // If there's a saved instance state, that overrides the name and
            // range.  Not the location, though.  That's locked in at this
            // point.
            if(savedInstanceState != null) {
                name = savedInstanceState.getString(NAME);
                range = savedInstanceState.getInt(RANGE);
                restrict = savedInstanceState.getBoolean(RESTRICT);
            }

            // Now then!  Let's create this mess.  First, if this is a location
            // that already exists, the user can delete it.  Otherwise, that
            // button goes away.
            View deleteButton = dialogView.findViewById(R.id.delete_location);
            if(mExisting == null) {
                deleteButton.setVisibility(View.GONE);
            } else {
                deleteButton.setOnClickListener(v -> {
                    pickerActivity.deleteActiveKnownLocation(mExisting);
                    dismiss();
                });
            }

            // The input takes on whatever name it needs to.
            final EditText nameInput = dialogView.findViewById(R.id.input_location_name);
            nameInput.setText(name);

            // The spinner needs an adapter.  Fortunately, a basic one will do,
            // as soon as we figure out what units we're using.
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(pickerActivity);
            String units = prefs.getString(GHDConstants.PREF_DIST_UNITS, GHDConstants.PREFVAL_DIST_METRIC);
            ArrayAdapter<CharSequence> adapter;

            if(units.equals(GHDConstants.PREFVAL_DIST_METRIC))
                adapter = ArrayAdapter.createFromResource(pickerActivity, R.array.known_locations_ranges_metric, android.R.layout.simple_spinner_item);
            else
                adapter = ArrayAdapter.createFromResource(pickerActivity, R.array.known_locations_ranges_imperial, android.R.layout.simple_spinner_item);

            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

            final Spinner spinner = dialogView.findViewById(R.id.spinner_location_range);
            spinner.setAdapter(adapter);
            spinner.setSelection(range);

            final CheckBox restrictBox = dialogView.findViewById(R.id.restrict);
            restrictBox.setChecked(restrict);

            // There!  Now, let's make it a dialog.
            return new AlertDialog.Builder(pickerActivity)
                    .setView(dialogView)
                    .setTitle(mExisting != null ? R.string.known_locations_title_edit : R.string.known_locations_title_add)
                    .setPositiveButton(R.string.ok_label, (dialog, which) -> {
                        // There HAS to be a better way to do this...
                        if(mAddress != null)
                            pickerActivity.confirmKnownLocationFromDialog(
                                    nameInput.getText().toString(),
                                    mLocation,
                                    convertPositionToRange(spinner.getSelectedItemPosition()),
                                    restrictBox.isChecked(),
                                    mAddress);
                        else
                            pickerActivity.confirmKnownLocationFromDialog(
                                    nameInput.getText().toString(),
                                    mLocation,
                                    convertPositionToRange(spinner.getSelectedItemPosition()),
                                    restrictBox.isChecked(),
                                    mExisting);
                        dismiss();
                    })
                    .setNegativeButton(R.string.cancel_label, (dialog, which) -> {
                        pickerActivity.removeActiveKnownLocation();
                        dismiss();
                    })
                    .create();
        }

        private double convertPositionToRange(int id) {
            return (double)getResources().getIntArray(R.array.known_locations_values)[id];
        }

        private int convertRangeToPosition(double range) {
            int pos = 0;
            for(int i : getResources().getIntArray(R.array.known_locations_values)) {
                if(range <= i)
                    return pos;

                pos++;
            }

            return pos;
        }
    }

    private static class LocationSearchTask extends AsyncTask<String, Void, LookupErrorCode> {
        static final class ResultObject {
            LookupErrorCode code;
            List<Address> addresses;
        }

        private List<Address> mAddresses;
        private VisibleRegion mVis;
        private float mBearing;
        private Geocoder mGeocoder;
        private Message mMessage;

        public LocationSearchTask(Message message, Geocoder geocoder, VisibleRegion vis, float bearing) {
            super();

            mMessage = message;
            mGeocoder = geocoder;
            mVis = vis;
            mBearing = bearing;
        }

        @Override
        protected LookupErrorCode doInBackground(String... params) {
            if(mGeocoder == null) return LookupErrorCode.NO_GEOCODER;

            mAddresses = new ArrayList<>();
            LookupErrorCode toReturn = LookupErrorCode.OKAY;

            // As initial tests proved, we really should try to narrow down the
            // location to roughly where the user is looking at the time.
            // Remember that the projection can do all sorts of crazy stuff, so
            // let's get the biggest rectangle we can from there.
            double lowerLeftLat, lowerLeftLon, upperRightLat, upperRightLon;

            // All we need is more or less an estimate of what the proper
            // rectangle is.  Since we have the visible region AND we know what
            // the rotation is, we can guess at a decent rectangle quickly.  And
            // more than a bit hackishly.  Come with me on this journey.
            if(mBearing >= 0.0f && mBearing < 45.0f) {
                // 0 - 45: The near-left and far-right coordinates are directly
                // what we want, more or less.
                lowerLeftLat = mVis.nearLeft.latitude;
                lowerLeftLon = mVis.nearLeft.longitude;
                upperRightLat = mVis.farRight.latitude;
                upperRightLon = mVis.farRight.longitude;
            } else if(mBearing >= 45.0f && mBearing < 90.0f) {
                // 45 - 90: Near-left works for the left boundary, but we need
                // near-right for the bottom.  Similarly, far-left is the top
                // and far-right is the right.
                lowerLeftLat = mVis.nearRight.latitude;
                lowerLeftLon = mVis.nearLeft.longitude;
                upperRightLat = mVis.farLeft.latitude;
                upperRightLon = mVis.farRight.longitude;
            } else if(mBearing >= 90.0f && mBearing < 135.0f) {
                // And we continue rotating in that manner.
                lowerLeftLat = mVis.nearRight.latitude;
                lowerLeftLon = mVis.nearRight.longitude;
                upperRightLat = mVis.farLeft.latitude;
                upperRightLon = mVis.farLeft.longitude;
            } else if(mBearing >= 135.0f && mBearing < 180.0f) {
                lowerLeftLat = mVis.farRight.latitude;
                lowerLeftLon = mVis.nearRight.longitude;
                upperRightLat = mVis.nearLeft.latitude;
                upperRightLon = mVis.farLeft.longitude;
            } else if(mBearing >= 180.0f && mBearing < 225.0f) {
                lowerLeftLat = mVis.farRight.latitude;
                lowerLeftLon = mVis.farRight.longitude;
                upperRightLat = mVis.nearLeft.latitude;
                upperRightLon = mVis.nearLeft.longitude;
            } else if(mBearing >= 225.0f && mBearing < 270.0f) {
                lowerLeftLat = mVis.farLeft.latitude;
                lowerLeftLon = mVis.farRight.longitude;
                upperRightLat = mVis.nearRight.latitude;
                upperRightLon = mVis.nearLeft.longitude;
            } else if(mBearing >= 270.0f && mBearing < 315.0f) {
                lowerLeftLat = mVis.farLeft.latitude;
                lowerLeftLon = mVis.farLeft.longitude;
                upperRightLat = mVis.nearRight.latitude;
                upperRightLon = mVis.nearRight.longitude;
            } else {
                lowerLeftLat = mVis.nearLeft.latitude;
                lowerLeftLon = mVis.farRight.longitude;
                upperRightLat = mVis.farRight.latitude;
                upperRightLon = mVis.nearLeft.longitude;
            }

            // I really hope we're not calling this with a bunch of Strings, but
            // sure, let's be defensive, why not?
            try {
                for(String s : params) {
                    if(isCancelled()) return LookupErrorCode.CANCELED;

                    List<Address> result = mGeocoder.getFromLocationName(
                            s,
                            10,
                            lowerLeftLat,
                            lowerLeftLon,
                            upperRightLat,
                            upperRightLon);

                    if(isCancelled()) return LookupErrorCode.CANCELED;

                    // If there was no result, well, broaden the search.
                    if(result == null || result.isEmpty())
                        result = mGeocoder.getFromLocationName(s, 10);

                    if(isCancelled()) return LookupErrorCode.CANCELED;

                    if(result != null)
                        mAddresses.addAll(result);
                }
            } catch (IOException ioe) {
                toReturn = LookupErrorCode.IO_ERROR;
            } catch (IllegalArgumentException iae) {
                toReturn = LookupErrorCode.INTERNAL_ERROR;
            }

            // Remember, we're returning the error code, not the list of
            // addresses, since we want to report that error if need be.
            if(mAddresses.isEmpty())
                toReturn = LookupErrorCode.NO_RESULTS;

            // Last chance for the cancel check...
            if(isCancelled()) return LookupErrorCode.CANCELED;

            return toReturn;
        }

        @Override
        protected void onPostExecute(LookupErrorCode code) {
            if(code != LookupErrorCode.CANCELED) {
                // Got a response!  Send it back!
                ResultObject obj = new ResultObject();
                obj.code = code;
                obj.addresses = mAddresses;

                mMessage.obj = obj;
                mMessage.sendToTarget();
            }
        }
    }

    private Geocoder mGeocoder;
    private LocationSearchTask mSearchTask;

    private boolean mMapIsReady = false;
    private boolean mLayoutComplete = false;
    private boolean mAlreadyLaidOut = false;
    private boolean mReloaded = false;

    private BiMap<Marker, KnownLocation> mMarkerMap;
    private BiMap<Circle, KnownLocation> mCircleMap;

    private List<KnownLocation> mLocations;
    private Marker mMapClickMarker;
    private MarkerOptions mMapClickMarkerOptions;

    private List<Address> mActiveAddresses;
    private BiMap<Marker, Address> mActiveAddressMap;

    private Marker mActiveMarker;

    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // We've got a layout, so let's use the layout.
        setContentView(R.layout.known_locations);

        // Now, we'll need to get the list of KnownLocations right away so we
        // can put them on the map.  Well, I guess not RIGHT away.  We still
        // have to wait on the map callbacks, but still, let's fetch them now.
        mLocations = KnownLocation.getAllKnownLocations(this);

        // We need maps.
        mMarkerMap = HashBiMap.create();
        mCircleMap = HashBiMap.create();

        // We need a Geocoder!  Well, not really; if we can't get one, remove
        // the search option.
        if(Geocoder.isPresent()) {
            mGeocoder = new Geocoder(this);

            // A valid Geocoder also means we can attach the click listener.
            final EditText input = findViewById(R.id.search);
            final View go = findViewById(R.id.search_go);
            go.setOnClickListener(v -> searchForLocation(input.getText().toString()));

            input.setOnEditorActionListener((v, actionId, event) -> {
                if(actionId == EditorInfo.IME_ACTION_GO) {
                    searchForLocation(v.getText().toString());
                    return true;
                }

                return false;
            });

        } else {
            findViewById(R.id.search_box).setVisibility(View.GONE);
        }

        // Our friend the map needs to get ready, too.
        MapFragment mapFrag = (MapFragment)getFragmentManager().findFragmentById(R.id.map);
        mapFrag.getMapAsync(googleMap -> {
            mMap = googleMap;

            // I could swear you could do this in XML...
            UiSettings set = mMap.getUiSettings();

            // The My Location button has to go off, as the search bar sort
            // of takes up that space.
            set.setMyLocationButtonEnabled(false);

            // Also, get rid of the map toolbar.  That just doesn't make any
            // sense here if we've already got a search widget handy.
            set.setMapToolbarEnabled(false);

            // Get ready to listen for clicks!
            mMap.setOnMapLongClickListener(KnownLocationsPicker.this);
            mMap.setOnInfoWindowClickListener(KnownLocationsPicker.this);

            // Were we waiting on a long-tapped marker?
            if(mMapClickMarkerOptions != null) {
                // Well, then put the marker back on the map!
                mMapClickMarker = mMap.addMarker(mMapClickMarkerOptions);
                mActiveMarker = mMapClickMarker;
                mMapClickMarker.showInfoWindow();
            }

            // Should this be the night map?   Maybe I'll add in the full
            // map type picker later, but for now, it's just the day or
            // night street map.
            if(isNightMode())
                if(!mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(KnownLocationsPicker.this, R.raw.map_night)))
                    Log.e(DEBUG_TAG, "Couldn't parse the map style JSON!");

            // Activate My Location if permissions are right.
            if(checkLocationPermissions(0))
                permissionsGranted();

            // Since there's only one ready check to wait on now, this
            // should kick things in motion.
            mMapIsReady = true;
            doReadyChecks();
        });

        // The map also needs to be laid out before we act on it.
        mapFrag.getView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            // Got a height!  Hopefully.
            if(!mAlreadyLaidOut) {
                mAlreadyLaidOut = true;

                // Flag!
                mLayoutComplete = true;
                doReadyChecks();
            }
        });

        // Now, this only kicks in if stock pre-fetching is on.  If it isn't, we
        // ought to make sure the user knows this.
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if(!prefs.getBoolean(GHDConstants.PREF_STOCK_ALARM, false)
                && !prefs.getBoolean(GHDConstants.PREF_STOP_BUGGING_ME_PREFETCH_WARNING, false)) {
            // Dialog!
            new AlertDialog.Builder(this)
                    .setMessage(R.string.known_locations_prefetch_is_off)
                    .setNegativeButton(R.string.stop_reminding_me_label, (dialog, which) -> {
                        dialog.dismiss();

                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean(GHDConstants.PREF_STOP_BUGGING_ME_PREFETCH_WARNING, true);
                        editor.apply();

                        BackupManager bm = new BackupManager(KnownLocationsPicker.this);
                        bm.dataChanged();
                    })
                    .setNeutralButton(R.string.go_to_preference, (dialog, which) -> {
                        dialog.dismiss();

                        Intent intent = new Intent(KnownLocationsPicker.this, PreferencesScreen.class);
                        intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT, PreferencesScreen.OtherPreferenceFragment.class.getName());
                        intent.putExtra(PreferenceActivity.EXTRA_NO_HEADERS, true);
                        startActivity(intent);
                    })
                    .setPositiveButton(R.string.gotcha_label, (dialog, which) -> dialog.dismiss())
                    .show();
        }

        // Now, in an effort to make this less leaky, let's make us a Handler to
        // handle things on the UI thread.
        mHandler = new Handler(this);
    }

    @Override
    protected void onStop() {
        if(mSearchTask != null)
            mSearchTask.cancel(true);

        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(RELOADING, true);

        // If we were looking at a click marker, hold on to it.
        if(mMapClickMarkerOptions != null) {
            outState.putParcelable(CLICKED_MARKER, mMapClickMarkerOptions);
        }

        if(mActiveAddresses != null) {
            // mActiveAddresses is a List, not an ArrayList, so we have to do
            // this manually.
            Address addresses[] = new Address[mActiveAddresses.size()];
            outState.putParcelableArray(LAST_ADDRESSES, mActiveAddresses.toArray(addresses));
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        if(savedInstanceState != null) {
            mReloaded = savedInstanceState.getBoolean(RELOADING, false);

            // Did we have a click marker?  Once the map's ready, we'll put it
            // back in place.
            if(savedInstanceState.containsKey(CLICKED_MARKER)) {
                mMapClickMarkerOptions = savedInstanceState.getParcelable(CLICKED_MARKER);
            }

            if(savedInstanceState.containsKey(LAST_ADDRESSES)) {
                // I'm actually surprised Geocoder doesn't return an ArrayList,
                // or that there's no direct putParcelableList method.
                Address[] addresses = (Address[])savedInstanceState.getParcelableArray(LAST_ADDRESSES);

                if(addresses != null) {
                    mActiveAddresses = new ArrayList<>();
                    Collections.addAll(mActiveAddresses, addresses);
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(permissions.length <= 0 || grantResults.length <= 0)
            return;

        // CentralMap will generally be handling location permissions.  So...
        if(grantResults[0] == PackageManager.PERMISSION_DENIED) {
            // If permissions get denied here, we ignore them and just don't
            // enable My Location support.
            mPermissionsDenied = true;
        } else {
            // Permissions... HO!!!!
            permissionsGranted();
            mPermissionsDenied = false;
        }
    }

    private void permissionsGranted() {
        try {
            mMap.setMyLocationEnabled(true);
        } catch(SecurityException se) {
            // This shouldn't happen (permissionsGranted is called AFTER we get
            // permissions), but Android Studio simply is NOT going to be happy
            // unless I surround it with a try/catch, so...
            checkLocationPermissions(0);
        }
    }

    private void doReadyChecks() {
        if(mMapIsReady && mLayoutComplete) {
            // The map should be centered on the currently-known locations.
            // Otherwise, well, default to dead zero, I guess.
            Log.d(DEBUG_TAG, "There are " + mLocations.size() + " known location(s).");

            // Throw any search addresses back on the map.
            if(mActiveAddresses != null)
                doAddressMarkers(mActiveAddresses);
            else
                mActiveAddressMap = HashBiMap.create();

            // Known locations also ought to be initialized.
            initKnownLocations();

            if(!mReloaded) {
                // If we're reloading, I think the map fragment knows to restore
                // itself.  If not, we default to the current KnownLocations.
                if(!mLocations.isEmpty()) {
                    CameraUpdate cam;

                    LatLngBounds.Builder builder = LatLngBounds.builder();

                    for(KnownLocation kl : mLocations) {
                        // Now, we want to include the range of each location,
                        // too.  Unfortunately, Android doesn't supply us with
                        // a method for "calculate point that is X distance at Y
                        // heading from another point" (or, the inverse geodetic
                        // problem, as it's better known).  So for this, we turn
                        // to the OpenSextant library!
                        Geodetic2DPoint gPoint = new Geodetic2DPoint(
                                new Longitude(kl.getLatLng().longitude, Angle.DEGREES),
                                new Latitude(kl.getLatLng().latitude, Angle.DEGREES));

                        // Start at due north and include it.
                        Geodetic2DArc gArc = new Geodetic2DArc(gPoint, kl.getRange(), new Angle(0, Angle.DEGREES));

                        builder.include(new LatLng(gArc.getPoint2().getLatitudeAsDegrees(), gArc.getPoint2().getLongitudeAsDegrees()));

                        // Repeat for the other cardinal directions.  That'll
                        // give us what we need to fit the circle.
                        gArc.setForwardAzimuth(new Angle(90, Angle.DEGREES));
                        builder.include(new LatLng(gArc.getPoint2().getLatitudeAsDegrees(), gArc.getPoint2().getLongitudeAsDegrees()));

                        gArc.setForwardAzimuth(new Angle(180, Angle.DEGREES));
                        builder.include(new LatLng(gArc.getPoint2().getLatitudeAsDegrees(), gArc.getPoint2().getLongitudeAsDegrees()));

                        gArc.setForwardAzimuth(new Angle(270, Angle.DEGREES));
                        builder.include(new LatLng(gArc.getPoint2().getLatitudeAsDegrees(), gArc.getPoint2().getLongitudeAsDegrees()));
                    }

                    LatLngBounds bounds = builder.build();
                    cam = CameraUpdateFactory.newLatLngBounds(bounds, getResources().getDimensionPixelSize(R.dimen.map_zoom_padding));
                    mMap.animateCamera(cam);
                }
            }

        } else {
        }
    }

    @Override
    public void onMapLongClick(LatLng latLng) {
        // If there's already a marker, clear it out.
        if(mMapClickMarker != null) {
            mMapClickMarker.remove();
            mMapClickMarker = null;
            mMapClickMarkerOptions = null;
        }

        // If the user long-taps the map, we place a marker on the map and offer
        // the user the option to add that as a known location.  We want to keep
        // track of the MarkerOptions object because that's Parcelable, allowing
        // us to stash it away if we need to save the activity's bundle state.
        mMapClickMarkerOptions = createMarker(latLng, null);

        mMapClickMarker = mMap.addMarker(mMapClickMarkerOptions);
        mMapClickMarker.showInfoWindow();
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        // Is this marker associated with a KnownLocation or Address?  If so, we
        // can init the data with that, AND keep track of it.
        String name = "";
        double range = 5.0;
        boolean restrict = false;

        KnownLocation loc = null;
        Address address = null;
        if(mMarkerMap.containsKey(marker)) {
            // Got it!
            loc = mMarkerMap.get(marker);
            name = loc.getName();
            range = loc.getRange();
            restrict = loc.isRestrictedGraticule();
        } else if(mActiveAddressMap.containsKey(marker)) {
            // An address!
            address = mActiveAddressMap.get(marker);
            name = address.getFeatureName();
        }

        mActiveMarker = marker;

        // Now, we've got a dialog to pop up!
        Bundle args = new Bundle();
        args.putString(NAME, name);
        args.putParcelable(EXISTING, loc);
        args.putParcelable(ADDRESS, address);
        args.putParcelable(LATLNG, marker.getPosition());
        args.putDouble(RANGE, range);
        args.putBoolean(RESTRICT, restrict);

        EditKnownLocationDialog dialog = new EditKnownLocationDialog();
        dialog.setArguments(args);
        dialog.show(getFragmentManager(), EDIT_DIALOG);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        return false;
    }

    @NonNull
    private MarkerOptions createMarker(@NonNull LatLng latLng, @Nullable String title) {
        // This builds up the basic marker for a potential KnownLocation.  By
        // "potential", I mean something that isn't stored yet as a
        // KnownLocation, such as search results or map taps.  KnownLocation
        // ITSELF has a makeMarker method.
        if(title == null || title.isEmpty())
            title = UnitConverter.makeFullCoordinateString(this, latLng, false, UnitConverter.OUTPUT_SHORT);

        return new MarkerOptions()
                .position(latLng)
                .flat(true)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.known_location_tap_marker))
                .anchor(0.5f, 0.5f)
                .title(title)
                .snippet(getString(R.string.known_locations_tap_to_add));
    }

    private void initKnownLocations() {
        if(mMarkerMap != null) {
            for(Marker m : mMarkerMap.keySet())
                m.remove();
        }

        if(mCircleMap != null) {
            for(Circle c : mCircleMap.keySet())
                c.remove();
        }

        mMarkerMap = HashBiMap.create();
        mCircleMap = HashBiMap.create();

        if(!mLocations.isEmpty()) {
            for(KnownLocation kl : mLocations) {
                // Each KnownLocation gives us a MarkerOptions we can use.
                Log.d(DEBUG_TAG, "Making marker for KnownLocation " + kl.toString() + " at a range of " + kl.getRange() + "m");
                Marker newMark = mMap.addMarker(makeExistingMarker(kl));
                Circle newCircle = mMap.addCircle(kl.makeCircle(this));
                mMarkerMap.put(newMark, kl);
                mCircleMap.put(newCircle, kl);
            }
        }
    }

    @NonNull
    private MarkerOptions makeExistingMarker(@NonNull KnownLocation loc) {
        return loc.makeMarker(this).snippet(getString(R.string.known_locations_tap_to_edit));
    }

    private void confirmKnownLocationFromDialog(@NonNull String name,
                                                @NonNull LatLng location,
                                                double range,
                                                boolean restrictGraticule,
                                                @NonNull Address address) {
        // An address!  We know what to do with this, right?
        KnownLocation newLoc = new KnownLocation(name, location, range, restrictGraticule);

        // Of course we do!  It's guaranteed to be a new marker!
        mLocations.add(newLoc);

        // And what's more, it's guaranteed to have an old version on the map!
        mActiveAddressMap.inverse().remove(address).remove();

        // Then, replace it with the new one.
        Marker newMark = mMap.addMarker(makeExistingMarker(newLoc));
        Circle newCircle = mMap.addCircle(newLoc.makeCircle(this));
        mMarkerMap.forcePut(newMark, newLoc);
        mCircleMap.forcePut(newCircle, newLoc);
        KnownLocation.storeKnownLocations(this, mLocations);

        mActiveAddresses.remove(address);
        if(mActiveMarker != null) mActiveMarker.remove();

        // Done!
        removeActiveKnownLocation();
    }

    private void confirmKnownLocationFromDialog(@NonNull String name,
                                                @NonNull LatLng location,
                                                double range,
                                                boolean restrictGraticule,
                                                @Nullable KnownLocation existing) {
        // Okay, we got location data in.  Make one!
        KnownLocation newLoc = new KnownLocation(name, location, range, restrictGraticule);

        // Is this new or a replacement?
        if(existing != null) {
            // Replacement!  Or rather, remove the old one and re-add the new
            // one in place.
            int oldIndex = mLocations.indexOf(existing);
            mLocations.remove(oldIndex);
            mLocations.add(oldIndex, newLoc);

            // Since this is an existing KnownLocation, the marker should be in
            // that map, ripe for removal.
            mMarkerMap.inverse().remove(existing).remove();
            mCircleMap.inverse().remove(existing).remove();
        } else {
            // Brand new!
            mLocations.add(newLoc);
        }

        // In both cases, store the data and add a new marker.
        Marker newMark = mMap.addMarker(makeExistingMarker(newLoc));
        Circle newCircle = mMap.addCircle(newLoc.makeCircle(this));
        mMarkerMap.forcePut(newMark, newLoc);
        mCircleMap.forcePut(newCircle, newLoc);
        KnownLocation.storeKnownLocations(this, mLocations);

        // And remove the marker from the map.  The visual one this time.
        // TODO: Null-checking shouldn't be necessary here.
        if(mActiveMarker != null) mActiveMarker.remove();

        // And end the active parts.
        removeActiveKnownLocation();
    }

    private void deleteActiveKnownLocation(@NonNull KnownLocation existing) {
        // This better exist, else we're in trouble.
        if(!mMarkerMap.containsValue(existing)) return;

        // Clear it from the map and from the marker list.
        Marker marker = mMarkerMap.inverse().get(existing);
        marker.remove();
        mMarkerMap.remove(marker);
        mCircleMap.inverse().remove(existing).remove();

        // Then, remove it from the location list and push that back to the
        // preferences.
        mLocations.remove(existing);
        KnownLocation.storeKnownLocations(this, mLocations);

        // Also, clear out the active location and marker.
        removeActiveKnownLocation();
    }

    private void removeActiveKnownLocation() {
        mActiveMarker = null;
        mMapClickMarkerOptions = null;
    }

    private void searchForLocation(@NonNull String input) {
        // If we didn't init a Geocoder by this point, that means the search box
        // shouldn't have been available.
        if(mGeocoder == null) return;

        // Same if this was a blank input.
        if(input.trim().isEmpty()) return;

        // Disable the input field and search button until we're done.
        findViewById(R.id.search).setEnabled(false);
        findViewById(R.id.search_go).setEnabled(false);

        // Let's do it this way: We try to search, and if the Activity goes away
        // by the time it comes back, we act like it never happened.  That's the
        // simplest way around it.
        if(mSearchTask != null) {
            mSearchTask.cancel(false);
        }

        // Fire up a task!  Remember, getProjection and getCameraPosition need
        // to be called on main, so we pass those in to the AsyncTask.

        mSearchTask = new LocationSearchTask(mHandler.obtainMessage(), mGeocoder, mMap.getProjection().getVisibleRegion(), mMap.getCameraPosition().bearing);
        mSearchTask.execute(input);
    }

    private void searchResults(LookupErrorCode code, @NonNull List<Address> addresses) {
        // No matter what, a result means the searchy parts come back on.
        findViewById(R.id.search).setEnabled(true);
        findViewById(R.id.search_go).setEnabled(true);

        // If anything went wrong, report it, but don't remove any markers we
        // already have on the map.  But if we got something...
        if(code == LookupErrorCode.OKAY) {
            Log.d(DEBUG_TAG, "Addresses found: " + addresses.size());

            for(Address a : addresses) {
                Log.d(DEBUG_TAG, "Address: " + a.toString());
            }

            doAddressMarkers(addresses);

            // Reposition the map, too.
            LatLngBounds.Builder builder = LatLngBounds.builder();

            for(Address a : addresses) {
                builder.include(new LatLng(a.getLatitude(), a.getLongitude()));
            }

            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), getResources().getDimensionPixelSize(R.dimen.map_zoom_padding)));
        } else {
            // Else we TOAST!
            int resId = R.string.known_locations_search_error_internal_error;
            String debugString = "Fell through the switch?";
            switch(code) {
                case NO_RESULTS:
                    resId = R.string.known_locations_search_error_no_results;
                    debugString = "There weren't any results.";
                    break;
                case IO_ERROR:
                    resId = R.string.known_locations_search_error_io_error;
                    debugString = "I/O error; probably no network connection.";
                    break;
                case NO_GEOCODER:
                    resId = R.string.known_locations_search_error_no_geocoder;
                    debugString = "No geocoder; how did we get here in the first place?";
                    break;
                case INTERNAL_ERROR:
                    resId = R.string.known_locations_search_error_internal_error;
                    debugString = "Internal error; this'll probably result in a bug report...";
                    break;
                case CANCELED:
                    // This really shouldn't have happened, but we can ignore it
                    // anyway.
                    Log.d(DEBUG_TAG, "Search was canceled; not actually sure how we got here, but ignoring...");
                    return;
            }

            Toast.makeText(this, resId, Toast.LENGTH_LONG).show();
            Log.w(DEBUG_TAG, "Location search lookup error: " + debugString);
        }
    }

    private void doAddressMarkers(@NonNull List<Address> addresses) {
        // Wipe out any current markers, if any.
        if(mActiveAddressMap != null) {
            for(Marker m : mActiveAddressMap.keySet()) {
                m.remove();
            }
        }

        mActiveAddressMap = HashBiMap.create();

        // Keep track of the current address list.
        mActiveAddresses = addresses;

        // Now, throw down a bunch of brand new markers.
        for(Address a : addresses) {
            LatLng curPos = new LatLng(a.getLatitude(), a.getLongitude());

            MarkerOptions opts = new MarkerOptions()
                    .position(curPos)
                    .title(a.getFeatureName() == null ? curPos.latitude + ", " + curPos.longitude : a.getFeatureName())
                    .icon(BitmapDescriptorFactory.fromBitmap(makeAddressBitmap(curPos)))
                    .anchor(0.5f, 1.0f)
                    .snippet(getString(R.string.known_locations_tap_to_add));

            mActiveAddressMap.put(mMap.addMarker(opts), a);
        }
    }

    @NonNull
    private Bitmap makeAddressBitmap(LatLng loc) {
        // The signpost for address search results will just be two rectangles.
        // The top rectangle will be the color the pin will be.
        int dim = getResources().getDimensionPixelSize(R.dimen.known_location_marker_canvas_size);

        Bitmap bitmap = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStrokeWidth(getResources().getDimension(R.dimen.known_location_stroke));


        KnownLocationPinData pinData = new KnownLocationPinData(this, loc);

        // Draw us a rectangle.  Centered horizontally, anchored to the bottom
        // of the canvas.  Draw the color block first, then outline it.
        int width = getResources().getDimensionPixelSize(R.dimen.known_location_address_post_width);
        int height = getResources().getDimensionPixelSize(R.dimen.known_location_address_post_height);

        paint.setColor(Color.HSVToColor(new float[]{25, 1.0f, 0.36f}));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect((dim - width) / 2, dim - height, (dim + width) / 2, dim, paint);

        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect((dim - width) / 2, dim - height, (dim + width) / 2, dim, paint);

        // Then, draw us another rectangle.  Center it horizontally again, inset
        // it from the top by a little bit.
        width = getResources().getDimensionPixelSize(R.dimen.known_location_address_sign_width);
        height = getResources().getDimensionPixelSize(R.dimen.known_location_address_sign_height);
        int inset = getResources().getDimensionPixelSize(R.dimen.known_location_address_sign_inset);

        paint.setColor(pinData.getColor());
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect((dim - width) / 2, inset, (dim + width) / 2, inset + height, paint);

        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect((dim - width) / 2, inset, (dim + width) / 2, inset + height, paint);

        // And one white rectangle so the sign isn't completely blank.  No
        // outline this time around.
        int innerInset = getResources().getDimensionPixelSize(R.dimen.known_location_address_sign_inner_inset);

        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect((dim - width) / 2 + innerInset, inset + innerInset, (dim + width) / 2 - innerInset, inset + height - innerInset, paint);

        return bitmap;
    }

    @Override
    public boolean handleMessage(Message msg) {
        // Back to this thread (and Context), we can start processing.
        LocationSearchTask.ResultObject obj = (LocationSearchTask.ResultObject)msg.obj;

        searchResults(obj.code, obj.addresses);
        return true;
    }
}
