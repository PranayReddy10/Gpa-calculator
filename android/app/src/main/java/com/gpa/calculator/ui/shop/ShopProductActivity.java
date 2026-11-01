package com.gpa.calculator.ui.shop;

import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.gpa.calculator.R;
import com.gpa.calculator.data.remote.Analytics;
import com.gpa.calculator.data.remote.model.ShopCatalog;
import com.gpa.calculator.data.remote.model.ShopCatalog.Personalisation;
import com.gpa.calculator.data.remote.model.ShopCatalog.Product;
import com.gpa.calculator.data.remote.model.ShopCatalog.Variant;
import com.gpa.calculator.data.shop.ShopQuery;
import com.gpa.calculator.data.shop.ShopRepo;
import com.gpa.calculator.databinding.ActivityShopProductBinding;
import com.gpa.calculator.databinding.ViewShopPersonalRowBinding;
import com.gpa.calculator.ui.BaseActivity;
import com.gpa.calculator.ui.common.ImageLoader;
import com.gpa.calculator.ui.common.ShareUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One product: photos, price, options, what to personalise, description and "Personalise & buy". */
public class ShopProductActivity extends BaseActivity {
    public static final String EXTRA_ID = "product_id";
    public static final String EXTRA_SOURCE = "source";

    private ActivityShopProductBinding b;
    private Product product;
    private final Map<String, String> selected = new LinkedHashMap<>();
    @Nullable private Variant variant;
    private boolean expanded;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityShopProductBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        padTopInset(b.topBar);
        padBottomInset(b.buyBar);

        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        int id = getIntent().getIntExtra(EXTRA_ID, -1);
        product = ShopRepo.get(this).product(id);
        if (product != null) {
            bind(savedInstanceState == null);
        } else {
            // App was restarted on this screen: the catalogue is loaded from the phone's copy first.
            b.buyBar.setVisibility(View.INVISIBLE);
            ShopRepo.get(this).load(false, new ShopRepo.Callback() {
                @Override public void onCatalog(ShopCatalog c, boolean fromNetwork) {
                    if (product != null || isFinishing()) return;
                    product = ShopRepo.get(ShopProductActivity.this).product(id);
                    if (product == null) { finish(); return; }
                    b.buyBar.setVisibility(View.VISIBLE);
                    bind(false);
                }

                @Override public void onError(String message) {
                    if (product == null) finish();
                }
            });
        }
    }

    private void bind(boolean logView) {
        if (logView) {
            Analytics.get(this).log("shop_product", "product", String.valueOf(product.id),
                    "source", String.valueOf(getIntent().getStringExtra(EXTRA_SOURCE)));
        }
        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        b.share.setOnClickListener(v -> ShareUtil.shareText(this,
                getString(R.string.shop_share_text, product.name, ShopQuery.priceLabel(product), product.url)));
        b.buy.setOnClickListener(v -> ShopNav.openCheckout(this, buyUrl(), product.name, product.id));
        b.regular.setPaintFlags(b.regular.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);

        bindGallery(product.images.isEmpty() ? listOf(product.image) : product.images);
        b.category.setText(product.category);
        b.category.setVisibility(product.category.isEmpty() ? View.GONE : View.VISIBLE);
        b.name.setText(product.name);
        if (product.rating.count > 0) {
            b.rating.setVisibility(View.VISIBLE);
            b.rating.setText(getString(R.string.shop_rating, product.rating.average, product.rating.count));
        }
        bindFacts();
        bindOptions();
        bindPersonalisation();
        bindDescription();
        bindRelated();
        updatePrice();
    }

    // ------------------------------------------------------------------ gallery
    private void bindGallery(List<String> photos) {
        int size = getResources().getDisplayMetrics().widthPixels;
        b.gallery.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shop_gallery, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }

            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
                ImageView iv = (ImageView) h.itemView;
                iv.setContentDescription(product.name);
                ImageLoader.get(iv.getContext()).load(iv, photos.get(pos), size);
            }

            @Override public int getItemCount() { return photos.size(); }
        });
        b.counter.setVisibility(photos.size() > 1 ? View.VISIBLE : View.GONE);
        b.counter.setText(getString(R.string.shop_photo_counter, 1, photos.size()));
        b.gallery.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                b.counter.setText(getString(R.string.shop_photo_counter, position + 1, photos.size()));
            }
        });
    }

    // ------------------------------------------------------------------ price + facts
    private void updatePrice() {
        double price = variant != null ? variant.price : product.price;
        double regular = variant != null ? variant.regular_price : product.regular_price;
        String label;
        if (variant != null) label = ShopQuery.money(price, product.currency);
        else if (!product.attributes.isEmpty() && product.max_price <= product.price + 0.005)
            label = getString(R.string.shop_from, ShopQuery.money(product.price, product.currency));
        else label = ShopQuery.priceLabel(product);
        b.price.setText(label);
        b.barPrice.setText(label);
        boolean discounted = regular > price + 0.005;
        b.regular.setVisibility(discounted ? View.VISIBLE : View.GONE);
        if (discounted) b.regular.setText(ShopQuery.money(regular, product.currency));
        int pct = discounted ? (int) Math.round((regular - price) * 100 / regular) : 0;
        b.discount.setVisibility(pct > 0 ? View.VISIBLE : View.GONE);
        if (pct > 0) b.discount.setText(getString(R.string.shop_off_percent, pct));

        boolean inStock = variant != null ? variant.in_stock : product.in_stock;
        b.buy.setEnabled(inStock);
        b.buy.setText(inStock ? R.string.shop_buy : R.string.shop_out_of_stock);
        b.barHint.setText(product.production_days > 0
                ? getResources().getQuantityString(R.plurals.shop_ready_in, product.production_days, product.production_days)
                : product.category);
    }

    private void bindFacts() {
        ChipGroup g = b.facts;
        g.removeAllViews();
        if (product.production_days > 0) {
            g.addView(fact(getResources().getQuantityString(R.plurals.shop_ready_in, product.production_days, product.production_days), R.drawable.ic_timer));
        }
        if (!product.personalisation.isEmpty()) g.addView(fact(getString(R.string.shop_personalise), R.drawable.ic_add_photo));
        if (product.featured) g.addView(fact(getString(R.string.shop_featured), R.drawable.ic_star));
        if (!product.in_stock) g.addView(fact(getString(R.string.shop_out_of_stock), R.drawable.ic_block));
        g.setVisibility(g.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private Chip fact(String text, int icon) {
        Chip c = new Chip(this);
        c.setText(text);
        c.setChipIconResource(icon);
        c.setChipIconVisible(true);
        c.setClickable(false);
        c.setCheckable(false);
        return c;
    }

    // ------------------------------------------------------------------ options (pack size, shape, …)
    private void bindOptions() {
        if (product.attributes.isEmpty()) return;
        b.optionsSection.setVisibility(View.VISIBLE);
        for (Map.Entry<String, List<String>> e : product.attributes.entrySet()) {
            TextView label = new TextView(this);
            label.setText(e.getKey());
            label.setTextAppearance(R.style.Text_GPA_Label);
            label.setPadding(0, dp(10), 0, dp(4));
            b.options.addView(label);
            ChipGroup group = new ChipGroup(this);
            group.setSingleSelection(true);
            for (String value : e.getValue()) {
                Chip c = (Chip) getLayoutInflater().inflate(R.layout.view_filter_chip, group, false);
                c.setId(View.generateViewId());
                c.setText(value);
                c.setOnCheckedChangeListener((btn, checked) -> {
                    if (checked) selected.put(e.getKey(), value);
                    else if (value.equals(selected.get(e.getKey()))) selected.remove(e.getKey());
                    onOptionChanged();
                });
                group.addView(c);
            }
            b.options.addView(group, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void onOptionChanged() {
        variant = selected.size() == product.attributes.size() ? ShopQuery.matchVariant(product, selected) : null;
        if (variant != null && !variant.image.isEmpty()) {
            List<String> photos = product.images;
            int idx = photos.indexOf(variant.image);
            if (idx >= 0) b.gallery.setCurrentItem(idx, true);
        }
        updatePrice();
    }

    /** The variant's own page when the shop listed one for the chosen options, else the product page. */
    private String buyUrl() {
        return variant != null && !variant.url.isEmpty() ? variant.url : product.url;
    }

    // ------------------------------------------------------------------ personalisation
    private void bindPersonalisation() {
        if (product.personalisation.isEmpty()) return;
        b.personalCard.setVisibility(View.VISIBLE);
        for (Personalisation x : product.personalisation) {
            ViewShopPersonalRowBinding r = ViewShopPersonalRowBinding.inflate(getLayoutInflater(), b.personalRows, true);
            r.title.setText(x.label.isEmpty() ? getString(R.string.shop_personalise) : x.label);
            r.required.setText(x.required ? R.string.shop_required : R.string.shop_optional);
            r.icon.setImageResource(iconFor(x));
            r.detail.setText(detailFor(x));
            r.help.setText(x.help);
            r.help.setVisibility(x.help.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private static int iconFor(Personalisation x) {
        switch (x.type) {
            case "images": case "image": case "file": return R.drawable.ic_add_photo;
            case "text": case "textarea": return R.drawable.ic_edit_note;
            default: return R.drawable.ic_tune;
        }
    }

    private String detailFor(Personalisation x) {
        switch (x.type) {
            case "images": case "image": case "file":
                if (x.max_files > 1) return getString(R.string.shop_photos_up_to, x.max_files);
                if (x.max_files == 1) return getString(R.string.shop_photo_one);
                return getString(R.string.shop_photos_some);
            case "text": case "textarea":
                return x.max_length > 0 ? getString(R.string.shop_text_up_to, x.max_length) : getString(R.string.shop_text_any);
            default:
                return x.options.isEmpty() ? "" : getString(R.string.shop_choose_from, String.join(" · ", x.options));
        }
    }

    // ------------------------------------------------------------------ description + related
    private void bindDescription() {
        String text = !product.description.isEmpty() ? product.description : product.shortText;
        b.description.setText(text);
        boolean hasText = !text.isEmpty();
        b.aboutTitle.setVisibility(hasText ? View.VISIBLE : View.GONE);
        b.description.setVisibility(hasText ? View.VISIBLE : View.GONE);
        b.readMore.setVisibility(View.GONE);
        b.description.post(() -> {
            if (b.description.getLayout() != null && b.description.getLayout().getEllipsisCount(b.description.getLineCount() - 1) > 0) {
                b.readMore.setVisibility(View.VISIBLE);
            }
        });
        b.readMore.setOnClickListener(v -> {
            expanded = !expanded;
            b.description.setMaxLines(expanded ? Integer.MAX_VALUE : 6);
            b.readMore.setText(expanded ? R.string.shop_read_less : R.string.shop_read_more);
        });
    }

    private void bindRelated() {
        ShopCatalog c = ShopRepo.get(this).cached();
        List<Product> related = c == null ? new ArrayList<>() : ShopQuery.related(c.products, product, 8);
        boolean show = !related.isEmpty();
        b.relatedTitle.setVisibility(show ? View.VISIBLE : View.GONE);
        b.related.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        ShopAdapter a = new ShopAdapter(p -> ShopNav.openProduct(this, p.id, "related"), dp(160));
        b.related.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        b.related.setAdapter(a);
        a.submitList(related);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private static List<String> listOf(String s) {
        List<String> l = new ArrayList<>();
        if (s != null && !s.isEmpty()) l.add(s);
        return l;
    }
}
