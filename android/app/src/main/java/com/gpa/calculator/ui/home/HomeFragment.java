package com.gpa.calculator.ui.home;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.chip.Chip;
import com.gpa.calculator.R;
import com.gpa.calculator.core.Calculator;
import com.gpa.calculator.core.CalculatorRegistry;
import com.gpa.calculator.core.Category;
import com.gpa.calculator.data.Repository;
import com.gpa.calculator.databinding.FragmentHomeBinding;
import com.gpa.calculator.ui.calc.CalcLauncher;
import com.gpa.calculator.ui.common.Icons;

import java.util.HashSet;
import java.util.List;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding b;
    private CalcAdapter adapter;
    @Nullable private Category category;
    private String query = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentHomeBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Header sits under the status bar: pad it by the inset.
        final int basePad = b.header.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(b.header, (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), basePad + sb.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        adapter = new CalcAdapter(false, new CalcAdapter.Listener() {
            @Override public void onOpen(Calculator calc, View sharedView) {
                CalcLauncher.open(requireActivity(), sharedView, calc, null);
            }

            @Override public void onToggleFavourite(Calculator calc, boolean nowFavourite) {
                Repository.get(requireContext()).setFavourite(calc.id(), nowFavourite);
            }
        });
        int span = getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2;
        b.grid.setLayoutManager(new GridLayoutManager(requireContext(), span));
        b.grid.setAdapter(adapter);
        b.grid.setHasFixedSize(true);

        Repository.get(requireContext()).favouriteIds().observe(getViewLifecycleOwner(), ids -> adapter.setFavourites(new HashSet<>(ids)));

        buildChips();
        b.search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int bf, int c) {}
            @Override public void afterTextChanged(Editable s) {
                query = s.toString();
                b.searchClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                refresh(false);
            }
        });
        b.searchClear.setOnClickListener(v -> b.search.setText(""));
        refresh(true);
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
            refresh(true);
        });
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

    private void refresh(boolean animateLayout) {
        List<Calculator> list = CalculatorRegistry.search(query, category);
        adapter.submitList(list, () -> {
            if (animateLayout && b != null) b.grid.scheduleLayoutAnimation();
        });
        b.empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
