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
package org.smssecure.smssecure.util;

import android.content.Context;
import android.os.Build;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

import org.smssecure.smssecure.R;

public class Dialogs {
  public static void showAlertDialog(Context context, String title, String message) {
    AlertDialog.Builder dialog = new AlertDialog.Builder(context);
    dialog.setTitle(title);
    dialog.setMessage(message);
    dialog.setIconAttribute(R.attr.dialog_alert_icon);
    dialog.setPositiveButton(android.R.string.ok, null);
    dialog.show();
  }

  public static void showInfoDialog(Context context, String title, String message) {
    AlertDialog.Builder dialog = new AlertDialog.Builder(context);
    dialog.setTitle(title);
    dialog.setMessage(message);
    dialog.setIconAttribute(R.attr.dialog_info_icon);
    dialog.setPositiveButton(android.R.string.ok, null);
    dialog.show();
  }

  public static void showHtmlDialog(Context context, int titleRes, String html) {
    View view = LayoutInflater.from(context).inflate(R.layout.dialog_scrollable_text, null);
    TextView textView = (TextView) view.findViewById(R.id.dialog_text);
    if (Build.VERSION.SDK_INT >= 24) textView.setText(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY));
    else                             textView.setText(Html.fromHtml(html));
    AlertDialog.Builder dialog = new AlertDialog.Builder(context);
    dialog.setTitle(titleRes);
    dialog.setView(view);
    dialog.setPositiveButton(android.R.string.ok, null);
    dialog.show();
  }
}
