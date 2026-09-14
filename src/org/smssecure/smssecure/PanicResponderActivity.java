package org.smssecure.smssecure;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;


import org.smssecure.smssecure.service.KeyCachingService;
import org.smssecure.smssecure.util.SilencePreferences;




public class PanicResponderActivity extends Activity {

  private static final String TAG = PanicResponderActivity.class.getSimpleName();

  public static final String PANIC_TRIGGER_ACTION = "info.guardianproject.panic.action.TRIGGER";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    String trustedPackage = SilencePreferences.getPanicTrustedPackage(this);
    String callingPackage  = getCallingPackage();
    Intent intent = (trustedPackage != null && trustedPackage.equals(callingPackage))
            ? getIntent()
            : null;
    if (intent != null
            && !SilencePreferences.isPasswordDisabled(this)
            && PANIC_TRIGGER_ACTION.equals(intent.getAction())) {
      handleClearPassphrase();
      ExitActivity.exitAndRemoveFromRecentApps(this);
    }

    if (Build.VERSION.SDK_INT >= 21) {
      finishAndRemoveTask();
    } else {
      finish();
    }
  }

  private void handleClearPassphrase() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(KeyCachingService.CLEAR_KEY_ACTION);
    startService(intent);
  }
}
