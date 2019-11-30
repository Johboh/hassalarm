package com.fjun.hassalarm;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Buffer;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TestConnectionActivity extends AppCompatActivity {

    private TextView mStatus;
    private TextView mLog;
    private ProgressBar mProgressBar;
    private Call<ResponseBody> mCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_connection);

        mStatus = findViewById(R.id.status);
        mLog = findViewById(R.id.log);
        mProgressBar = findViewById(R.id.progress);
        findViewById(R.id.close).setOnClickListener(v -> {
            if (mCall != null) {
                mCall.cancel();
            }
            finish();
        });

        runTest();
    }

    private void runTest() {
        mStatus.setText(R.string.status_running);

        try {
            mCall = NextAlarmUpdaterJob.createRequestCall(this);

            mLog.append(getString(R.string.using_url, mCall.request().method(), mCall.request().url().toString()));
            mLog.append(getString(R.string.headers, mCall.request().headers().toString()));
            mLog.append(getString(R.string.body, requestBodyToString(mCall.request())));
            mCall.enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    try (ResponseBody body = response.body()) {
                        if (body != null) {
                            mLog.append(getString(R.string.connection_ok, body.string()));
                        } else if (response.errorBody() != null) {
                            mLog.append(getString(R.string.connection_failure, response.errorBody().string()));
                        } else {
                            mLog.append(getString(R.string.connection_failure_code, response.code()));
                        }
                    } catch (IOException e) {
                        mLog.append(getString(R.string.connection_failure, e.getMessage()));
                    }
                    markAsDone();
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    mLog.append(getString(R.string.connection_failure, t.getMessage()));
                    markAsDone();
                }
            });
        } catch (IllegalArgumentException e) {
            mLog.append(e.getMessage() + "\n");
            markAsDone();
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

    private void markAsDone() {
        mProgressBar.setVisibility(View.GONE);
        mStatus.setText(R.string.status_done);
    }

    public static Intent createIntent(Context context) {
        return new Intent(context, TestConnectionActivity.class);
    }
}
