package com.fjun.hassalarm;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.ContextMenu;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.fjun.hassalarm.databinding.ActivityTestConnectionBinding;
import com.fjun.hassalarm.history.AppDatabase;
import com.fjun.hassalarm.history.Publish;
import com.fjun.hassalarm.history.PublishDao;

import java.io.IOException;
import java.util.HashSet;

import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Buffer;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TestConnectionActivity extends AppCompatActivity {

  private static final String KEY_LAST_SUCCESSFUL = "last_run_successful";
  private static final String EXTRA_HOST = "host";
  private static final String EXTRA_TOKEN = "token";
  private static final String EXTRA_ENTITY_ID = "entity_id";
  private static final String EXTRA_ACCESS_TYPE = "access_type";
  private static final String EXTRA_ENTITY_ID_IS_LEGACY = "entity_id_is_legacy";

  private String mStrippedLog;
  private Boolean mLastRunWasSuccessful;
  private NextAlarmUpdaterJob.Request mRequest;
  private ActivityTestConnectionBinding mBinding;

  private String mHost;
  private String mToken;
  private String mEntityId;
  private Boolean mEntityIdIsLegacy;
  private Constants.AccessType mAccessType;

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

  public static Intent createIntent(
      Context context,
      String host,
      String token,
      String entityId,
      Constants.AccessType accessType,
      boolean entityIdIsLegacy) {
    final Intent intent = new Intent(context, TestConnectionActivity.class);
    intent.putExtra(EXTRA_HOST, host);
    intent.putExtra(EXTRA_TOKEN, token);
    intent.putExtra(EXTRA_ENTITY_ID, entityId);
    intent.putExtra(EXTRA_ACCESS_TYPE, accessType);
    intent.putExtra(EXTRA_ENTITY_ID_IS_LEGACY, entityIdIsLegacy);
    return intent;
  }

  public static Boolean getLastRunWasSuccessful(@Nullable Intent data) {
    if (data == null) {
      return null;
    }
    final Bundle extras = data.getExtras();
    return extras != null && extras.containsKey(KEY_LAST_SUCCESSFUL)
        ? data.getBooleanExtra(KEY_LAST_SUCCESSFUL, false)
        : null;
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = ActivityTestConnectionBinding.inflate(getLayoutInflater());
    setContentView(mBinding.root);

    if (savedInstanceState != null) {
      mLastRunWasSuccessful = savedInstanceState.getBoolean(KEY_LAST_SUCCESSFUL, false);
    }

    mBinding.close.setOnClickListener(view -> finish());

    if (savedInstanceState != null) {
      mHost = savedInstanceState.getString(EXTRA_HOST);
      mToken = savedInstanceState.getString(EXTRA_TOKEN);
      mEntityId = savedInstanceState.getString(EXTRA_ENTITY_ID);
      mAccessType = (Constants.AccessType) savedInstanceState.getSerializable(EXTRA_ACCESS_TYPE);
      mEntityIdIsLegacy = savedInstanceState.getBoolean(EXTRA_ENTITY_ID_IS_LEGACY);
    } else {
      mHost = getIntent().getStringExtra(EXTRA_HOST);
      mToken = getIntent().getStringExtra(EXTRA_TOKEN);
      mEntityId = getIntent().getStringExtra(EXTRA_ENTITY_ID);
      mAccessType = (Constants.AccessType) getIntent().getSerializableExtra(EXTRA_ACCESS_TYPE);
      mEntityIdIsLegacy = getIntent().getBooleanExtra(EXTRA_ENTITY_ID_IS_LEGACY, false);
    }

    mBinding.log.setMovementMethod(new ScrollingMovementMethod());
    mBinding.log.setOnLongClickListener(View::showContextMenu);

    runTest();
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    if (v.getId() == R.id.log) {
      final ClipboardManager clipboard =
          (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
      menu.add(R.string.copy_full_log)
          .setOnMenuItemClickListener(
              item -> {
                clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        "Hassalarm full connection log", mBinding.log.getText().toString()));
                return true;
              });
      if (!TextUtils.isEmpty(mStrippedLog)) {
        menu.add(R.string.copy_anonymous_log)
            .setOnMenuItemClickListener(
                item -> {
                  clipboard.setPrimaryClip(
                      ClipData.newPlainText("Hassalarm anonymous connection log", mStrippedLog));
                  return true;
                });
      }
    }
  }

  @Override
  protected void onStop() {
    super.onStop();
    if (mRequest != null) {
      mRequest.call().cancel();
    }
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    if (mLastRunWasSuccessful != null) {
      outState.putBoolean(KEY_LAST_SUCCESSFUL, mLastRunWasSuccessful);
    }
    outState.putString(EXTRA_HOST, mHost);
    outState.putString(EXTRA_TOKEN, mToken);
    outState.putString(EXTRA_ENTITY_ID, mEntityId);
    outState.putSerializable(EXTRA_ACCESS_TYPE, mAccessType);
    outState.putBoolean(EXTRA_ENTITY_ID_IS_LEGACY, mEntityIdIsLegacy);
  }

  private void runTest() {
    mBinding.progress.setVisibility(View.VISIBLE);
    mBinding.statusDrawable.setVisibility(View.GONE);
    mBinding.status.setText(R.string.status_running);
    mBinding.log.setText("");
    mStrippedLog = "";

    if (mAccessType == Constants.AccessType.LONG_LIVED_TOKEN) {
      mBinding.log.append(getString(R.string.log_using_token) + "\n");
    } else if (mAccessType == Constants.AccessType.WEB_HOOK) {
      mBinding.log.append(getString(R.string.log_using_webhook) + "\n");
    } else {
      mBinding.log.append(getString(R.string.log_using_api_key) + "\n");
    }
    if (mAccessType != Constants.AccessType.WEB_HOOK) {
      mBinding.log.append(
          (mEntityIdIsLegacy
                  ? getString(R.string.log_entity_id_is_legacy_sensor)
                  : getString(R.string.log_entity_id_is_input_datetime))
              + "\n");
    }

    try {
      mRequest =
          NextAlarmUpdaterJob.createRequest(
              this, mHost, mToken, mEntityId, mAccessType, mEntityIdIsLegacy, new HashSet<>());

      final Call<ResponseBody> call = mRequest.call();
      long triggerTimestamp = mRequest.triggerTimestamp();
      mBinding.log.append(
          getString(R.string.using_url, call.request().method(), call.request().url().toString())
              + "\n");
      mBinding.log.append(getString(R.string.headers, call.request().headers().toString()) + "\n");
      mBinding.log.append(getString(R.string.body, requestBodyToString(call.request())) + "\n");
      call.enqueue(
          new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
              boolean wasSuccessful = false;
              String message = "";
              try (ResponseBody body = response.body()) {
                if (body != null) {
                  message = body.string();
                  mBinding.log.append(getString(R.string.connection_ok, message) + "\n");
                  wasSuccessful = true;
                } else if (response.errorBody() != null) {
                  message = response.errorBody().string();
                  mBinding.log.append(
                      getString(R.string.connection_failure, message) + "\n");
                } else {
                  message = String.valueOf(response.code());
                  mBinding.log.append(
                      getString(R.string.connection_failure_code, message) + "\n");
                }
              } catch (IOException e) {
                message = e.getMessage();
                mBinding.log.append(getString(R.string.connection_failure, message) + "\n");
              }
              insertPublish(new Publish(System.currentTimeMillis(), wasSuccessful, triggerTimestamp, message));
              markAsDone(wasSuccessful);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
              final String message = t.getMessage();
              mBinding.log.append(getString(R.string.connection_failure, message) + "\n");
              insertPublish(new Publish(System.currentTimeMillis(), false, triggerTimestamp, message));
              markAsDone(false);
            }
          });
    } catch (IllegalArgumentException e) {
      final String message = e.getMessage();
      mBinding.log.append(message + "\n");
      insertPublish(new Publish(System.currentTimeMillis(), false, 0L, message));
      markAsDone(false);
    }

    mStrippedLog =
        mBinding.log.getText().toString().replace(mHost, "<host>").replace(mToken, "<token>");
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
    final Intent intent = new Intent();
    intent.putExtra(KEY_LAST_SUCCESSFUL, mLastRunWasSuccessful);
    setResult(AppCompatActivity.RESULT_OK, intent);
    registerForContextMenu(mBinding.log);
  }

  private PublishDao database() {
    return AppDatabase.getDatabase(getApplicationContext()).publishDao();
  }

  private void insertPublish(Publish publish) {
    AsyncTask.execute(() -> database().insertAll(publish));
  }
}
