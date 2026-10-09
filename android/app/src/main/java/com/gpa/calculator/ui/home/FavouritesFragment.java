package com.gpa.calculator.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.gpa.calculator.R;
import com.gpa.calculator.core.Calculator;
import com.gpa.calculator.core.CalculatorRegistry;
import com.gpa.calculator.data.Repository;
import com.gpa.calculator.data.local.FavouriteEntity;
import com.gpa.calculator.databinding.FragmentListBinding;
import com.gpa.calculator.ui.calc.CalcLauncher;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FavouritesFragment extends Fragment {
    private FragmentListBinding b;
    private CalcAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        b = FragmentListBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle s) {
        b.toolbar.setTitle(R.string.nav_favourites);
        b.emptyIcon.setImageResource(R.drawable.ic_favorite);
        b.emptyTitle.setText(R.string.favourites_empty_title);
        b.emptyBody.setText(R.string.favourites_empty_body);
        adapter = new CalcAdapter(true, new CalcAdapter.Listener() {
            @Override public void onOpen(Calculator calc, View sharedView) {
                CalcLauncher.open(requireActivity(), sharedView, calc, null);
            }

            @Override public void onToggleFavourite(Calculator calc, boolean nowFavourite) {
                Repository.get(requireContext()).setFavourite(calc.id(), nowFavourite);
            }
        });
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        Repository.get(requireContext()).favourites().observe(getViewLifecycleOwner(), favs -> {
            List<Calculator> list = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (FavouriteEntity f : favs) {
                Calculator c = CalculatorRegistry.byId(f.calcId);
                if (c != null) { list.add(c); ids.add(c.id()); }
            }
            boolean first = adapter.getItemCount() == 0;
            adapter.setFavourites(ids);
            adapter.submitList(list, () -> { if (first && b != null) b.list.scheduleLayoutAnimation(); });
            b.empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
