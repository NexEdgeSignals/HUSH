package org.smssecure.smssecure.components.reminder;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.Telephony;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import org.smssecure.smssecure.R;
import org.smssecure.smssecure.util.SilencePreferences;
import org.smssecure.smssecure.util.Util;

public class DefaultSmsReminder extends Reminder {

  private static final int SMS_ROLE_REQUEST_CODE = 42;

  @TargetApi(VERSION_CODES.KITKAT)
  public DefaultSmsReminder(final Context context) {
    super(context.getString(R.string.reminder_header_sms_default_title),
          context.getString(R.string.reminder_header_sms_default_text_mandatory),
          context.getString(R.string.reminder_header_sms_default_button));

    final OnClickListener okListener = new OnClickListener() {
      @Override
      public void onClick(View v) {
        SilencePreferences.setPromptedDefaultSmsProvider(context, true);
        try {
          if (VERSION.SDK_INT >= VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) context.getSystemService(Context.ROLE_SERVICE);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
              Intent roleIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS);
              if (context instanceof Activity) ((Activity) context).startActivityForResult(roleIntent, SMS_ROLE_REQUEST_CODE);
              else                             context.startActivity(roleIntent);
              return;
            }
          } else {
            Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.getPackageName());
            context.startActivity(intent);
            return;
          }
        } catch (Exception e) {
          Log.w("DefaultSmsReminder", "Failed to launch default SMS role request", e);
          Toast.makeText(context, context.getString(R.string.DefaultSmsReminder_error), Toast.LENGTH_LONG).show();
        }
      }
    };
    final OnClickListener dismissListener = new OnClickListener() {
      @Override
      public void onClick(View v) {
        SilencePreferences.setPromptedDefaultSmsProvider(context, true);
      }
    };
    setOkListener(okListener);
    setDismissListener(dismissListener);
  }

  public static boolean isEligible(Context context) {
    final boolean isDefault = Util.isDefaultSmsProvider(context);
    if (isDefault) {
      SilencePreferences.setPromptedDefaultSmsProvider(context, false);
    }

    return !isDefault && !SilencePreferences.hasPromptedDefaultSmsProvider(context);
  }
}
