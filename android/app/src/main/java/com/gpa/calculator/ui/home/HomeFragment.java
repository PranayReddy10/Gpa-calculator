package com.gpa.calculator.ui.home;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.chip.Chip;
import com.gpa.calculator.R;
import com.gpa.calculator.core.Calculator;
import com.gpa.calculator.core.CalculatorRegistry;
import com.gpa.calculator.core.Category;
import com.gpa.calculator.data.Repository;
import com.gpa.calculator.data.local.FavouriteEntity;
import com.gpa.calculator.data.local.HistoryEntity;
import com.gpa.calculator.databinding.FragmentHomeBinding;
import com.gpa.calculator.ui.calc.CalcLauncher;
import com.gpa.calculator.ui.common.Icons;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding b;
    private HomeAdapter adapter;
    @Nullable private Category category;
    private String query = "";
    private List<HomeSections.Recent> recents = Collections.emptyList();
    private List<String> favouriteIds = Collections.emptyList();
    private boolean firstLayout = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentHomeBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        b.greeting.setText(HomeSections.greeting(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) + " 👋");
        updateSubtitle();

        adapter = new HomeAdapter(new HomeAdapter.Listener() {
            @Override public void onOpen(Calculator calc, View sharedView, String inputsJson) {
                CalcLauncher.open(requireActivity(), sharedView, calc, inputsJson);
            }

            @Override public void onToggleFavourite(Calculator calc, boolean nowFavourite) {
                Repository.get(requireContext()).setFavourite(calc.id(), nowFavourite);
            }

            @Override public void onSeeAll(Category c) { selectCategory(c); }
        });
        int span = getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2;
        GridLayoutManager glm = new GridLayoutManager(requireContext(), span);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return adapter.isFullWidth(position) ? span : 1; }
        });
        b.grid.setLayoutManager(glm);
        b.grid.setAdapter(adapter);

        Repository repo = Repository.get(requireContext());
        repo.recentHistory().observe(getViewLifecycleOwner(), rows -> {
            List<HomeSections.Recent> list = new ArrayList<>();
            for (HistoryEntity e : rows) {
                Calculator c = CalculatorRegistry.byId(e.calcId);
                if (c != null) list.add(new HomeSections.Recent(c, e.summary, e.inputsJson, e.createdAt));
            }
            recents = list;
            refresh();
        });
        repo.favourites().observe(getViewLifecycleOwner(), favs -> {
            List<String> ids = new ArrayList<>();
            for (FavouriteEntity f : favs) ids.add(f.calcId);
            favouriteIds = ids;
            adapter.setFavourites(new HashSet<>(ids));
            updateSubtitle();
            refresh();
        });

        buildChips();
        b.search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int bf, int c) {}
            @Override public void afterTextChanged(Editable s) {
                query = s.toString();
                b.searchClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                refresh();
            }
        });
        b.searchClear.setOnClickListener(v -> b.search.setText(""));
        refresh();
    }

    private void updateSubtitle() {
        if (b == null) return;
        b.greetingSub.setText(getString(R.string.home_subtitle, CalculatorRegistry.count(), favouriteIds.size()));
    }

    private void buildChips() {
        b.chips.removeAllViews();
        b.chips.addView(chip(getString(R.string.all), null, 0));
        for (Category c : Category.values()) b.chips.addView(chip(c.title, c, Icons.resolve(requireContext(), c.iconName)));
        ((Chip) b.chips.getChildAt(0)).setChecked(true);
        b.chips.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            Chip chip = group.findViewById(ids.get(0));
            category = (Category) chip.getTag();
            b.grid.scrollToPosition(0);
            refresh();
        });
    }

    private void selectCategory(Category c) {
        for (int i = 0; i < b.chips.getChildCount(); i++) {
            Chip chip = (Chip) b.chips.getChildAt(i);
            if (chip.getTag() == c) {
                chip.setChecked(true);
                b.chipScroll.smoothScrollTo(chip.getLeft() - 32, 0);
                break;
            }
        }
        b.appBar.setExpanded(true, true);
    }

    private Chip chip(String text, @Nullable Category c, int icon) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.view_filter_chip, b.chips, false);
        chip.setText(text);
        chip.setTag(c);
        chip.setId(View.generateViewId());
        if (icon != 0) {
            chip.setChipIconResource(icon);
            chip.setChipIconVisible(true);
        }
        return chip;
    }

    private void refresh() {
        if (b == null) return;
        List<HomeSections.Item> items = HomeSections.build(query, category, recents, favouriteIds);
        boolean noResults = items.size() == 1 && items.get(0).type == HomeSections.Item.Type.SECTION;
        adapter.submitList(items, () -> {
            if (b == null) return;
            if (firstLayout) {
                firstLayout = false;
                b.grid.scheduleLayoutAnimation();
            }
        });
        b.empty.setVisibility(noResults ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
