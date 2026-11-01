package com.gpa.calculator.ui.shop;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.gpa.calculator.R;
import com.gpa.calculator.data.remote.Analytics;
import com.gpa.calculator.data.remote.ConfigStore;
import com.gpa.calculator.data.remote.model.RemoteConfig;
import com.gpa.calculator.data.remote.model.ShopCatalog;
import com.gpa.calculator.data.shop.ShopQuery;
import com.gpa.calculator.data.shop.ShopRepo;
import com.gpa.calculator.databinding.FragmentShopBinding;

import java.util.List;

/** Shop tab: the shop's products with search, categories and sorting. */
public class ShopFragment extends Fragment {
    private static final String ARG_CATEGORY = "category";

    /** @param category top-level category slug to open on, or null for all products */
    public static ShopFragment newInstance(@Nullable String category) {
        ShopFragment f = new ShopFragment();
        if (category != null) {
            Bundle a = new Bundle();
            a.putString(ARG_CATEGORY, category);
            f.setArguments(a);
        }
        return f;
    }

    private FragmentShopBinding b;
    private ShopAdapter adapter;
    @Nullable private ShopCatalog catalog;
    @Nullable private String category;
    private String query = "";
    private ShopQuery.Sort sort = ShopQuery.Sort.FEATURED;
    private boolean firstLayout = true;
    private String builtChipsFor = "";

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = FragmentShopBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (savedInstanceState == null && getArguments() != null) category = getArguments().getString(ARG_CATEGORY);
        if (savedInstanceState != null) {
            category = savedInstanceState.getString("category");
            query = savedInstanceState.getString("query", "");
            sort = ShopQuery.Sort.values()[savedInstanceState.getInt("sort", 0)];
        }
        RemoteConfig.Shop cfg = ConfigStore.get(requireContext()).config().shop;
        b.shopName.setText(cfg.name);
        b.shopTitle.setText(cfg.title);
        b.shopSubtitle.setText(cfg.subtitle);
        b.shopSubtitle.setVisibility(cfg.subtitle == null || cfg.subtitle.isEmpty() ? View.GONE : View.VISIBLE);

        adapter = new ShopAdapter(p -> ShopNav.openProduct(requireContext(), p.id, "grid"), 0);
        GridLayoutManager glm = new GridLayoutManager(requireContext(), getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2);
        b.grid.setLayoutManager(glm);
        b.grid.setAdapter(adapter);

        b.search.setText(query);
        b.search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int bf, int c) {}
            @Override public void afterTextChanged(Editable s) {
                query = s.toString();
                b.searchClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                render();
            }
        });
        b.search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                if (!query.trim().isEmpty()) Analytics.get(requireContext()).log("shop_search", "q", query.trim());
                return true;
            }
            return false;
        });
        b.searchClear.setOnClickListener(v -> b.search.setText(""));
        b.sort.setOnClickListener(this::showSortMenu);
        b.refresh.setOnClickListener(v -> load(true));
        b.stateRetry.setOnClickListener(v -> load(true));

        load(false);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("category", category);
        out.putString("query", query);
        out.putInt("sort", sort.ordinal());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    private void load(boolean force) {
        if (catalog == null) showProgress();
        b.refresh.animate().rotationBy(360f).setDuration(600).start();
        ShopRepo.get(requireContext()).load(force, new ShopRepo.Callback() {
            @Override public void onCatalog(ShopCatalog c, boolean fromNetwork) {
                if (b == null) return;
                catalog = c;
                render();
            }

            @Override public void onError(String message) {
                if (b == null) return;
                if (catalog == null) {
                    showState(R.drawable.ic_storefront, getString(R.string.shop_error_title), getString(R.string.shop_error_body), true);
                } else {
                    Snackbar.make(b.getRoot(), R.string.shop_offline_copy, Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }

    private void render() {
        if (b == null || catalog == null) return;
        b.progress.setVisibility(View.GONE);
        buildChips(catalog);
        List<ShopCatalog.Product> list = ShopQuery.sort(ShopQuery.filter(catalog.products, category, query), sort);
        adapter.submitList(list, () -> {
            if (b == null) return;
            if (firstLayout && !list.isEmpty()) {
                firstLayout = false;
                b.grid.scheduleLayoutAnimation();
            }
        });
        if (list.isEmpty()) {
            showState(R.drawable.ic_search, getString(R.string.shop_empty), "", false);
        } else {
            b.state.setVisibility(View.GONE);
        }
    }

    private void buildChips(ShopCatalog c) {
        List<ShopCatalog.Category> top = ShopQuery.topCategories(c.categories);
        StringBuilder sig = new StringBuilder();
        for (ShopCatalog.Category x : top) sig.append(x.slug).append(',');
        if (sig.toString().equals(builtChipsFor)) return; // unchanged: keep the user's selection
        builtChipsFor = sig.toString();

        b.chips.setOnCheckedStateChangeListener(null);
        b.chips.removeAllViews();
        Chip all = chip(getString(R.string.shop_all), null);
        b.chips.addView(all);
        Chip selected = all;
        for (ShopCatalog.Category x : top) {
            Chip ch = chip(x.name, x.slug);
            b.chips.addView(ch);
            if (x.slug.equals(category)) selected = ch;
        }
        if (selected == all) category = null; // the category disappeared from the shop
        selected.setChecked(true);
        b.chips.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            Chip ch = group.findViewById(ids.get(0));
            category = (String) ch.getTag();
            if (category != null) Analytics.get(requireContext()).log("shop_category", "category", category);
            b.grid.scrollToPosition(0);
            render();
        });
    }

    /** Switches to a category (e.g. from "More Gift Combo" on a product page). */
    public void showCategory(String slug) {
        category = slug;
        builtChipsFor = ""; // rebuild so the chip selection follows
        if (b != null) {
            b.appBar.setExpanded(true, true);
            b.grid.scrollToPosition(0);
            render();
        }
    }

    private Chip chip(String text, @Nullable String slug) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.view_filter_chip, b.chips, false);
        chip.setText(text);
        chip.setTag(slug);
        chip.setId(View.generateViewId());
        return chip;
    }

    private void showSortMenu(View anchor) {
        PopupMenu m = new PopupMenu(requireContext(), anchor);
        m.getMenu().add(0, 0, 0, R.string.shop_sort_featured);
        m.getMenu().add(0, 1, 1, R.string.shop_sort_price_low);
        m.getMenu().add(0, 2, 2, R.string.shop_sort_price_high);
        m.getMenu().add(0, 3, 3, R.string.shop_sort_name);
        m.getMenu().setGroupCheckable(0, true, true);
        m.getMenu().getItem(sort.ordinal()).setChecked(true);
        m.setOnMenuItemClickListener(item -> {
            sort = ShopQuery.Sort.values()[item.getItemId()];
            b.grid.scrollToPosition(0);
            render();
            return true;
        });
        m.show();
    }

    private void showProgress() {
        b.progress.setVisibility(View.VISIBLE);
        b.state.setVisibility(View.GONE);
    }

    private void showState(int icon, String title, String body, boolean retry) {
        b.progress.setVisibility(View.GONE);
        b.state.setVisibility(View.VISIBLE);
        b.stateIcon.setImageResource(icon);
        b.stateTitle.setText(title);
        b.stateBody.setText(body);
        b.stateBody.setVisibility(body.isEmpty() ? View.GONE : View.VISIBLE);
        b.stateRetry.setVisibility(retry ? View.VISIBLE : View.GONE);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(b.search.getWindowToken(), 0);
        b.search.clearFocus();
    }
}
