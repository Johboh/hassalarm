package com.fjun.hassalarm;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

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

    private TextView mStatus;
    private TextView mLog;
    private ProgressBar mProgressBar;
    private Call<ResponseBody> mCall;
    private EditText mHostEditText;
    private EditText mApiKeyEditText;
    private EditText mEntityIdEditText;
    private CheckBox mIsToken;
    private boolean mLastRunWasSuccessful;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_connection);

        if (savedInstanceState != null) {
            mLastRunWasSuccessful = savedInstanceState.getBoolean(KEY_LAST_SUCCESSFUL, false);
        }

        mHostEditText = findViewById(R.id.host);
        mApiKeyEditText = findViewById(R.id.api_key);
        mEntityIdEditText = findViewById(R.id.entity_id);
        mIsToken = findViewById(R.id.isToken);
        mStatus = findViewById(R.id.status);
        mLog = findViewById(R.id.log);
        mLog.setMovementMethod(new ScrollingMovementMethod());
        mProgressBar = findViewById(R.id.progress);
        findViewById(R.id.test_button).setOnClickListener(view -> runTest());

        // Set current saved host and api key.
        final SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        mHostEditText.setText(sharedPreferences.getString(KEY_PREFS_HOST, ""));
        mApiKeyEditText.setText(sharedPreferences.getString(KEY_PREFS_API_KEY, ""));
        mEntityIdEditText.setText(sharedPreferences.getString(KEY_PREFS_ENTITY_ID, ""));
        mIsToken.setChecked(sharedPreferences.getBoolean(KEY_PREFS_IS_TOKEN,false));

        findViewById(R.id.save).setOnClickListener(v -> {
            if (mCall != null) {
                mCall.cancel();
            }
            sharedPreferences.edit()
                    .putString(KEY_PREFS_HOST, mHostEditText.getText().toString().trim())
                    .putString(KEY_PREFS_API_KEY, mApiKeyEditText.getText().toString().trim())
                    .putString(KEY_PREFS_ENTITY_ID, mEntityIdEditText.getText().toString().trim())
                    .putBoolean(KEY_PREFS_IS_TOKEN, mIsToken.isChecked())
                    .apply();
            Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
            NextAlarmUpdaterJob.markAsDone(this, mLastRunWasSuccessful);
            finish();
        });
    }

    private void runTest() {
        mProgressBar.setVisibility(View.VISIBLE);
        mStatus.setText(R.string.status_running);
        mLog.setText("");

        try {
            mCall = NextAlarmUpdaterJob.createRequestCall(this,
                    mHostEditText.getText().toString().trim(),
                    mApiKeyEditText.getText().toString().trim(),
                    mEntityIdEditText.getText().toString().trim(),
                    mIsToken.isChecked());

            mLog.append(getString(R.string.using_url, mCall.request().method(), mCall.request().url().toString()));
            mLog.append(getString(R.string.headers, mCall.request().headers().toString()));
            mLog.append(getString(R.string.body, requestBodyToString(mCall.request())));
            mCall.enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    boolean wasSuccessful = false;
                    try (ResponseBody body = response.body()) {
                        if (body != null) {
                            mLog.append(getString(R.string.connection_ok, body.string()));
                            wasSuccessful = true;
                        } else if (response.errorBody() != null) {
                            mLog.append(getString(R.string.connection_failure, response.errorBody().string()));
                        } else {
                            mLog.append(getString(R.string.connection_failure_code, response.code()));
                        }
                    } catch (IOException e) {
                        mLog.append(getString(R.string.connection_failure, e.getMessage()));
                    }
                    markAsDone(wasSuccessful);
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    mLog.append(getString(R.string.connection_failure, t.getMessage()));
                    markAsDone(false);
                }
            });
        } catch (IllegalArgumentException e) {
            mLog.append(e.getMessage() + "\n");
            markAsDone(false);
        }

        final InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && mHostEditText != null) {
            imm.hideSoftInputFromWindow(mHostEditText.getWindowToken(), 0);
        }
    }

    private static String requestBodyToString(final Request request){
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
        mProgressBar.setVisibility(View.GONE);
        mStatus.setText(R.string.status_done);
        mLastRunWasSuccessful = successful;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_LAST_SUCCESSFUL, mLastRunWasSuccessful);
    }

    public static Intent createIntent(Context context) {
        return new Intent(context, EditConnectionActivity.class);
    }
}
