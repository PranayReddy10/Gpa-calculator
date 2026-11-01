package com.gpa.calculator.ui.shop;

import android.content.Intent;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BulletSpan;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.GridLayoutManager;
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
import com.gpa.calculator.databinding.ItemShopRelatedBinding;
import com.gpa.calculator.databinding.ItemShopThumbBinding;
import com.gpa.calculator.databinding.ViewShopHighlightBinding;
import com.gpa.calculator.databinding.ViewShopPersonalClassicBinding;
import com.gpa.calculator.ui.BaseActivity;
import com.gpa.calculator.ui.common.FontSpan;
import com.gpa.calculator.ui.common.ImageLoader;
import com.gpa.calculator.ui.common.ShareUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One product, laid out like the shop's own website: photo card with thumbnails, serif title,
 * price card, chips, "Personalise it", trust badges, share, description and "You may also like".
 * The real photo upload and checkout happen on the shop's page (ShopCheckoutActivity).
 */
public class ShopProductActivity extends BaseActivity {
    public static final String EXTRA_ID = "product_id";
    public static final String EXTRA_SOURCE = "source";

    private static final int[] HIGHLIGHT_ICONS = {R.drawable.ic_verified_user, R.drawable.ic_replay, R.drawable.ic_pan_tool};

    private ActivityShopProductBinding b;
    private Product product;
    @Nullable private ShopCatalog catalog;
    private final Map<String, String> selected = new LinkedHashMap<>();
    @Nullable private Variant variant;
    private boolean descriptionOpen = true;
    private List<String> photos = new ArrayList<>();
    @Nullable private ThumbAdapter thumbAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityShopProductBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        padTopInset(b.page);
        padBottomInset(b.buyBar);

        b.back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        int id = getIntent().getIntExtra(EXTRA_ID, -1);
        catalog = ShopRepo.get(this).cached();
        product = ShopRepo.get(this).product(id);
        if (product != null) {
            bind(savedInstanceState == null);
        } else {
            // App was restarted on this screen: the catalogue is loaded from the phone's copy first.
            b.buyBar.setVisibility(View.INVISIBLE);
            ShopRepo.get(this).load(false, new ShopRepo.Callback() {
                @Override public void onCatalog(ShopCatalog c, boolean fromNetwork) {
                    if (product != null || isFinishing()) return;
                    catalog = c;
                    product = ShopRepo.get(ShopProductActivity.this).product(id);
                    if (product == null) { finish(); return; }
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
        String shopName = catalog != null && !catalog.name.isEmpty() ? catalog.name : "";
        b.shopName.setText(shopName);
        b.breadcrumb.setText(product.category.isEmpty()
                ? getString(R.string.shop_breadcrumb, product.name)
                : getString(R.string.shop_breadcrumb, product.category + " / " + product.name));
        b.share.setOnClickListener(v -> shareSheet());
        b.shareMore.setOnClickListener(v -> shareSheet());
        b.shareWhatsapp.setOnClickListener(v -> shareWhatsApp());
        b.shareLink.setOnClickListener(v -> ShareUtil.copy(b.getRoot(), cleanUrl(product.url)));
        View.OnClickListener buy = v -> ShopNav.openCheckout(this, buyUrl(), product.name, product.id);
        b.buy.setOnClickListener(buy);
        b.buyInline.setOnClickListener(buy);
        b.regular.setPaintFlags(b.regular.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);

        photos = product.images.isEmpty() ? listOf(product.image) : product.images;
        bindGallery();
        b.category.setText(product.category);
        b.category.setVisibility(product.category.isEmpty() ? View.GONE : View.VISIBLE);
        b.name.setText(product.name);
        b.rating.setText(product.rating.count > 0
                ? getString(R.string.shop_reviews, product.rating.average, product.rating.count)
                : getString(R.string.shop_be_first));
        String note = catalog != null ? catalog.price_note : "";
        b.priceNote.setText(note);
        b.priceNote.setVisibility(note == null || note.isEmpty() ? View.GONE : View.VISIBLE);
        b.shortText.setText(product.shortText);
        b.shortText.setVisibility(product.shortText.isEmpty() ? View.GONE : View.VISIBLE);
        bindFacts();
        bindOptions();
        bindPersonalisation();
        bindHighlights();
        bindDescription();
        bindRelated();
        updatePrice();
        setupStickyBar();
    }

    // ------------------------------------------------------------------ gallery + thumbnails
    private void bindGallery() {
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
        b.thumbs.setVisibility(photos.size() > 1 ? View.VISIBLE : View.GONE);
        if (photos.size() > 1) {
            thumbAdapter = new ThumbAdapter();
            b.thumbs.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            b.thumbs.setAdapter(thumbAdapter);
        }
        b.gallery.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                b.counter.setText(getString(R.string.shop_photo_counter, position + 1, photos.size()));
                if (thumbAdapter != null) thumbAdapter.select(position);
            }
        });
    }

    private class ThumbAdapter extends RecyclerView.Adapter<ThumbAdapter.H> {
        private int current;

        void select(int pos) {
            int old = current;
            current = pos;
            notifyItemChanged(old);
            notifyItemChanged(pos);
            b.thumbs.smoothScrollToPosition(pos);
        }

        @NonNull @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new H(ItemShopThumbBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override public void onBindViewHolder(@NonNull H h, int pos) {
            ImageLoader.get(ShopProductActivity.this).load(h.t.thumb, photos.get(pos), dp(64) * 2);
            h.t.selected.setVisibility(pos == current ? View.VISIBLE : View.INVISIBLE);
            h.t.getRoot().setOnClickListener(v -> b.gallery.setCurrentItem(h.getBindingAdapterPosition(), true));
        }

        @Override public int getItemCount() { return photos.size(); }

        class H extends RecyclerView.ViewHolder {
            final ItemShopThumbBinding t;
            H(ItemShopThumbBinding t) { super(t.getRoot()); this.t = t; }
        }
    }

    // ------------------------------------------------------------------ price + chips
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
        for (com.google.android.material.button.MaterialButton btn : new com.google.android.material.button.MaterialButton[]{b.buy, b.buyInline}) {
            btn.setEnabled(inStock);
            btn.setText(inStock ? R.string.shop_buy : R.string.shop_out_of_stock);
        }
        b.barHint.setText(product.production_days > 0
                ? getResources().getQuantityString(R.plurals.shop_ready_in, product.production_days, product.production_days)
                : product.category);
    }

    private void bindFacts() {
        ChipGroup g = b.facts;
        g.removeAllViews();
        boolean photosNeeded = false;
        for (Personalisation x : product.personalisation) if (isPhotos(x)) photosNeeded = true;
        if (photosNeeded) g.addView(fact(getString(R.string.shop_add_your_photo), R.drawable.ic_photo_camera));
        if (product.production_days > 0) {
            g.addView(fact(getResources().getQuantityString(R.plurals.shop_ready_in, product.production_days, product.production_days), R.drawable.ic_pan_tool));
        }
        if (product.featured) g.addView(fact(getString(R.string.shop_featured), R.drawable.ic_star));
        if (!product.in_stock) g.addView(fact(getString(R.string.shop_out_of_stock), R.drawable.ic_block));
        g.setVisibility(g.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private Chip fact(String text, int icon) {
        Chip c = (Chip) getLayoutInflater().inflate(R.layout.view_shop_fact_chip, b.facts, false);
        c.setText(text);
        c.setChipIconResource(icon);
        c.setChipIconVisible(true);
        return c;
    }

    // ------------------------------------------------------------------ options (pack size, shape, …)
    private void bindOptions() {
        b.options.removeAllViews();
        if (product.attributes.isEmpty()) { b.optionsSection.setVisibility(View.GONE); return; }
        b.optionsSection.setVisibility(View.VISIBLE);
        for (Map.Entry<String, List<String>> e : product.attributes.entrySet()) {
            TextView label = new TextView(this);
            label.setText(e.getKey());
            label.setTextAppearance(R.style.Text_Shop_Strong);
            label.setPadding(0, dp(12), 0, dp(4));
            b.options.addView(label);
            ChipGroup group = new ChipGroup(this);
            group.setSingleSelection(true);
            group.setChipSpacingHorizontal(dp(8));
            for (String value : e.getValue()) {
                Chip c = (Chip) getLayoutInflater().inflate(R.layout.view_shop_choice_chip, group, false);
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
            int idx = photos.indexOf(variant.image);
            if (idx >= 0) b.gallery.setCurrentItem(idx, true);
        }
        updatePrice();
    }

    /** The variant's own page when the shop listed one for the chosen options, else the product page. */
    private String buyUrl() {
        return variant != null && !variant.url.isEmpty() ? variant.url : product.url;
    }

    // ------------------------------------------------------------------ personalise it
    private static boolean isPhotos(Personalisation x) {
        return x.type.equals("images") || x.type.equals("image") || x.type.equals("file");
    }

    private void bindPersonalisation() {
        b.personalRows.removeAllViews();
        if (product.personalisation.isEmpty()) { b.personalCard.setVisibility(View.GONE); return; }
        b.personalCard.setVisibility(View.VISIBLE);
        View.OnClickListener toCheckout = v -> ShopNav.openCheckout(this, buyUrl(), product.name, product.id);
        for (Personalisation x : product.personalisation) {
            ViewShopPersonalClassicBinding r = ViewShopPersonalClassicBinding.inflate(getLayoutInflater(), b.personalRows, true);
            String label = x.label.isEmpty() ? getString(R.string.shop_personalise) : x.label;
            SpannableStringBuilder sb = new SpannableStringBuilder(label);
            if (x.required) {
                sb.append(" *");
                sb.setSpan(new android.text.style.ForegroundColorSpan(ContextCompat.getColor(this, R.color.shop_pink)),
                        sb.length() - 1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            r.label.setText(sb);
            r.help.setText(x.help);
            r.help.setVisibility(x.help.isEmpty() ? View.GONE : View.VISIBLE);
            if (isPhotos(x)) {
                r.addPhoto.setVisibility(View.VISIBLE);
                r.addPhoto.setOnClickListener(toCheckout);
                if (x.max_files > 0) {
                    r.counter.setVisibility(View.VISIBLE);
                    SpannableStringBuilder c = new SpannableStringBuilder(getString(R.string.shop_files_counter, x.max_files));
                    c.setSpan(new android.text.style.ForegroundColorSpan(ContextCompat.getColor(this, R.color.shop_pink)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    r.counter.setText(c);
                }
            } else if (x.type.equals("text") || x.type.equals("textarea")) {
                r.textBox.setVisibility(View.VISIBLE);
                r.textBox.setOnClickListener(toCheckout);
                if (x.max_length > 0) {
                    r.counter.setVisibility(View.VISIBLE);
                    r.counter.setText(getString(R.string.shop_files_counter, x.max_length));
                }
            } else if (!x.options.isEmpty()) {
                r.choices.setVisibility(View.VISIBLE);
                r.choices.setSingleSelection(true);
                for (String o : x.options) {
                    Chip c = (Chip) getLayoutInflater().inflate(R.layout.view_shop_choice_chip, r.choices, false);
                    c.setId(View.generateViewId());
                    c.setText(o);
                    r.choices.addView(c);
                }
            }
        }
    }

    // ------------------------------------------------------------------ trust badges
    private void bindHighlights() {
        b.highlights.removeAllViews();
        List<ShopCatalog.Highlight> hs = catalog != null ? catalog.highlights : null;
        if (hs == null || hs.isEmpty()) { b.highlights.setVisibility(View.GONE); return; }
        b.highlights.setVisibility(View.VISIBLE);
        for (int i = 0; i < hs.size() && i < 3; i++) {
            ViewShopHighlightBinding h = ViewShopHighlightBinding.inflate(getLayoutInflater(), b.highlights, false);
            h.icon.setImageResource(HIGHLIGHT_ICONS[i]);
            h.title.setText(hs.get(i).title);
            h.text.setText(hs.get(i).text);
            h.text.setVisibility(hs.get(i).text.isEmpty() ? View.GONE : View.VISIBLE);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) h.getRoot().getLayoutParams();
            if (i > 0) lp.setMarginStart(dp(8));
            b.highlights.addView(h.getRoot(), lp);
        }
    }

    // ------------------------------------------------------------------ share
    private String shareText() {
        return getString(R.string.shop_share_text, product.name, ShopQuery.priceLabel(product), cleanUrl(product.url));
    }

    private void shareSheet() {
        Analytics.get(this).log("shop_share", "product", String.valueOf(product.id));
        ShareUtil.shareText(this, shareText());
    }

    private void shareWhatsApp() {
        Analytics.get(this).log("shop_share", "product", String.valueOf(product.id), "to", "whatsapp");
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText());
        for (String pkg : new String[]{"com.whatsapp", "com.whatsapp.w4b"}) {
            try {
                startActivity(new Intent(i).setPackage(pkg));
                return;
            } catch (Exception ignored) {
                // not installed: try the next one, then the share sheet
            }
        }
        ShareUtil.shareText(this, shareText());
    }

    /** Shared links point at the plain product page (the app's tracking tags are for in-app taps). */
    private static String cleanUrl(String url) {
        int q = url.indexOf("utm_");
        if (q <= 0) return url;
        String base = url.substring(0, q);
        return base.endsWith("?") || base.endsWith("&") ? base.substring(0, base.length() - 1) : base;
    }

    // ------------------------------------------------------------------ description
    private void bindDescription() {
        CharSequence text = !product.description_html.isEmpty() ? styledHtml(product.description_html)
                : !product.description.isEmpty() ? product.description : product.shortText;
        b.descriptionCard.setVisibility(text.length() == 0 ? View.GONE : View.VISIBLE);
        b.description.setText(text);
        b.descriptionHeader.setOnClickListener(v -> {
            descriptionOpen = !descriptionOpen;
            b.description.setVisibility(descriptionOpen ? View.VISIBLE : View.GONE);
            b.descriptionToggle.setImageResource(descriptionOpen ? R.drawable.ic_remove : R.drawable.ic_add);
            b.descriptionToggle.setContentDescription(getString(descriptionOpen ? R.string.shop_read_less : R.string.shop_read_more));
        });
    }

    /** The shop's description HTML with its headings in the serif title font and pink list bullets. */
    private CharSequence styledHtml(String html) {
        Spanned src = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT);
        SpannableStringBuilder sb = new SpannableStringBuilder(src);
        Typeface serif = ResourcesCompat.getFont(this, R.font.fraunces_bold);
        int pink = ContextCompat.getColor(this, R.color.shop_pink);
        int ink = ContextCompat.getColor(this, R.color.shop_ink);
        for (RelativeSizeSpan rs : sb.getSpans(0, sb.length(), RelativeSizeSpan.class)) {
            int s = sb.getSpanStart(rs), e = sb.getSpanEnd(rs);
            if (serif != null) sb.setSpan(new FontSpan(serif), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new android.text.style.ForegroundColorSpan(ink), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new RelativeSizeSpan(Math.min(rs.getSizeChange(), 1.3f)), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.removeSpan(rs);
        }
        for (BulletSpan bs : sb.getSpans(0, sb.length(), BulletSpan.class)) {
            int s = sb.getSpanStart(bs), e = sb.getSpanEnd(bs), f = sb.getSpanFlags(bs);
            sb.removeSpan(bs);
            BulletSpan pinkBullet = Build.VERSION.SDK_INT >= 28 ? new BulletSpan(dp(10), pink, dp(3)) : new BulletSpan(dp(10), pink);
            sb.setSpan(pinkBullet, s, e, f);
        }
        while (sb.length() > 0 && Character.isWhitespace(sb.charAt(sb.length() - 1))) sb.delete(sb.length() - 1, sb.length());
        return sb;
    }

    // ------------------------------------------------------------------ you may also like
    private void bindRelated() {
        List<Product> related = catalog == null ? new ArrayList<>() : ShopQuery.related(catalog.products, product, 4);
        boolean show = !related.isEmpty();
        b.relatedHeader.setVisibility(show ? View.VISIBLE : View.GONE);
        b.related.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        String top = product.category_slugs.isEmpty() ? null : product.category_slugs.get(product.category_slugs.size() - 1);
        String topName = null;
        if (catalog != null && top != null) for (ShopCatalog.Category c : catalog.categories) if (c.slug.equals(top)) topName = c.name;
        if (topName != null) {
            b.relatedMore.setVisibility(View.VISIBLE);
            b.relatedMore.setText(getString(R.string.shop_more_in, topName));
            final String slug = top;
            b.relatedMore.setOnClickListener(v -> ShopNav.openShop(this, "related_more", slug));
        } else {
            b.relatedMore.setVisibility(View.GONE);
        }
        b.related.setLayoutManager(new GridLayoutManager(this, 2));
        b.related.setAdapter(new RelatedAdapter(related));
    }

    private class RelatedAdapter extends RecyclerView.Adapter<RelatedAdapter.H> {
        private final List<Product> items;

        RelatedAdapter(List<Product> items) { this.items = items; }

        @NonNull @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new H(ItemShopRelatedBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override public void onBindViewHolder(@NonNull H h, int pos) {
            Product p = items.get(pos);
            h.r.name.setText(p.name);
            h.r.price.setText(!p.attributes.isEmpty() && p.max_price <= p.price + 0.005
                    ? getString(R.string.shop_from, ShopQuery.money(p.price, p.currency)) : ShopQuery.priceLabel(p));
            int n = p.images.size();
            h.r.photos.setVisibility(n > 1 ? View.VISIBLE : View.GONE);
            h.r.photos.setText(getString(R.string.shop_photos_count, n));
            ImageLoader.get(ShopProductActivity.this).load(h.r.image, p.thumbnail.isEmpty() ? p.image : p.thumbnail,
                    getResources().getDisplayMetrics().widthPixels / 2);
            h.r.image.setContentDescription(p.name);
            h.r.card.setOnClickListener(v -> ShopNav.openProduct(ShopProductActivity.this, p.id, "related"));
        }

        @Override public int getItemCount() { return items.size(); }

        class H extends RecyclerView.ViewHolder {
            final ItemShopRelatedBinding r;
            H(ItemShopRelatedBinding r) { super(r.getRoot()); this.r = r; }
        }
    }

    // ------------------------------------------------------------------ sticky bar
    /** The bottom bar appears once the in-page "Personalise & buy" button has scrolled out of view. */
    private void setupStickyBar() {
        b.buyBar.setVisibility(View.VISIBLE);
        b.buyBar.setTranslationY(dp(120));
        b.scroll.setOnScrollChangeListener((androidx.core.widget.NestedScrollView.OnScrollChangeListener) (v, x, y, ox, oy) -> updateStickyBar());
        b.scroll.post(this::updateStickyBar);
    }

    private boolean barShown;

    private void updateStickyBar() {
        Rect r = new Rect();
        boolean inlineVisible = b.buyInline.getLocalVisibleRect(r) && r.height() >= b.buyInline.getHeight() / 2;
        boolean show = !inlineVisible;
        if (show == barShown) return;
        barShown = show;
        b.buyBar.animate().translationY(show ? 0 : b.buyBar.getHeight() + dp(24)).setDuration(220).start();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private static List<String> listOf(String s) {
        List<String> l = new ArrayList<>();
        if (s != null && !s.isEmpty()) l.add(s);
        return l;
    }
}
