package com.gpa.calculator.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.gpa.calculator.R;
import com.gpa.calculator.core.Calculator;
import com.gpa.calculator.databinding.ItemCalcCardBinding;
import com.gpa.calculator.databinding.ItemFavouriteBinding;
import com.gpa.calculator.ui.common.Anim;
import com.gpa.calculator.ui.common.Haptics;
import com.gpa.calculator.ui.common.Icons;

import java.util.Collections;
import java.util.Set;

/** Grid (home) or list (favourites) adapter for calculators with an animated favourite toggle. */
public class CalcAdapter extends ListAdapter<Calculator, CalcAdapter.Holder> {
    public interface Listener {
        void onOpen(Calculator calc, View sharedView);
        void onToggleFavourite(Calculator calc, boolean nowFavourite);
    }

    private final boolean listMode;
    private final Listener listener;
    private Set<String> favourites = Collections.emptySet();

    public CalcAdapter(boolean listMode, Listener listener) {
        super(new DiffUtil.ItemCallback<Calculator>() {
            @Override public boolean areItemsTheSame(@NonNull Calculator a, @NonNull Calculator b) { return a.id().equals(b.id()); }
            @Override public boolean areContentsTheSame(@NonNull Calculator a, @NonNull Calculator b) { return a.id().equals(b.id()); }
        });
        this.listMode = listMode;
        this.listener = listener;
    }

    public void setFavourites(Set<String> ids) {
        favourites = ids;
        notifyItemRangeChanged(0, getItemCount(), "fav");
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater li = LayoutInflater.from(parent.getContext());
        if (listMode) {
            ItemFavouriteBinding b = ItemFavouriteBinding.inflate(li, parent, false);
            return new Holder(b.getRoot(), b.card, b.icon, b.name, b.desc, b.fav);
        }
        ItemCalcCardBinding b = ItemCalcCardBinding.inflate(li, parent, false);
        return new Holder(b.getRoot(), b.card, b.icon, b.name, b.desc, b.fav);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Calculator c = getItem(position);
        h.name.setText(c.name());
        h.desc.setText(c.description());
        h.icon.setImageResource(Icons.resolve(h.icon.getContext(), c.icon()));
        bindFav(h, c, false);
        h.card.setOnClickListener(v -> listener.onOpen(c, h.card));
        h.fav.setOnClickListener(v -> {
            boolean now = !favourites.contains(c.id());
            Haptics.tick(v);
            Anim.pop(v);
            listener.onToggleFavourite(c, now);
        });
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position, @NonNull java.util.List<Object> payloads) {
        if (payloads.contains("fav")) bindFav(h, getItem(position), true);
        else super.onBindViewHolder(h, position, payloads);
    }

    private void bindFav(Holder h, Calculator c, boolean animate) {
        boolean fav = favourites.contains(c.id());
        h.fav.setSelected(fav);
        h.fav.setImageResource(fav ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
    }

    static class Holder extends RecyclerView.ViewHolder {
        final View card;
        final android.widget.ImageView icon;
        final android.widget.TextView name, desc;
        final android.widget.ImageButton fav;

        Holder(View root, View card, android.widget.ImageView icon, android.widget.TextView name, android.widget.TextView desc, android.widget.ImageButton fav) {
            super(root);
            this.card = card;
            this.icon = icon;
            this.name = name;
            this.desc = desc;
            this.fav = fav;
        }
    }
}
