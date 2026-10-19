package org.smssecure.smssecure.util;

import android.os.Environment;

import java.io.File;

/**
 * Central place for backup folder and file names.
 *
 * Export always uses the HUSH names. Import checks the HUSH names first
 * and then falls back to the legacy Silence names, so backups made by
 * original Silence can still be restored.
 */
public final class BackupPaths {

  public static final String HUSH_EXPORT_DIRECTORY       = "HushExport";
  public static final String HUSH_PLAINTEXT_BACKUP_FILE  = "HushPlaintextBackup.xml";
  public static final String LEGACY_SILENCE_EXPORT_DIR   = "SilenceExport";

  private BackupPaths() {}

  public static File getExternalStorageRoot() {
    return Environment.getExternalStorageDirectory();
  }

  public static File getHushExportDirectory() {
    return new File(getExternalStorageRoot(), HUSH_EXPORT_DIRECTORY);
  }

  public static File getLegacySilenceExportDirectory() {
    return new File(getExternalStorageRoot(), LEGACY_SILENCE_EXPORT_DIR);
  }

  public static File getHushPlaintextBackupFile() {
    return new File(getExternalStorageRoot(), HUSH_PLAINTEXT_BACKUP_FILE);
  }

  public static boolean isLegacySilenceDirectory(File directory) {
    return LEGACY_SILENCE_EXPORT_DIR.equals(directory.getName());
  }
}
