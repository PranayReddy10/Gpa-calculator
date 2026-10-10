package com.gpa.calculator.ui.home;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.gpa.calculator.R;
import com.gpa.calculator.core.Calculator;
import com.gpa.calculator.core.Category;
import com.gpa.calculator.databinding.ItemCalcCardBinding;
import com.gpa.calculator.databinding.ItemFeaturedCardBinding;
import com.gpa.calculator.databinding.ItemHomeCarouselBinding;
import com.gpa.calculator.databinding.ItemHomeSectionBinding;
import com.gpa.calculator.databinding.ItemRecentCardBinding;
import com.gpa.calculator.ui.common.Anim;
import com.gpa.calculator.ui.common.CategoryStyle;
import com.gpa.calculator.ui.common.Haptics;
import com.gpa.calculator.ui.common.Icons;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Sectioned home list: headers, horizontal carousels and the calculator grid. */
public class HomeAdapter extends ListAdapter<HomeSections.Item, RecyclerView.ViewHolder> {
    public interface Listener {
        void onOpen(Calculator calc, View sharedView, String inputsJson);
        void onToggleFavourite(Calculator calc, boolean nowFavourite);
        void onSeeAll(Category category);
    }

    private static final int T_SECTION = 0, T_RECENT = 1, T_FAVS = 2, T_FEATURED = 3, T_CALC = 4;
    private final Listener listener;
    private final RecyclerView.RecycledViewPool pool = new RecyclerView.RecycledViewPool();
    private Set<String> favourites = Collections.emptySet();

    public HomeAdapter(Listener listener) {
        super(new DiffUtil.ItemCallback<HomeSections.Item>() {
            @Override public boolean areItemsTheSame(@NonNull HomeSections.Item a, @NonNull HomeSections.Item b) { return a.key.equals(b.key); }
            @Override public boolean areContentsTheSame(@NonNull HomeSections.Item a, @NonNull HomeSections.Item b) { return a.sameContent(b); }
        });
        this.listener = listener;
    }

    public void setFavourites(Set<String> ids) {
        favourites = ids;
        for (int i = 0; i < getItemCount(); i++) if (getItem(i).type == HomeSections.Item.Type.CALC) notifyItemChanged(i, "fav");
    }

    /** Grid span helper: only calculator cards share a row. */
    public boolean isFullWidth(int position) {
        return position < 0 || position >= getItemCount() || getItem(position).type != HomeSections.Item.Type.CALC;
    }

    @Override
    public int getItemViewType(int position) {
        switch (getItem(position).type) {
            case SECTION: return T_SECTION;
            case RECENT_ROW: return T_RECENT;
            case FAVOURITE_ROW: return T_FAVS;
            case FEATURED_ROW: return T_FEATURED;
            default: return T_CALC;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater li = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case T_SECTION: return new SectionHolder(ItemHomeSectionBinding.inflate(li, parent, false));
            case T_CALC: return new CalcHolder(ItemCalcCardBinding.inflate(li, parent, false));
            default: {
                ItemHomeCarouselBinding b = ItemHomeCarouselBinding.inflate(li, parent, false);
                b.carousel.setLayoutManager(new LinearLayoutManager(parent.getContext(), LinearLayoutManager.HORIZONTAL, false));
                b.carousel.setRecycledViewPool(pool);
                b.carousel.setLayoutAnimation(android.view.animation.AnimationUtils.loadLayoutAnimation(parent.getContext(), R.anim.layout_slide_in));
                return new CarouselHolder(b);
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
        HomeSections.Item item = getItem(position);
        if (h instanceof SectionHolder) ((SectionHolder) h).bind(item);
        else if (h instanceof CalcHolder) ((CalcHolder) h).bind(item.calc);
        else ((CarouselHolder) h).bind(item);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position, @NonNull List<Object> payloads) {
        if (payloads.contains("fav") && h instanceof CalcHolder) ((CalcHolder) h).bindFav(getItem(position).calc);
        else super.onBindViewHolder(h, position, payloads);
    }

    // ------------------------------------------------------------------ holders
    class SectionHolder extends RecyclerView.ViewHolder {
        final ItemHomeSectionBinding b;

        SectionHolder(ItemHomeSectionBinding b) { super(b.getRoot()); this.b = b; }

        void bind(HomeSections.Item item) {
            b.sectionTitle.setText(item.title);
            b.sectionCount.setText(item.subtitle);
            b.sectionCount.setVisibility(item.subtitle.isEmpty() ? View.GONE : View.VISIBLE);
            b.sectionSeeAll.setVisibility(item.seeAll == null ? View.GONE : View.VISIBLE);
            b.sectionSeeAll.setOnClickListener(v -> { Haptics.tick(v); listener.onSeeAll(item.seeAll); });
        }
    }

    class CalcHolder extends RecyclerView.ViewHolder {
        final ItemCalcCardBinding b;

        CalcHolder(ItemCalcCardBinding b) { super(b.getRoot()); this.b = b; }

        void bind(Calculator c) {
            b.name.setText(c.name());
            b.desc.setText(c.description());
            b.icon.setImageResource(Icons.resolve(b.icon.getContext(), c.icon()));
            CategoryStyle.applyIcon(b.iconBg, b.icon, c.category());
            bindFav(c);
            b.card.setOnClickListener(v -> listener.onOpen(c, b.card, null));
            b.fav.setOnClickListener(v -> {
                boolean now = !favourites.contains(c.id());
                Haptics.tick(v);
                Anim.pop(v);
                listener.onToggleFavourite(c, now);
            });
        }

        void bindFav(Calculator c) {
            boolean fav = favourites.contains(c.id());
            b.fav.setSelected(fav);
            b.fav.setImageResource(fav ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        }
    }

    class CarouselHolder extends RecyclerView.ViewHolder {
        final ItemHomeCarouselBinding b;

        CarouselHolder(ItemHomeCarouselBinding b) { super(b.getRoot()); this.b = b; }

        void bind(HomeSections.Item item) {
            RecyclerView.Adapter<?> inner;
            switch (item.type) {
                case RECENT_ROW: inner = new RecentAdapter(item.recents); break;
                case FAVOURITE_ROW: inner = new MiniAdapter(item.calcs); break;
                default: inner = new FeaturedAdapter(item.calcs);
            }
            b.carousel.setAdapter(inner);
        }
    }

    // ------------------------------------------------------------------ carousel adapters
    private class RecentAdapter extends RecyclerView.Adapter<RecentHolder> {
        final List<HomeSections.Recent> items;

        RecentAdapter(List<HomeSections.Recent> items) { this.items = items; }

        @Override public int getItemViewType(int position) { return 101; }

        @NonNull @Override public RecentHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new RecentHolder(ItemRecentCardBinding.inflate(LayoutInflater.from(p.getContext()), p, false));
        }

        @Override public void onBindViewHolder(@NonNull RecentHolder h, int pos) {
            HomeSections.Recent r = items.get(pos);
            h.b.name.setText(r.calc.name());
            h.b.summary.setText(r.summary);
            h.b.summary.setVisibility(r.summary.isEmpty() ? View.GONE : View.VISIBLE);
            h.b.when.setText(DateUtils.getRelativeTimeSpanString(r.at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE));
            h.b.icon.setImageResource(Icons.resolve(h.b.icon.getContext(), r.calc.icon()));
            CategoryStyle.applyIcon(h.b.iconBg, h.b.icon, r.calc.category());
            h.b.card.setOnClickListener(v -> listener.onOpen(r.calc, h.b.card, r.inputsJson));
        }

        @Override public int getItemCount() { return items.size(); }
    }

    private class MiniAdapter extends RecyclerView.Adapter<RecentHolder> {
        final List<Calculator> items;

        MiniAdapter(List<Calculator> items) { this.items = items; }

        @Override public int getItemViewType(int position) { return 102; }

        @NonNull @Override public RecentHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new RecentHolder(ItemRecentCardBinding.inflate(LayoutInflater.from(p.getContext()), p, false));
        }

        @Override public void onBindViewHolder(@NonNull RecentHolder h, int pos) {
            Calculator c = items.get(pos);
            h.b.name.setText(c.name());
            h.b.summary.setText(c.category().title);
            h.b.summary.setTextSize(13);
            h.b.when.setText("♥");
            h.b.when.setTextColor(h.b.when.getContext().getColor(R.color.heart));
            h.b.icon.setImageResource(Icons.resolve(h.b.icon.getContext(), c.icon()));
            CategoryStyle.applyIcon(h.b.iconBg, h.b.icon, c.category());
            h.b.card.setOnClickListener(v -> listener.onOpen(c, h.b.card, null));
        }

        @Override public int getItemCount() { return items.size(); }
    }

    private class FeaturedAdapter extends RecyclerView.Adapter<FeaturedHolder> {
        final List<Calculator> items;

        FeaturedAdapter(List<Calculator> items) { this.items = items; }

        @Override public int getItemViewType(int position) { return 103; }

        @NonNull @Override public FeaturedHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new FeaturedHolder(ItemFeaturedCardBinding.inflate(LayoutInflater.from(p.getContext()), p, false));
        }

        @Override public void onBindViewHolder(@NonNull FeaturedHolder h, int pos) {
            Calculator c = items.get(pos);
            float r = 24 * h.b.card.getResources().getDisplayMetrics().density;
            h.b.card.setBackground(CategoryStyle.gradient(h.b.card.getContext(), c.category(), r));
            h.b.card.setClipToOutline(true);
            h.b.name.setText(c.name());
            h.b.desc.setText(c.category().title);
            h.b.icon.setImageResource(Icons.resolve(h.b.icon.getContext(), c.icon()));
            h.b.card.setOnClickListener(v -> listener.onOpen(c, h.b.card, null));
        }

        @Override public int getItemCount() { return items.size(); }
    }

    static class RecentHolder extends RecyclerView.ViewHolder {
        final ItemRecentCardBinding b;
        RecentHolder(ItemRecentCardBinding b) { super(b.getRoot()); this.b = b; }
    }

    static class FeaturedHolder extends RecyclerView.ViewHolder {
        final ItemFeaturedCardBinding b;
        FeaturedHolder(ItemFeaturedCardBinding b) { super(b.getRoot()); this.b = b; }
    }
}
