package org.smssecure.smssecure.util;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

/**
 * Storage access for backup and restore.
 *
 * Android 11 (R) and newer: plain WRITE/READ_EXTERNAL_STORAGE no longer grants
 * access to shared storage, so backup/restore needs the "All files access"
 * special permission (MANAGE_EXTERNAL_STORAGE), granted by the user through a
 * system settings screen. It is only requested when the user opens a
 * backup/restore feature - never at first launch.
 *
 * Android 10 and older: the classic runtime permissions still work and are
 * checked here so callers have a single entry point.
 */
public final class StoragePermissions {

  /** Request code for the All files access settings screen. */
  public static final int REQUEST_ALL_FILES_ACCESS = 42424;

  private StoragePermissions() {}

  /**
   * True when the app may read and write the shared storage folders used by
   * backup/restore. On Android 10 and older this only means the legacy
   * runtime permissions are granted; on Android 11+ it means All files
   * access has been granted.
   */
  public static boolean hasBackupStorageAccess(Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      return Environment.isExternalStorageManager();
    }
    return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
           ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)  == PackageManager.PERMISSION_GRANTED;
  }

  /**
   * Intent that opens the system "All files access" screen for this app.
   * Falls back to the general All files access list if the direct screen is
   * unavailable on this device.
   */
  public static Intent createAllFilesAccessSettingsIntent(Context context) {
    Uri packageUri = Uri.fromParts("package", context.getPackageName(), null);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Intent appSpecific = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri);
      if (appSpecific.resolveActivity(context.getPackageManager()) != null) {
        return appSpecific;
      }
      return new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
    }

    return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri);
  }
}
