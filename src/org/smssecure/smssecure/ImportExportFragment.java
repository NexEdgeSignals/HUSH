package org.smssecure.smssecure;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.database.DatabaseFactory;
import org.smssecure.smssecure.database.EncryptedBackupExporter;
import org.smssecure.smssecure.database.NoExternalStorageException;
import org.smssecure.smssecure.database.PlaintextBackupExporter;
import org.smssecure.smssecure.database.PlaintextBackupImporter;
import org.smssecure.smssecure.permissions.Permissions;
import org.smssecure.smssecure.service.ApplicationMigrationService;
import org.smssecure.smssecure.util.BackupPaths;
import org.smssecure.smssecure.util.SilencePreferences;
import org.smssecure.smssecure.util.StoragePermissions;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;


public class ImportExportFragment extends Fragment {

  @SuppressWarnings("unused")
  private static final String TAG = ImportExportFragment.class.getSimpleName();

  private static final int SUCCESS        = 0;
  private static final int NO_SD_CARD     = 1;
  private static final int ERROR_IO       = 2;
  private static final int GENERIC_ERROR  = 3;
  private static final int NEWER_VERSION  = 4;

  private MasterSecret   masterSecret;
  private ProgressDialog progressDialog;

  /** The backup/restore action waiting for the user to grant All files access. */
  private Runnable pendingStorageAction;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    this.masterSecret = getArguments().getParcelable("master_secret");
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    View layout               = inflater.inflate(R.layout.import_export_fragment, container, false);
    View importSmsView        = layout.findViewById(R.id.import_sms             );
    View importEncryptedView  = layout.findViewById(R.id.import_encrypted_backup);
    View importPlaintextView  = layout.findViewById(R.id.import_plaintext_backup);
    View exportEncryptedView  = layout.findViewById(R.id.export_encrypted_backup);
    View exportPlaintextView  = layout.findViewById(R.id.export_plaintext_backup);

    importSmsView.setOnClickListener(v -> handleImportSms());
    importEncryptedView.setOnClickListener(v -> handleImportEncryptedBackup());
    importPlaintextView.setOnClickListener(v -> handleImportPlaintextBackup());
    exportEncryptedView.setOnClickListener(v -> handleExportEncryptedBackup());
    exportPlaintextView.setOnClickListener(v -> handleExportPlaintextBackup());

    return layout;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

    if (progressDialog != null && progressDialog.isShowing()) {
      progressDialog.dismiss();
      progressDialog = null;
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == StoragePermissions.REQUEST_ALL_FILES_ACCESS) {
      Runnable action = pendingStorageAction;
      pendingStorageAction = null;

      if (action == null || getActivity() == null) return;

      if (StoragePermissions.hasBackupStorageAccess(getContext())) {
        action.run();
      } else {
        Toast.makeText(getContext(), R.string.ImportExportFragment_all_files_access_denied, Toast.LENGTH_LONG).show();
      }
    }
  }

  /**
   * Runs the given backup/restore action once storage access is available.
   * Android 11+: requests All files access through a short explanation and
   * the system settings screen, resuming the action on return. Android 10
   * and older: the classic runtime permission flow, unchanged.
   */
  private void withStorageAccess(Runnable action, int permanentDeniedRes, int deniedToastRes) {
    if (StoragePermissions.hasBackupStorageAccess(getContext())) {
      action.run();
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      showAllFilesAccessExplanation(action);
    } else {
      Permissions.with(this)
                 .request(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
                 .ifNecessary()
                 .withPermanentDenialDialog(getString(permanentDeniedRes))
                 .onAllGranted(action)
                 .onAnyDenied(() -> Toast.makeText(getContext(), deniedToastRes, Toast.LENGTH_LONG).show())
                 .execute();
    }
  }

  private void showAllFilesAccessExplanation(Runnable action) {
    if (getActivity() == null) return;

    pendingStorageAction = action;

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setIconAttribute(R.attr.dialog_info_icon);
    builder.setTitle(R.string.ImportExportFragment_all_files_access_title);
    builder.setMessage(R.string.ImportExportFragment_all_files_access_explanation);
    builder.setPositiveButton(R.string.ImportExportFragment_open_settings, (dialog, which) -> {
      if (getActivity() != null) {
        // Launched from the Fragment (not the Activity) so the result is
        // delivered to this fragment's onActivityResult above.
        startActivityForResult(StoragePermissions.createAllFilesAccessSettingsIntent(getActivity()),
                              StoragePermissions.REQUEST_ALL_FILES_ACCESS);
      }
    });
    builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> pendingStorageAction = null);
    builder.setOnCancelListener(dialog -> pendingStorageAction = null);
    builder.show();
  }

  @SuppressWarnings("CodeBlock2Expr")
  private void handleImportSms() {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setIconAttribute(R.attr.dialog_info_icon);
    builder.setTitle(getActivity().getString(R.string.ImportFragment_import_system_sms_database));
    builder.setMessage(getActivity().getString(R.string.ImportFragment_this_will_import_messages_from_the_system));
    builder.setPositiveButton(getActivity().getString(R.string.ImportFragment_import), (dialog, which) -> {
      Permissions.with(this)
                 .request(Manifest.permission.READ_SMS)
                 .ifNecessary()
                 .withPermanentDenialDialog(getString(R.string.ImportExportFragment_silence_needs_the_sms_permission_in_order_to_import_sms_messages))
                 .onAllGranted(() -> {
                   Intent intent = new Intent(getActivity(), ApplicationMigrationService.class);
                   intent.setAction(ApplicationMigrationService.MIGRATE_DATABASE);
                   intent.putExtra("master_secret", masterSecret);
                   getActivity().startService(intent);

                   Intent nextIntent = new Intent(getActivity(), ConversationListActivity.class);

                   Intent activityIntent = new Intent(getActivity(), DatabaseMigrationActivity.class);
                   activityIntent.putExtra("next_intent", nextIntent);
                   getActivity().startActivity(activityIntent);
                 })
                 .onAnyDenied(() -> Toast.makeText(getContext(), R.string.ImportExportFragment_silence_needs_the_sms_permission_in_order_to_import_sms_messages_toast, Toast.LENGTH_LONG).show())
                 .execute();
    });
    builder.setNegativeButton(getActivity().getString(R.string.ImportFragment_cancel), null);
    builder.show();
  }

  @SuppressWarnings("CodeBlock2Expr")
  private void handleImportEncryptedBackup() {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setTitle(getActivity().getString(R.string.ImportFragment_restore_encrypted_backup));
    builder.setMessage(getActivity().getString(R.string.ImportFragment_restoring_an_encrypted_backup_will_completely_replace_your_existing_keys));
    builder.setPositiveButton(getActivity().getString(R.string.ImportFragment_import), (dialog, which) -> {
      withStorageAccess(this::detectAndRestoreEncryptedBackup,
                        R.string.ImportExportFragment_silence_needs_the_storage_permission_in_order_to_read_from_external_storage_but_it_has_been_permanently_denied,
                        R.string.ImportExportFragment_silence_needs_the_storage_permission_in_order_to_read_from_external_storage);
    });
    builder.setNegativeButton(getActivity().getString(R.string.ImportFragment_cancel), null);
    builder.show();
  }

  /**
   * Unified restore: finds the HUSH and legacy Silence backup folders. One
   * folder restores directly; when both exist the user chooses (each shown
   * with its date) - never chosen silently.
   */
  private void detectAndRestoreEncryptedBackup() {
    List<File> directories = EncryptedBackupExporter.getAvailableBackupDirectories();

    if (directories.isEmpty()) {
      Toast.makeText(getContext(), R.string.ImportFragment_no_encrypted_backup_found, Toast.LENGTH_LONG).show();
      return;
    }

    if (directories.size() == 1) {
      confirmRestoreEncryptedBackup(directories.get(0));
      return;
    }

    CharSequence[] labels = new CharSequence[directories.size()];
    for (int i = 0; i < directories.size(); i++) {
      labels[i] = buildBackupChoiceLabel(directories.get(i));
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setIconAttribute(R.attr.dialog_info_icon);
    builder.setTitle(R.string.ImportFragment_choose_backup_title);
    builder.setMessage(R.string.ImportFragment_choose_backup_message);
    builder.setItems(labels, (dialog, which) -> confirmRestoreEncryptedBackup(directories.get(which)));
    builder.setNegativeButton(R.string.ImportFragment_cancel, null);
    builder.show();
  }

  private CharSequence buildBackupChoiceLabel(File directory) {
    DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
    String date       = format.format(new Date(directory.lastModified()));

    if (BackupPaths.isLegacySilenceDirectory(directory)) {
      return getString(R.string.ImportFragment_backup_choice_legacy, directory.getName(), date);
    }
    return getString(R.string.ImportFragment_backup_choice_entry, directory.getName(), date);
  }

  @SuppressWarnings("CodeBlock2Expr")
  private void confirmRestoreEncryptedBackup(File backupDirectory) {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setTitle(getActivity().getString(R.string.ImportFragment_restore_encrypted_backup));
    builder.setMessage(getActivity().getString(R.string.ImportFragment_restoring_an_encrypted_backup_will_completely_replace_your_existing_keys));
    builder.setPositiveButton(getActivity().getString(R.string.ImportFragment_import), (dialog, which) -> {
      new ImportEncryptedBackupTask(backupDirectory).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    });
    builder.setNegativeButton(getActivity().getString(R.string.ImportFragment_cancel), null);
    builder.show();
  }

  @SuppressWarnings("CodeBlock2Expr")
  private void handleImportPlaintextBackup() {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setTitle(getActivity().getString(R.string.ImportFragment_import_plaintext_backup));
    builder.setMessage(getActivity().getString(R.string.ImportFragment_this_will_import_messages_from_a_plaintext_backup));
    builder.setPositiveButton(getActivity().getString(R.string.ImportFragment_import), (dialog, which) -> {
      withStorageAccess(() -> new ImportPlaintextBackupTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR),
                        R.string.ImportExportFragment_silence_needs_the_storage_permission_in_order_to_read_from_external_storage_but_it_has_been_permanently_denied,
                        R.string.ImportExportFragment_silence_needs_the_storage_permission_in_order_to_read_from_external_storage);
    });
    builder.setNegativeButton(getActivity().getString(R.string.ImportFragment_cancel), null);
    builder.show();
  }

  private void handleExportEncryptedBackup() {
    if (SilencePreferences.isPasswordDisabled(getActivity())) {
      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      builder.setIconAttribute(R.attr.dialog_alert_icon);
      builder.setTitle(R.string.ExportFragment_encrypted_backup_without_passphrase_title);
      builder.setMessage(R.string.ExportFragment_warning_encrypted_backup_no_passphrase);
      builder.setPositiveButton(R.string.ExportFragment_export_anyway, (dialog, which) -> showExportEncryptedConfirmation());
      builder.setNegativeButton(android.R.string.cancel, null);
      builder.show();
    } else {
      showExportEncryptedConfirmation();
    }
  }

  @SuppressWarnings("CodeBlock2Expr")
  private void showExportEncryptedConfirmation() {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setIconAttribute(R.attr.dialog_info_icon);
    builder.setTitle(getActivity().getString(R.string.ExportFragment_export_encrypted_backup));
    builder.setMessage(getActivity().getString(R.string.ExportFragment_this_will_export_your_encrypted_keys_settings_and_messages));
    builder.setPositiveButton(getActivity().getString(R.string.ExportFragment_export), (dialog, which) -> {
      withStorageAccess(() -> new ExportEncryptedBackupTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR),
                        R.string.ImportExportFragment_silence_needs_the_storage_permission_in_order_to_write_to_external_storage_but_it_has_been_permanently_denied,
                        R.string.ImportExportFragment_silence_needs_the_storage_permission_in_order_to_write_to_external_storage);
    });
    builder.setNegativeButton(getActivity().getString(R.string.ExportFragment_cancel), null);
    builder.show();
  }

  @SuppressWarnings("CodeBlock2Expr")
  private void handleExportPlaintextBackup() {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setTitle(getActivity().getString(R.string.ExportFragment_export_plaintext_to_storage));
    builder.setMessage(getActivity().getString(R.string.ExportFragment_warning_plaintext_export_file));
    builder.setPositiveButton(getActivity().getString(R.string.ExportFragment_export), (dialog, which) -> {
      withStorageAccess(() -> new ExportPlaintextTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR),
                        R.string.ImportExportFragment_silence_needs_the_storage_permission_in_order_to_write_to_external_storage_but_it_has_been_permanently_denied,
                        R.string.ImportExportFragment_silence_needs_the_storage_permission_in_order_to_write_to_external_storage);
    });
    builder.setNegativeButton(getActivity().getString(R.string.ExportFragment_cancel), null);
    builder.show();
  }

  @SuppressLint("StaticFieldLeak")
  private class ImportPlaintextBackupTask extends AsyncTask<Void, Void, Integer> {

    @Override
    protected void onPreExecute() {
      progressDialog = ProgressDialog.show(getActivity(),
                                           getActivity().getString(R.string.ImportFragment_importing),
                                           getActivity().getString(R.string.ImportFragment_import_plaintext_backup_elipse),
                                           true, false);
    }

    protected void onPostExecute(Integer result) {
      Context context = getActivity();

      if (progressDialog != null)
        progressDialog.dismiss();

      if (context == null)
        return;

      switch (result) {
        case NO_SD_CARD:
          Toast.makeText(context,
                         context.getString(R.string.ImportFragment_no_plaintext_backup_found),
                         Toast.LENGTH_LONG).show();
          break;
        case ERROR_IO:
          Toast.makeText(context,
                         context.getString(R.string.ImportFragment_error_importing_backup),
                         Toast.LENGTH_LONG).show();
          break;
        case GENERIC_ERROR:
          Toast.makeText(context,
                         context.getString(R.string.ImportFragment_backup_failed),
                         Toast.LENGTH_LONG).show();
          break;
        case SUCCESS:
          Toast.makeText(context,
                         context.getString(R.string.ImportFragment_import_complete),
                         Toast.LENGTH_LONG).show();
          break;
      }
    }

    @Override
    protected Integer doInBackground(Void... params) {
      try {
        PlaintextBackupImporter.importPlaintextFromSd(getActivity(), masterSecret);
        return SUCCESS;
      } catch (NoExternalStorageException e) {
        Log.w("ImportFragment", e);
        return NO_SD_CARD;
      } catch (IOException e) {
        Log.w("ImportFragment", e);
        return ERROR_IO;
      } catch (Exception e) {
        Log.w("ImportFragment", e);
        return GENERIC_ERROR;
      }
    }
  }

  @SuppressLint("StaticFieldLeak")
  private class ExportPlaintextTask extends AsyncTask<Void, Void, Integer> {
    private ProgressDialog dialog;

    @Override
    protected void onPreExecute() {
      dialog = ProgressDialog.show(getActivity(),
                                   getActivity().getString(R.string.ExportFragment_exporting),
                                   getActivity().getString(R.string.ExportFragment_exporting_plaintext_to_storage),
                                   true, false);
    }

    @Override
    protected Integer doInBackground(Void... params) {
      try {
        PlaintextBackupExporter.exportPlaintextToSd(getActivity(), masterSecret);
        return SUCCESS;
      } catch (NoExternalStorageException e) {
        Log.w("ExportFragment", e);
        return NO_SD_CARD;
      } catch (IOException e) {
        Log.w("ExportFragment", e);
        return ERROR_IO;
      } catch (Exception e) {
        Log.w("ExportFragment", e);
        return GENERIC_ERROR;
      }
    }

    @Override
    protected void onPostExecute(Integer result) {
      Context context = getActivity();

      if (dialog != null)
        dialog.dismiss();

      if (context == null)
        return;

      switch (result) {
        case NO_SD_CARD:
          Toast.makeText(context,
                         context.getString(R.string.ExportFragment_error_unable_to_write_to_storage),
                         Toast.LENGTH_LONG).show();
          break;
        case ERROR_IO:
          Toast.makeText(context,
                         context.getString(R.string.ExportFragment_error_while_writing_to_storage),
                         Toast.LENGTH_LONG).show();
          break;
        case GENERIC_ERROR:
          Toast.makeText(context,
                         context.getString(R.string.ImportFragment_backup_failed),
                         Toast.LENGTH_LONG).show();
          break;
        case SUCCESS:
          Toast.makeText(context,
                         context.getString(R.string.ExportFragment_export_successful),
                         Toast.LENGTH_LONG).show();
          break;
      }
    }
  }

  @SuppressLint("StaticFieldLeak")
  private class ImportEncryptedBackupTask extends AsyncTask<Void, Void, Integer> {

    private final File backupDirectory;

    ImportEncryptedBackupTask(File backupDirectory) {
      this.backupDirectory = backupDirectory;
    }

    @Override
    protected void onPreExecute() {
      progressDialog = ProgressDialog.show(getActivity(),
                                           getActivity().getString(R.string.ImportFragment_importing),
                                           getActivity().getString(R.string.ImportFragment_restoring_encrypted_backup),
                                           true, false);
    }

    @Override
    protected Integer doInBackground(Void... params) {
      try {
        int backupVersion = EncryptedBackupExporter.getBackupDatabaseVersion(backupDirectory);

        if (backupVersion > DatabaseFactory.DATABASE_VERSION) {
          Log.w("ImportFragment", "Backup database version " + backupVersion +
                                  " is newer than app database version " + DatabaseFactory.DATABASE_VERSION);
          return NEWER_VERSION;
        }

        EncryptedBackupExporter.importFromStorage(getActivity(), backupDirectory);
        return SUCCESS;
      } catch (NoExternalStorageException e) {
        Log.w("ImportFragment", e);
        return NO_SD_CARD;
      } catch (IOException e) {
        Log.w("ImportFragment", e);
        return ERROR_IO;
      } catch (Exception e) {
        Log.w("ImportFragment", e);
        return GENERIC_ERROR;
      }
    }

    protected void onPostExecute(Integer result) {
      Context context = getActivity();

      if (progressDialog != null)
        progressDialog.dismiss();

      if (context == null)
        return;

      switch (result) {
        case NO_SD_CARD:
          Toast.makeText(context,
                         context.getString(R.string.ImportFragment_no_encrypted_backup_found),
                         Toast.LENGTH_LONG).show();
          break;
        case ERROR_IO:
          Toast.makeText(context,
                         context.getString(R.string.ImportFragment_error_importing_backup),
                         Toast.LENGTH_LONG).show();
          break;
        case GENERIC_ERROR:
          Toast.makeText(context,
                         context.getString(R.string.ImportFragment_backup_failed),
                         Toast.LENGTH_LONG).show();
          break;
        case NEWER_VERSION:
          Toast.makeText(context,
                         context.getString(R.string.ImportFragment_backup_newer_version),
                         Toast.LENGTH_LONG).show();
          break;
        case SUCCESS:
          ExitActivity.exitAndRemoveFromRecentApps(getActivity());
          break;
      }
    }
  }

  @SuppressLint("StaticFieldLeak")
  private class ExportEncryptedBackupTask extends AsyncTask<Void, Void, Integer> {
    private ProgressDialog dialog;

    @Override
    protected void onPreExecute() {
      dialog = ProgressDialog.show(getActivity(),
                                   getActivity().getString(R.string.ExportFragment_exporting),
                                   getActivity().getString(R.string.ExportFragment_exporting_keys_settings_and_messages),
                                   true, false);
    }

    @Override
    protected Integer doInBackground(Void... params) {
      try {
        EncryptedBackupExporter.exportToStorage(getActivity());
        return SUCCESS;
      } catch (NoExternalStorageException e) {
        Log.w("ExportFragment", e);
        return NO_SD_CARD;
      } catch (IOException e) {
        Log.w("ExportFragment", e);
        return ERROR_IO;
      } catch (Exception e) {
        Log.w("ExportFragment", e);
        return GENERIC_ERROR;
      }
    }

    @Override
    protected void onPostExecute(Integer result) {
      Context context = getActivity();

      if (dialog != null) dialog.dismiss();

      if (context == null) return;

      switch (result) {
        case NO_SD_CARD:
          Toast.makeText(context,
                         context.getString(R.string.ExportFragment_error_unable_to_write_to_storage),
                         Toast.LENGTH_LONG).show();
          break;
        case ERROR_IO:
          Toast.makeText(context,
                         context.getString(R.string.ExportFragment_error_while_writing_to_storage),
                         Toast.LENGTH_LONG).show();
          break;
        case GENERIC_ERROR:
          Toast.makeText(context,
                         context.getString(R.string.ImportFragment_backup_failed),
                         Toast.LENGTH_LONG).show();
          break;
        case SUCCESS:
          Toast.makeText(context,
                         context.getString(R.string.ExportFragment_export_successful),
                         Toast.LENGTH_LONG).show();
          break;
      }
    }
  }

}
