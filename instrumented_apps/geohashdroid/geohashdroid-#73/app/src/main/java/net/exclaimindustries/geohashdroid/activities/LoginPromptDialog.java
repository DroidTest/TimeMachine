/*
 * LoginPromptDialog.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.activities;

import android.app.Activity;
import android.app.backup.BackupManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.services.WikiService;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.tools.QueueService;

/**
 * This is a simple dialog prompt that asks for a new username/password combo
 * from the user.  This is summoned from {@link WikiService} any time the wiki
 * reports a login problem.  Once the credentials are updated, it tells the
 * service to kick back in again.
 */
public class LoginPromptDialog extends Activity {
    private Button mOkay;

    private EditText mUsername;
    private EditText mPassword;

    private TextWatcher mWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // Nothing.
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // Also nothing.
        }

        @Override
        public void afterTextChanged(Editable s) {
            // Something!
            mOkay.setEnabled(!mPassword.getText().toString().isEmpty() && !mUsername.getText().toString().isEmpty());
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.logindialog);

        // Widgets!
        mOkay = findViewById(R.id.okay);
        Button cancel = findViewById(R.id.cancel);

        mUsername = findViewById(R.id.input_username);
        mPassword = findViewById(R.id.input_password);

        // The okay and cancel buttons are easy to figure out.
        cancel.setOnClickListener(v -> finish());

        mOkay.setOnClickListener(v -> {
            // Dispatch new settings!
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(LoginPromptDialog.this);

            SharedPreferences.Editor edit = prefs.edit();

            // We're pretty sure the okay button should only be enabled if
            // there's input in both fields.
            edit.putString(GHDConstants.PREF_WIKI_USER, mUsername.getText().toString());
            edit.putString(GHDConstants.PREF_WIKI_PASS, mPassword.getText().toString());

            // Commit's a good idea here.  Sure, chances are the background
            // operation will finish before WikiService kicks back in, but
            // we should make sure.
            edit.commit();

            BackupManager bm = new BackupManager(LoginPromptDialog.this);
            bm.dataChanged();

            // Then, tell WikiService it can get back to work.
            Intent in = new Intent(LoginPromptDialog.this, WikiService.class)
                    .putExtra(QueueService.COMMAND_EXTRA, QueueService.COMMAND_RESUME);
            startService(in);

            // And we're done here.
            finish();
        });

        // Then, both the text boxes need to track whether or not Okay should be
        // on.
        mPassword.addTextChangedListener(mWatcher);
        mUsername.addTextChangedListener(mWatcher);

        // Also, those fields should be populated with data.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mUsername.setText(prefs.getString(GHDConstants.PREF_WIKI_USER, ""));
        mPassword.setText(prefs.getString(GHDConstants.PREF_WIKI_PASS, ""));
    }
}
