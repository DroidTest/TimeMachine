/*
 * PermissionsDeniedListener.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.util;

/**
 * Interface used by CentralMapModes and CentralMapExtraFragments for
 * knowing when permissions have been explicitly denied.
 */
public interface PermissionsDeniedListener {
    /**
     * Called when a change happens to whether or not the permissions have
     * been denied.  On startup, CentralMap should be queried for what it
     * thinks is the most recent status of permissions.  This will be called
     * when the user either gives or refuses permission explicitly (until
     * that happens, CentralMap will report that it hasn't been denied).
     * Update the mode appropriately.
     *
     * @param denied true for permission denied, false for permission granted
     */
    void permissionsDenied(boolean denied);
}
