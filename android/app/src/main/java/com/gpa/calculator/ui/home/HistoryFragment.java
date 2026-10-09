package com.gpa.calculator.ui.home;

import android.graphics.Canvas;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.gpa.calculator.R;
import com.gpa.calculator.core.Calculator;
import com.gpa.calculator.core.CalculatorRegistry;
import com.gpa.calculator.data.Repository;
import com.gpa.calculator.data.local.HistoryEntity;
import com.gpa.calculator.databinding.FragmentListBinding;
import com.gpa.calculator.databinding.ItemHistoryBinding;
import com.gpa.calculator.ui.calc.CalcLauncher;
import com.gpa.calculator.ui.common.Haptics;
import com.gpa.calculator.ui.common.Icons;

import java.util.Map;

public class HistoryFragment extends Fragment {
    private FragmentListBinding b;
    private Adapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentListBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        b.toolbar.setTitle(R.string.nav_history);
        b.emptyIcon.setImageResource(R.drawable.ic_history);
        b.emptyTitle.setText(R.string.history_empty_title);
        b.emptyBody.setText(R.string.history_empty_body);
        adapter = new Adapter();
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        Repository.get(requireContext()).history().observe(getViewLifecycleOwner(), list -> {
            boolean first = adapter.getItemCount() == 0;
            adapter.submitList(list, () -> { if (first && b != null) b.list.scheduleLayoutAnimation(); });
            b.empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder t) { return false; }

            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
                HistoryEntity e = adapter.getItem(vh.getBindingAdapterPosition());
                Haptics.confirm(vh.itemView);
                Repository.get(requireContext()).deleteHistory(e);
                Snackbar.make(b.getRoot(), R.string.delete, Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo, v -> Repository.get(requireContext()).io().execute(() -> {
                            e.id = 0; e.synced = false; e.remoteId = 0;
                            Repository.get(requireContext()).db().history().insert(e);
                        })).show();
            }

            @Override public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, float dX, float dY, int state, boolean active) {
                // Only slide the card; the delete background stays in place behind it.
                View card = ((Holder) vh).b.card;
                card.setTranslationX(dX);
                ((Holder) vh).b.swipeBg.setAlpha(Math.min(1f, Math.abs(dX) / card.getWidth() * 2));
            }

            @Override public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                ((Holder) vh).b.card.setTranslationX(0);
                super.clearView(rv, vh);
            }
        }).attachToRecyclerView(b.list);
    }

    private class Adapter extends ListAdapter<HistoryEntity, Holder> {
        Adapter() {
            super(new DiffUtil.ItemCallback<HistoryEntity>() {
                @Override public boolean areItemsTheSame(@NonNull HistoryEntity a, @NonNull HistoryEntity b) { return a.id == b.id; }
                @Override public boolean areContentsTheSame(@NonNull HistoryEntity a, @NonNull HistoryEntity b) { return a.id == b.id && a.synced == b.synced; }
            });
        }

        @Override public HistoryEntity getItem(int position) { return super.getItem(position); }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemHistoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override public void onBindViewHolder(@NonNull Holder h, int position) {
            HistoryEntity e = getItem(position);
            Calculator c = CalculatorRegistry.byId(e.calcId);
            h.b.title.setText(e.title);
            h.b.summary.setText(e.summary);
            h.b.icon.setImageResource(Icons.resolve(h.b.icon.getContext(), c == null ? "ic_calculate" : c.icon()));
            String when = DateUtils.getRelativeTimeSpanString(e.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString();
            h.b.subtitle.setText(when + " · " + inputsSummary(e.inputsJson));
            h.b.card.setTranslationX(0);
            h.b.swipeBg.setAlpha(0f);
            h.b.card.setOnClickListener(v -> {
                if (c != null) CalcLauncher.open(requireActivity(), h.b.card, c, e.inputsJson);
            });
        }
    }

    private static String inputsSummary(String json) {
        try {
            Map<String, Object> m = new Gson().fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Object> en : m.entrySet()) {
                if (en.getValue() == null || en.getValue().toString().isEmpty() || en.getValue() instanceof java.util.List) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append(en.getKey()).append(' ').append(en.getValue());
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemHistoryBinding b;
        Holder(ItemHistoryBinding b) { super(b.getRoot()); this.b = b; }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
