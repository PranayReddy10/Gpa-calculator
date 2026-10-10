package com.gpa.calculator.ui.home;

import android.content.Intent;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.gpa.calculator.R;
import com.gpa.calculator.core.Calculator;
import com.gpa.calculator.core.CalculatorRegistry;
import com.gpa.calculator.data.Repository;
import com.gpa.calculator.data.local.HistoryEntity;
import com.gpa.calculator.data.remote.Analytics;
import com.gpa.calculator.databinding.FragmentListBinding;
import com.gpa.calculator.databinding.ItemHistoryBinding;
import com.gpa.calculator.databinding.ItemHistoryHeaderBinding;
import com.gpa.calculator.ui.calc.CalcLauncher;
import com.gpa.calculator.ui.common.CategoryStyle;
import com.gpa.calculator.ui.common.Haptics;
import com.gpa.calculator.ui.common.Icons;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** History grouped by day, with swipe-to-delete, Clear all and CSV export. */
public class HistoryFragment extends Fragment {
    private FragmentListBinding b;
    private Adapter adapter;
    private List<HistoryEntity> current = Collections.emptyList();

    /** A list row: either a date header or a history entry. */
    static final class Row {
        final String header;
        final HistoryEntity entry;

        Row(String header, HistoryEntity entry) {
            this.header = header;
            this.entry = entry;
        }

        String key() { return header != null ? "h_" + header : "e_" + entry.id; }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentListBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        b.toolbar.setTitle(R.string.nav_history);
        b.toolbar.inflateMenu(R.menu.menu_history);
        b.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_clear) { confirmClear(); return true; }
            if (item.getItemId() == R.id.action_export) { exportCsv(); return true; }
            return false;
        });
        b.emptyIcon.setImageResource(R.drawable.ic_history);
        b.emptyTitle.setText(R.string.history_empty_title);
        b.emptyBody.setText(R.string.history_empty_body);
        adapter = new Adapter();
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);

        Repository.get(requireContext()).history().observe(getViewLifecycleOwner(), list -> {
            current = list;
            boolean first = adapter.getItemCount() == 0;
            adapter.submitList(group(list), () -> { if (first && b != null) b.list.scheduleLayoutAnimation(); });
            b.empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            b.toolbar.getMenu().findItem(R.id.action_clear).setVisible(!list.isEmpty());
            b.toolbar.getMenu().findItem(R.id.action_export).setVisible(!list.isEmpty());
        });

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override public int getSwipeDirs(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                return vh instanceof EntryHolder ? super.getSwipeDirs(rv, vh) : 0;
            }

            @Override public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder t) { return false; }

            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
                int pos = vh.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                HistoryEntity e = adapter.getItem(pos).entry;
                if (e == null) return;
                Haptics.confirm(vh.itemView);
                Repository.get(requireContext()).deleteHistory(e);
                Snackbar.make(b.getRoot(), R.string.history_deleted, Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo, v -> Repository.get(requireContext()).io().execute(() -> {
                            e.id = 0; e.synced = false; e.remoteId = 0;
                            Repository.get(requireContext()).db().history().insert(e);
                        })).show();
            }

            @Override public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, float dX, float dY, int state, boolean active) {
                if (!(vh instanceof EntryHolder)) return;
                View card = ((EntryHolder) vh).b.card;
                card.setTranslationX(dX);
                ((EntryHolder) vh).b.swipeBg.setAlpha(Math.min(1f, Math.abs(dX) / Math.max(1, card.getWidth()) * 2));
            }

            @Override public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                if (vh instanceof EntryHolder) ((EntryHolder) vh).b.card.setTranslationX(0);
                super.clearView(rv, vh);
            }
        }).attachToRecyclerView(b.list);
    }

    static List<Row> group(List<HistoryEntity> list) {
        List<Row> rows = new ArrayList<>();
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        String last = null;
        for (HistoryEntity e : list) {
            String label = HistoryGrouping.label(e.createdAt, now, zone);
            if (!label.equals(last)) {
                rows.add(new Row(label, null));
                last = label;
            }
            rows.add(new Row(null, e));
        }
        return rows;
    }

    // ------------------------------------------------------------------ clear all
    private void confirmClear() {
        int n = current.size();
        if (n == 0) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setIcon(R.drawable.ic_delete)
                .setTitle(R.string.history_clear_title)
                .setMessage(getString(R.string.history_clear_body, n))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.history_clear, (d, w) -> {
                    Analytics.get(requireContext()).log("history_clear", "count", String.valueOf(n));
                    // fade the list out, then clear (LiveData shows the empty state)
                    b.list.animate().alpha(0f).translationY(40f).setDuration(220).withEndAction(() -> {
                        Repository.get(requireContext()).clearHistory();
                        if (b == null) return;
                        b.list.setAlpha(1f);
                        b.list.setTranslationY(0f);
                        Snackbar.make(b.getRoot(), R.string.history_cleared, Snackbar.LENGTH_SHORT).show();
                    }).start();
                })
                .show();
    }

    // ------------------------------------------------------------------ CSV export
    private void exportCsv() {
        List<HistoryEntity> rows = new ArrayList<>(current);
        android.content.Context app = requireContext().getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        Repository.get(app).io().execute(() -> {
            try {
                File dir = new File(app.getCacheDir(), "shared");
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("mkdirs");
                File f = new File(dir, "gpa-calculator-history.csv");
                java.text.DateFormat df = DateFormat.getMediumDateFormat(app);
                java.text.DateFormat tf = DateFormat.getTimeFormat(app);
                try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
                    w.write('﻿'); // BOM so Excel opens UTF-8 correctly
                    w.write("Date,Time,Calculator,Inputs,Result\n");
                    for (HistoryEntity e : rows) {
                        Date d = new Date(e.createdAt);
                        w.write(csv(df.format(d)) + "," + csv(tf.format(d)) + "," + csv(e.title) + ","
                                + csv(inputsSummary(e.inputsJson)) + "," + csv(e.summary) + "\n");
                    }
                }
                Uri uri = FileProvider.getUriForFile(app, app.getPackageName() + ".fileprovider", f);
                main.post(() -> {
                    if (!isAdded()) return;
                    Intent i = new Intent(Intent.ACTION_SEND).setType("text/csv")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " history")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(i, getString(R.string.history_export)));
                    Analytics.get(app).log("history_export", "count", String.valueOf(rows.size()));
                });
            } catch (Exception ex) {
                main.post(() -> { if (b != null) Snackbar.make(b.getRoot(), R.string.history_export_failed, Snackbar.LENGTH_LONG).show(); });
            }
        });
    }

    static String csv(String v) {
        if (v == null) return "";
        boolean quote = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r");
        String s = v.replace("\"", "\"\"");
        return quote ? "\"" + s + "\"" : s;
    }

    // ------------------------------------------------------------------ adapter
    private class Adapter extends ListAdapter<Row, RecyclerView.ViewHolder> {
        Adapter() {
            super(new DiffUtil.ItemCallback<Row>() {
                @Override public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) { return a.key().equals(b.key()); }
                @Override public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
                    return a.header != null ? a.header.equals(b.header) : a.entry.id == b.entry.id && a.entry.synced == b.entry.synced;
                }
            });
        }

        @Override public Row getItem(int position) { return super.getItem(position); }

        @Override public int getItemViewType(int position) { return getItem(position).header != null ? 0 : 1; }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater li = LayoutInflater.from(parent.getContext());
            if (viewType == 0) return new HeaderHolder(ItemHistoryHeaderBinding.inflate(li, parent, false));
            return new EntryHolder(ItemHistoryBinding.inflate(li, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row r = getItem(position);
            if (holder instanceof HeaderHolder) {
                ((HeaderHolder) holder).b.header.setText(r.header);
                return;
            }
            EntryHolder h = (EntryHolder) holder;
            HistoryEntity e = r.entry;
            Calculator c = CalculatorRegistry.byId(e.calcId);
            h.b.title.setText(e.title);
            h.b.summary.setText(e.summary);
            h.b.icon.setImageResource(Icons.resolve(h.b.icon.getContext(), c == null ? "ic_calculate" : c.icon()));
            if (c != null) CategoryStyle.applyIcon(h.b.iconBg, h.b.icon, c.category());
            String when = DateUtils.formatDateTime(h.b.title.getContext(), e.createdAt, DateUtils.FORMAT_SHOW_TIME);
            String inputs = inputsSummary(e.inputsJson);
            h.b.subtitle.setText(inputs.isEmpty() ? when : when + " · " + inputs);
            h.b.card.setTranslationX(0);
            h.b.swipeBg.setAlpha(0f);
            h.b.card.setOnClickListener(v -> {
                if (c != null) CalcLauncher.open(requireActivity(), h.b.card, c, e.inputsJson);
            });
        }
    }

    static String inputsSummary(String json) {
        try {
            Map<String, Object> m = new Gson().fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Object> en : m.entrySet()) {
                Object v = en.getValue();
                if (v == null || v.toString().isEmpty() || v.toString().startsWith("[")) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append(en.getKey().replace('_', ' ')).append(' ').append(v);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final ItemHistoryHeaderBinding b;
        HeaderHolder(ItemHistoryHeaderBinding b) { super(b.getRoot()); this.b = b; }
    }

    static class EntryHolder extends RecyclerView.ViewHolder {
        final ItemHistoryBinding b;
        EntryHolder(ItemHistoryBinding b) { super(b.getRoot()); this.b = b; }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
