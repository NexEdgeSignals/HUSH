/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.smssecure.smssecure.database;

import android.content.Context;
import android.util.Log;

import org.smssecure.smssecure.util.BackupPaths;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.nio.channels.FileChannel;

public class EncryptedBackupExporter {

  private static final String TAG = EncryptedBackupExporter.class.getSimpleName();

  public static void exportToStorage(Context context) throws NoExternalStorageException, IOException {
    verifyExternalStorageForExport();
    exportDirectory(context, "");
  }

  public static void importFromStorage(Context context, File backupDirectory) throws NoExternalStorageException, IOException {
    verifyExternalStorageForImport(backupDirectory);
    importDirectory(context, backupDirectory, "");
  }

  /**
   * Returns the existing encrypted-backup folders: the HUSH folder first,
   * then the legacy Silence folder. A folder counts as a backup only when it
   * contains databases/messages.db. May be empty, one entry, or both.
   */
  public static List<File> getAvailableBackupDirectories() {
    List<File> candidates = new ArrayList<>();
    candidates.add(BackupPaths.getHushExportDirectory());
    candidates.add(BackupPaths.getLegacySilenceExportDirectory());

    List<File> available = new ArrayList<>();
    for (File candidate : candidates) {
      if (candidate.isDirectory() && new File(candidate, "databases" + File.separator + "messages.db").isFile()) {
        available.add(candidate);
      }
    }
    return available;
  }

  /**
   * Reads the SQLite header "user version" field of the backup's messages.db
   * (big-endian 4-byte integer at byte offset 60 - the field Android's
   * SQLiteOpenHelper keeps DATABASE_VERSION in). Returns -1 when the file is
   * unreadable or its header is not the standard "SQLite format 3" marker -
   * callers treat -1 as "unknown" and skip the version check.
   */
  public static int getBackupDatabaseVersion(File backupDirectory) {
    File   dbFile = new File(backupDirectory, "databases" + File.separator + "messages.db");
    InputStream in = null;
    try {
      in     = new FileInputStream(dbFile);
      byte[] header = new byte[64];
      int    read   = 0;

      while (read < header.length) {
        int r = in.read(header, read, header.length - read);
        if (r < 0) return -1;
        read += r;
      }

      byte[] sqliteMarker = "SQLite format 3\u0000".getBytes(StandardCharsets.US_ASCII);
      for (int i = 0; i < sqliteMarker.length; i++) {
        if (header[i] != sqliteMarker[i]) {
          Log.w(TAG, "Backup database header is not SQLite - skipping version check");
          return -1;
        }
      }

      return ((header[60] & 0xff) << 24) | ((header[61] & 0xff) << 16) | ((header[62] & 0xff) << 8) | (header[63] & 0xff);
    } catch (IOException e) {
      Log.w(TAG, "Could not read backup database version", e);
      return -1;
    } finally {
      try {
        if (in != null) in.close();
      } catch (IOException ignored) {}
    }
  }

  private static String getExportDirectoryPath() {
    return BackupPaths.getHushExportDirectory().getAbsolutePath();
  }

  private static void verifyExternalStorageForExport() throws NoExternalStorageException {
    if (!BackupPaths.getExternalStorageRoot().canWrite())
      throw new NoExternalStorageException();

    String exportDirectoryPath = getExportDirectoryPath();
    File exportDirectory       = new File(exportDirectoryPath);

    if (!exportDirectory.exists())
      exportDirectory.mkdirs();
  }

  private static void verifyExternalStorageForImport(File backupDirectory) throws NoExternalStorageException {
    if (!BackupPaths.getExternalStorageRoot().canRead()) {
      Log.w(TAG, "Cannot get external storage directory!");
      throw new NoExternalStorageException();
    }
    if (!backupDirectory.exists()) {
      Log.w(TAG, "Cannot get backup directory \"" + backupDirectory.getAbsolutePath() + "\"!");
      throw new NoExternalStorageException();
    }
  }

  private static void migrateFile(File from, File to) {
    try {
      if (from.exists()) {
        FileChannel source      = new FileInputStream(from).getChannel();
        FileChannel destination = new FileOutputStream(to).getChannel();

        destination.transferFrom(source, 0, source.size());
        source.close();
        destination.close();
      }
    } catch (IOException ioe) {
      Log.w(TAG, ioe);
    }
  }

  private static void exportDirectory(Context context, String directoryName) throws IOException {
    if (!directoryName.equals("/lib")) {
      File directory       = new File(context.getFilesDir().getParent() + File.separatorChar + directoryName);
      File exportDirectory = new File(getExportDirectoryPath() + File.separatorChar + directoryName);

      if (directory.exists() && directory.isDirectory()) {
        exportDirectory.mkdirs();

        File[] contents = directory.listFiles();

        if (contents == null)
          throw new IOException("directory.listFiles() is null for " + context.getFilesDir().getParent() + File.separatorChar + directoryName + "!");

          for (File localFile : contents) {
              if (localFile.isFile() && !localFile.getAbsolutePath().contains("libcurve25519.so")) {
                  File exportedFile = new File(exportDirectory.getAbsolutePath() + File.separator + localFile.getName());
                  migrateFile(localFile, exportedFile);
              } else {
                  exportDirectory(context, directoryName + File.separator + localFile.getName());
              }
          }
      } else {
        Log.w(TAG, "Could not find directory: " + directory.getAbsolutePath() + " (or it is not a directory)");
      }
    }
  }

  private static void importDirectory(Context context, File backupDirectory, String directoryName) throws IOException {
    File directory       = new File(backupDirectory.getAbsolutePath() + File.separator + directoryName);
    File importDirectory = new File(context.getFilesDir().getParent() + File.separator + directoryName);

    if (directory.exists() && directory.isDirectory()) {
      importDirectory.mkdirs();

      File[] contents = directory.listFiles();

      for (File exportedFile : contents) {
        if (exportedFile.isFile()) {
          File localFile = new File(importDirectory.getAbsolutePath() + File.separator + exportedFile.getName());
          migrateFile(exportedFile, localFile);
        } else if (exportedFile.isDirectory()) {
          importDirectory(context, backupDirectory, directoryName + File.separator + exportedFile.getName());
        }
      }
    }
  }
}
