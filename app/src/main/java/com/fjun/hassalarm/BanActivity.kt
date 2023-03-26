package com.fjun.hassalarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.fjun.hassalarm.databinding.ActivityBanlistBinding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static com.fjun.hassalarm.Constants.KEY_IGNORED_PACKAGES;
import static com.fjun.hassalarm.Constants.PREFS_NAME;

public class BanActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        com.fjun.hassalarm.databinding.ActivityBanlistBinding mBinding = ActivityBanlistBinding.inflate(getLayoutInflater());
        setContentView(mBinding.root);

        final AlarmManager alarmManager = getSystemService(AlarmManager.class);
        final AlarmManager.AlarmClockInfo alarmClockInfo = alarmManager.getNextAlarmClock();

        final String missingPackageName = getString(R.string.ban_no_alarm_set);
        final PendingIntent pendingIntent = alarmClockInfo != null ? alarmClockInfo.getShowIntent() : null;
        final String pendingPackageName = pendingIntent != null ? pendingIntent.getCreatorPackage() : missingPackageName;
        mBinding.add.setEnabled(pendingIntent != null);

        mBinding.nextPackage.setText(pendingPackageName != null ? pendingPackageName : missingPackageName);

        final SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        mBinding.list.setLayoutManager(new LinearLayoutManager(this));
        final BanAdapter adapter = new BanAdapter((packageName, adapter1) -> {
            // REMOVE
            final Set<String> set = sharedPreferences.getStringSet(KEY_IGNORED_PACKAGES, new HashSet<>());
            set.remove(packageName);
            save(sharedPreferences, set);
            setList(sharedPreferences, adapter1);
        });
        mBinding.list.setAdapter(adapter);

        mBinding.add.setOnClickListener(
                v -> {
                    // ADD
                    if (pendingIntent != null) {
                        final Set<String> set =
                                sharedPreferences.getStringSet(KEY_IGNORED_PACKAGES, new HashSet<>());
                        set.add(pendingIntent.getCreatorPackage());
                        save(sharedPreferences, set);
                        setList(sharedPreferences, adapter);
                    }
                });

        // LOAD
        setList(sharedPreferences, adapter);
    }

    private static void setList(SharedPreferences sharedPreferences, BanAdapter adapter) {
        adapter.set(new ArrayList<>(sharedPreferences.getStringSet(KEY_IGNORED_PACKAGES, new HashSet<>())));
    }

    private static void save(SharedPreferences sharedPreferences, Set<String> set) {
        sharedPreferences.edit().putStringSet(KEY_IGNORED_PACKAGES, set).apply();
    }

    public static Intent createIntent(Context context) {
        return new Intent(context, BanActivity.class);
    }
}
