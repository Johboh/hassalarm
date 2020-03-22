package com.fjun.hassalarm;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.fjun.hassalarm.databinding.ActivityEditConnectionBinding;
import com.fjun.hassalarm.databinding.ContentEditConnectionBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import static com.fjun.hassalarm.Constants.KEY_PREFS_API_KEY;
import static com.fjun.hassalarm.Constants.KEY_PREFS_ENTITY_ID;
import static com.fjun.hassalarm.Constants.KEY_PREFS_HOST;
import static com.fjun.hassalarm.Constants.KEY_PREFS_IS_ENTITY_ID_LEGACY;
import static com.fjun.hassalarm.Constants.KEY_PREFS_IS_TOKEN;
import static com.fjun.hassalarm.Constants.KEY_PREFS_IS_WEBHOOK;
import static com.fjun.hassalarm.Constants.PREFS_NAME;

public class EditConnectionActivity extends AppCompatActivity {

  private static final int REQUEST_CODE_TEST_CONNECTION = 12;
  private static final String KEY_LAST_SUCCESSFUL = "last_run_successful";

  private Boolean mLastRunWasSuccessful;
  private ContentEditConnectionBinding mBinding;

  public static Intent createIntent(Context context) {
    return new Intent(context, EditConnectionActivity.class);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    final ActivityEditConnectionBinding binding =
        ActivityEditConnectionBinding.inflate(getLayoutInflater());
    setContentView(binding.root);
    setSupportActionBar(binding.toolbar);
    mBinding = binding.content;

    if (savedInstanceState != null) {
      mLastRunWasSuccessful = savedInstanceState.getBoolean(KEY_LAST_SUCCESSFUL, false);
    }

    mBinding.testButton.setOnClickListener(
        view -> {
          final String host = mBinding.hostInput.getText().toString().trim();
          final String token = mBinding.apiKeyInput.getText().toString().trim();
          final String entityId = mBinding.entityIdInput.getText().toString().trim();
          final boolean isToken = mBinding.keyIsToken.isChecked();
          final boolean isWebHook = mBinding.keyIsWebhook.isChecked();
          final boolean entityIdIsLegacy = mBinding.isEntityLegacy.isChecked();
          mLastRunWasSuccessful = null;
          startActivityForResult(
              TestConnectionActivity.createIntent(
                  this, host, token, entityId, isToken, isWebHook, entityIdIsLegacy),
              REQUEST_CODE_TEST_CONNECTION);
        });

    // Set current saved host and api key.
    final SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    mBinding.hostInput.setText(sharedPreferences.getString(KEY_PREFS_HOST, ""));
    mBinding.apiKeyInput.setText(sharedPreferences.getString(KEY_PREFS_API_KEY, ""));

    mBinding.keyType.setOnCheckedChangeListener(
        (group, checkedId) -> {
          if (checkedId == mBinding.keyIsWebhook.getId()) {
            mBinding.isEntityLegacy.setEnabled(false);
          } else {
            mBinding.isEntityLegacy.setEnabled(true);
          }
        });

    int activeRadioButton;
    if (sharedPreferences.getBoolean(KEY_PREFS_IS_TOKEN, true)) {
      activeRadioButton = mBinding.keyIsToken.getId();
    } else if (sharedPreferences.getBoolean(KEY_PREFS_IS_WEBHOOK, false)) {
      activeRadioButton = mBinding.keyIsWebhook.getId();
    } else {
      activeRadioButton = mBinding.keyIsLegacy.getId();
    }
    mBinding.keyType.check(activeRadioButton);

    // Migration of old versions to new versions
    mBinding.entityIdInput.setText(Migration.getEntityId(sharedPreferences));
    mBinding.isEntityLegacy.setChecked(Migration.entityIdIsLegacy(sharedPreferences));

    mBinding.save.setOnClickListener(
        v -> {
          sharedPreferences
              .edit()
              .putString(KEY_PREFS_HOST, mBinding.hostInput.getText().toString().trim())
              .putString(KEY_PREFS_API_KEY, mBinding.apiKeyInput.getText().toString().trim())
              .putString(KEY_PREFS_ENTITY_ID, mBinding.entityIdInput.getText().toString().trim())
              .putBoolean(KEY_PREFS_IS_TOKEN, mBinding.keyIsToken.isChecked())
              .putBoolean(KEY_PREFS_IS_WEBHOOK, mBinding.keyIsWebhook.isChecked())
              .putBoolean(KEY_PREFS_IS_ENTITY_ID_LEGACY, mBinding.isEntityLegacy.isChecked())
              .putLong(Constants.LAST_PUBLISHED_TRIGGER_TIMESTAMP, 0)
              .apply();
          Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
          if (mLastRunWasSuccessful != null) {
            // On change, reset trigger time.
            NextAlarmUpdaterJob.markAsDone(this, mLastRunWasSuccessful, 0);
          }
          finish();
        });
  }

  @Override
  public void onBackPressed() {
    final SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    if (!sharedPreferences
            .getString(KEY_PREFS_HOST, "")
            .equals(mBinding.hostInput.getText().toString().trim())
        || !sharedPreferences
            .getString(KEY_PREFS_API_KEY, "")
            .equals(mBinding.apiKeyInput.getText().toString().trim())
        || !Migration.getEntityId(sharedPreferences)
            .equals(mBinding.entityIdInput.getText().toString().trim())
        || Migration.entityIdIsLegacy(sharedPreferences) != mBinding.isEntityLegacy.isChecked()
        || sharedPreferences.getBoolean(KEY_PREFS_IS_TOKEN, true) != mBinding.keyIsToken.isChecked()
        || sharedPreferences.getBoolean(KEY_PREFS_IS_WEBHOOK, false)
            != mBinding.keyIsWebhook.isChecked()) {
      new MaterialAlertDialogBuilder(this)
          .setTitle(R.string.unsaved_changes_title)
          .setMessage(R.string.unsaved_changes_message)
          .setPositiveButton(R.string.unsaved_changes_discard, (dialog, which) -> finish())
          .setNeutralButton(R.string.unsaved_changes_cancel, null)
          .show();
    } else {
      super.onBackPressed();
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_main, menu);
    return true;
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    if (mLastRunWasSuccessful != null) {
      outState.putBoolean(KEY_LAST_SUCCESSFUL, mLastRunWasSuccessful);
    }
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    if (item.getItemId() == R.id.action_about) {
      startActivity(AboutActivity.createIntent(this));
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_CODE_TEST_CONNECTION) {
      mLastRunWasSuccessful = TestConnectionActivity.getLastRunWasSuccessful(data);
    }
  }
}
