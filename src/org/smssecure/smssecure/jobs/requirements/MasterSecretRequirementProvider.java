package org.smssecure.smssecure.jobs.requirements;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import org.smssecure.smssecure.service.KeyCachingService;
import org.whispersystems.jobqueue.requirements.RequirementListener;
import org.whispersystems.jobqueue.requirements.RequirementProvider;

public class MasterSecretRequirementProvider implements RequirementProvider {

  private final BroadcastReceiver newKeyReceiver;

  private RequirementListener listener;

  public MasterSecretRequirementProvider(Context context) {
    this.newKeyReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        if (listener != null) {
          listener.onRequirementStatusChanged();
        }
      }
    };

    IntentFilter filter = new IntentFilter(KeyCachingService.NEW_KEY_EVENT);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.registerReceiver(newKeyReceiver, filter, KeyCachingService.KEY_PERMISSION, null, Context.RECEIVER_NOT_EXPORTED);
    } else {
      context.registerReceiver(newKeyReceiver, filter, KeyCachingService.KEY_PERMISSION, null);
    }
  }

  @Override
  public void setListener(RequirementListener listener) {
    this.listener = listener;
  }
}
