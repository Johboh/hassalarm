package com.fjun.hassalarm;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fjun.hassalarm.databinding.RowIgnoredAppsBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for showing banned/ignored apps.
 */
public class BanAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface InteractionListener {
        // Passing back adapter.. ugly.. fix.
        void onRemove(@NonNull String packageName, @NonNull BanAdapter adapter);
    }

    private final InteractionListener mInteractionListener;
    private final List<String> mPackages = new ArrayList<>(0);

    public BanAdapter(@NonNull InteractionListener interactionListener) {
        mInteractionListener = interactionListener;
        setHasStableIds(true);
    }

    public void set(@NonNull List<String> packages) {
        mPackages.clear();
        mPackages.addAll(packages);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        RowIgnoredAppsBinding binding = RowIgnoredAppsBinding.inflate(LayoutInflater.from(parent.getContext()));
        return new Row(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = (Row) holder;
        row.bind(mPackages.get(position));
    }

    @Override
    public int getItemCount() {
        return mPackages.size();
    }

    @Override
    public long getItemId(int position) {
        return mPackages.get(position).hashCode();
    }

    private class Row extends RecyclerView.ViewHolder {

        private final RowIgnoredAppsBinding mBinding;

        public Row(@NonNull RowIgnoredAppsBinding rowIgnoredAppsBinding) {
            super(rowIgnoredAppsBinding.getRoot());
            mBinding = rowIgnoredAppsBinding;
        }

        public void bind(@NonNull String packageName) {
            mBinding.title.setText(packageName);
            mBinding.remove.setOnClickListener(v -> mInteractionListener.onRemove(packageName, BanAdapter.this));
        }
    }
}
