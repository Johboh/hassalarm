package com.fjun.hassalarm;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.fjun.hassalarm.databinding.ActivityEditConnectionBinding;
import com.fjun.hassalarm.databinding.ContentEditConnectionBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;

import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Buffer;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.fjun.hassalarm.Constants.KEY_PREFS_API_KEY;
import static com.fjun.hassalarm.Constants.KEY_PREFS_ENTITY_ID;
import static com.fjun.hassalarm.Constants.KEY_PREFS_HOST;
import static com.fjun.hassalarm.Constants.KEY_PREFS_IS_ENTITY_ID_LEGACY;
import static com.fjun.hassalarm.Constants.KEY_PREFS_IS_TOKEN;
import static com.fjun.hassalarm.Constants.PREFS_NAME;

public class EditConnectionActivity extends AppCompatActivity {

    private static final String KEY_LAST_SUCCESSFUL = "last_run_successful";

    private ContentEditConnectionBinding mBinding;
    private NextAlarmUpdaterJob.Request mRequest;
    private Boolean mLastRunWasSuccessful;
    private String mStrippedLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ActivityEditConnectionBinding binding = ActivityEditConnectionBinding.inflate(getLayoutInflater());
        setContentView(binding.root);
        setSupportActionBar(binding.toolbar);
        mBinding = binding.content;

        if (savedInstanceState != null) {
            mLastRunWasSuccessful = savedInstanceState.getBoolean(KEY_LAST_SUCCESSFUL, false);
        }

        mBinding.log.setMovementMethod(new ScrollingMovementMethod());
        mBinding.testButton.setOnClickListener(view -> runTest());

        // Set current saved host and api key.
        final SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        mBinding.hostInput.setText(sharedPreferences.getString(KEY_PREFS_HOST, ""));
        mBinding.apiKeyInput.setText(sharedPreferences.getString(KEY_PREFS_API_KEY, ""));
        mBinding.isApiInput.setChecked(!sharedPreferences.getBoolean(KEY_PREFS_IS_TOKEN, true));

        // Migration of old versions to new versions
        mBinding.entityIdInput.setText(Migration.getEntityId(sharedPreferences));
        mBinding.isEntityLegacy.setChecked(Migration.entityIdIsLegacy(sharedPreferences));

        mBinding.save.setOnClickListener(v -> {
            if (mRequest != null) {
                mRequest.call().cancel();
            }
            sharedPreferences.edit()
                    .putString(KEY_PREFS_HOST, mBinding.hostInput.getText().toString().trim())
                    .putString(KEY_PREFS_API_KEY, mBinding.apiKeyInput.getText().toString().trim())
                    .putString(KEY_PREFS_ENTITY_ID, mBinding.entityIdInput.getText().toString().trim())
                    .putBoolean(KEY_PREFS_IS_TOKEN, !mBinding.isApiInput.isChecked())
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

        mBinding.log.setOnLongClickListener(View::showContextMenu);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.log) {
            final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            menu.add(R.string.copy_full_log).setOnMenuItemClickListener(item -> {
                clipboard.setPrimaryClip(ClipData.newPlainText("Hassalarm full connection log", mBinding.log.getText().toString()));
                return true;
            });
            if (!TextUtils.isEmpty(mStrippedLog)) {
                menu.add(R.string.copy_anonymous_log).setOnMenuItemClickListener(item -> {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Hassalarm anonymous connection log", mStrippedLog));
                    return true;
                });
            }
        }
    }

    @Override
    public void onBackPressed() {
        final SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!sharedPreferences.getString(KEY_PREFS_HOST, "").equals(mBinding.hostInput.getText().toString().trim()) ||
                !sharedPreferences.getString(KEY_PREFS_API_KEY, "").equals(mBinding.apiKeyInput.getText().toString().trim()) ||
                !Migration.getEntityId(sharedPreferences).equals(mBinding.entityIdInput.getText().toString().trim()) ||
                Migration.entityIdIsLegacy(sharedPreferences) != mBinding.isEntityLegacy.isChecked() ||
                sharedPreferences.getBoolean(KEY_PREFS_IS_TOKEN, true) == mBinding.isApiInput.isChecked()) {
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
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_about) {
            startActivity(AboutActivity.createIntent(this));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void runTest() {
        mBinding.progress.setVisibility(View.VISIBLE);
        mBinding.statusDrawable.setVisibility(View.GONE);
        mBinding.status.setText(R.string.status_running);
        mBinding.log.setText("");
        mStrippedLog = "";

        final String host = mBinding.hostInput.getText().toString().trim();
        final String token = mBinding.apiKeyInput.getText().toString().trim();
        final String entityId = mBinding.entityIdInput.getText().toString().trim();
        final boolean isToken = !mBinding.isApiInput.isChecked();
        final boolean entityIdIsLegacy = mBinding.isEntityLegacy.isChecked();

        mBinding.log.append((isToken ? getString(R.string.log_using_token) : getString(R.string.log_using_api_key)) + "\n");
        mBinding.log.append((entityIdIsLegacy ? getString(R.string.log_entity_id_is_legacy_sensor) : getString(R.string.log_entity_id_is_input_datetime)) + "\n");

        try {
            mRequest = NextAlarmUpdaterJob.createRequest(this,
                    host,
                    token,
                    entityId,
                    isToken,
                    entityIdIsLegacy);

            final Call<ResponseBody> call = mRequest.call();
            mBinding.log.append(getString(R.string.using_url, call.request().method(), call.request().url().toString()) + "\n");
            mBinding.log.append(getString(R.string.headers, call.request().headers().toString()) + "\n");
            mBinding.log.append(getString(R.string.body, requestBodyToString(call.request())) + "\n");
            call.enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    boolean wasSuccessful = false;
                    try (ResponseBody body = response.body()) {
                        if (body != null) {
                            mBinding.log.append(getString(R.string.connection_ok, body.string()) + "\n");
                            wasSuccessful = true;
                        } else if (response.errorBody() != null) {
                            mBinding.log.append(getString(R.string.connection_failure, response.errorBody().string()) + "\n");
                        } else {
                            mBinding.log.append(getString(R.string.connection_failure_code, response.code()) + "\n");
                        }
                    } catch (IOException e) {
                        mBinding.log.append(getString(R.string.connection_failure, e.getMessage()) + "\n");
                    }
                    markAsDone(wasSuccessful);
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    mBinding.log.append(getString(R.string.connection_failure, t.getMessage()) + "\n");
                    markAsDone(false);
                }
            });
        } catch (IllegalArgumentException e) {
            mBinding.log.append(e.getMessage() + "\n");
            markAsDone(false);
        }

        mStrippedLog = mBinding.log.getText().toString()
                .replace(host, "<host>")
                .replace(token, "<token>");

        final InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mBinding.hostInput.getWindowToken(), 0);
        }
    }

    private static String requestBodyToString(final Request request) {
        try {
            final Request copy = request.newBuilder().build();
            final Buffer buffer = new Buffer();
            copy.body().writeTo(buffer);
            return buffer.readUtf8();
        } catch (final IOException e) {
            return "Unable to get request body";
        }
    }

    private void markAsDone(boolean successful) {
        mBinding.progress.setVisibility(View.GONE);
        mBinding.statusDrawable.setVisibility(View.VISIBLE);
        if (successful) {
            mBinding.status.setText(R.string.status_done_ok);
            mBinding.statusDrawable.setImageDrawable(getDrawable(R.drawable.ic_check_green_24dp));
        } else {
            mBinding.status.setText(R.string.status_done_failed);
            mBinding.statusDrawable.setImageDrawable(getDrawable(R.drawable.ic_error_outline_red_24dp));
        }
        mLastRunWasSuccessful = successful;
        registerForContextMenu(mBinding.log);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mLastRunWasSuccessful != null) {
            outState.putBoolean(KEY_LAST_SUCCESSFUL, mLastRunWasSuccessful);
        }
    }

    public static Intent createIntent(Context context) {
        return new Intent(context, EditConnectionActivity.class);
    }
}
