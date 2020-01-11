package com.fjun.hassalarm;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.fjun.hassalarm.databinding.ActivityEditConnectionBinding;
import com.fjun.hassalarm.databinding.ContentEditConnectionBinding;

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
import static com.fjun.hassalarm.Constants.KEY_PREFS_IS_TOKEN;
import static com.fjun.hassalarm.Constants.PREFS_NAME;

public class EditConnectionActivity extends AppCompatActivity {

    private static final String KEY_LAST_SUCCESSFUL = "last_run_successful";

    private ContentEditConnectionBinding mBinding;
    private Call<ResponseBody> mCall;
    private Boolean mLastRunWasSuccessful;

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

        findViewById(R.id.save).setOnClickListener(v -> {
            if (mCall != null) {
                mCall.cancel();
            }
            sharedPreferences.edit()
                    .putString(KEY_PREFS_HOST, mBinding.hostInput.getText().toString().trim())
                    .putString(KEY_PREFS_API_KEY, mBinding.apiKeyInput.getText().toString().trim())
                    .putString(KEY_PREFS_ENTITY_ID, mBinding.entityIdInput.getText().toString().trim())
                    .putBoolean(KEY_PREFS_IS_TOKEN, !mBinding.isApiInput.isChecked())
                    .apply();
            Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
            if (mLastRunWasSuccessful != null) {
                NextAlarmUpdaterJob.markAsDone(this, mLastRunWasSuccessful);
            }
            finish();
        });
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

        try {
            mCall = NextAlarmUpdaterJob.createRequestCall(this,
                    mBinding.hostInput.getText().toString().trim(),
                    mBinding.apiKeyInput.getText().toString().trim(),
                    mBinding.entityIdInput.getText().toString().trim(),
                    !mBinding.isApiInput.isChecked(),
                    mBinding.isEntityLegacy.isChecked());

            mBinding.log.append(getString(R.string.using_url, mCall.request().method(), mCall.request().url().toString()));
            mBinding.log.append(getString(R.string.headers, mCall.request().headers().toString()));
            mBinding.log.append(getString(R.string.body, requestBodyToString(mCall.request())));
            mCall.enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    boolean wasSuccessful = false;
                    try (ResponseBody body = response.body()) {
                        if (body != null) {
                            mBinding.log.append(getString(R.string.connection_ok, body.string()));
                            wasSuccessful = true;
                        } else if (response.errorBody() != null) {
                            mBinding.log.append(getString(R.string.connection_failure, response.errorBody().string()));
                        } else {
                            mBinding.log.append(getString(R.string.connection_failure_code, response.code()));
                        }
                    } catch (IOException e) {
                        mBinding.log.append(getString(R.string.connection_failure, e.getMessage()));
                    }
                    markAsDone(wasSuccessful);
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    mBinding.log.append(getString(R.string.connection_failure, t.getMessage()));
                    markAsDone(false);
                }
            });
        } catch (IllegalArgumentException e) {
            mBinding.log.append(e.getMessage() + "\n");
            markAsDone(false);
        }

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
