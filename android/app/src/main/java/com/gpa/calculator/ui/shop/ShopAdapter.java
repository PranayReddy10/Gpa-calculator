package com.gpa.calculator.ui.shop;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.gpa.calculator.R;
import com.gpa.calculator.data.remote.model.ShopCatalog.Product;
import com.gpa.calculator.data.shop.ShopQuery;
import com.gpa.calculator.databinding.ItemShopProductBinding;
import com.gpa.calculator.ui.common.ImageLoader;

/** Product cards for the shop grid and the "You may also like" row. */
public class ShopAdapter extends ListAdapter<Product, ShopAdapter.Holder> {
    public interface OnClick { void onProduct(Product p); }

    private final OnClick onClick;
    /** Fixed card width in px for horizontal rows; 0 = fill the grid cell. */
    private final int fixedWidthPx;

    public ShopAdapter(OnClick onClick, int fixedWidthPx) {
        super(DIFF);
        this.onClick = onClick;
        this.fixedWidthPx = fixedWidthPx;
        setHasStableIds(true);
    }

    @Override public long getItemId(int position) { return getItem(position).id; }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemShopProductBinding b = ItemShopProductBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        if (fixedWidthPx > 0) b.getRoot().getLayoutParams().width = fixedWidthPx;
        b.regular.setPaintFlags(b.regular.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        return new Holder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Product p = getItem(position);
        ItemShopProductBinding b = h.b;
        b.name.setText(p.name);
        bindPrice(p, b);
        bindBadge(p, b);
        int days = p.production_days;
        boolean personal = !p.personalisation.isEmpty();
        if (!p.in_stock) {
            b.meta.setText(R.string.shop_out_of_stock);
            b.meta.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        } else if (personal) {
            b.meta.setText(R.string.shop_personalise);
            b.meta.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_add_photo, 0, 0, 0);
        } else if (days > 0) {
            b.meta.setText(b.getRoot().getContext().getResources().getQuantityString(R.plurals.shop_ready_in, days, days));
            b.meta.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_timer, 0, 0, 0);
        } else {
            b.meta.setText("");
            b.meta.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }
        int target = fixedWidthPx > 0 ? fixedWidthPx : b.getRoot().getResources().getDisplayMetrics().widthPixels / 2;
        ImageLoader.get(b.getRoot().getContext()).load(b.image, p.thumbnail.isEmpty() ? p.image : p.thumbnail, target);
        b.image.setContentDescription(p.name);
        b.card.setOnClickListener(v -> onClick.onProduct(p));
    }

    static void bindPrice(Product p, ItemShopProductBinding b) {
        if (!p.attributes.isEmpty() && p.max_price <= p.price + 0.005) {
            b.price.setText(b.getRoot().getContext().getString(R.string.shop_from, ShopQuery.money(p.price, p.currency)));
        } else {
            b.price.setText(ShopQuery.priceLabel(p));
        }
        boolean discounted = p.regular_price > p.price + 0.005;
        b.regular.setVisibility(discounted ? View.VISIBLE : View.GONE);
        if (discounted) b.regular.setText(ShopQuery.money(p.regular_price, p.currency));
    }

    private static void bindBadge(Product p, ItemShopProductBinding b) {
        if (p.on_sale && p.discount_percent > 0) {
            b.badge.setText(b.getRoot().getContext().getString(R.string.shop_off_percent, p.discount_percent));
            b.badge.setVisibility(View.VISIBLE);
        } else if (p.featured) {
            b.badge.setText(R.string.shop_featured);
            b.badge.setVisibility(View.VISIBLE);
        } else {
            b.badge.setVisibility(View.GONE);
        }
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemShopProductBinding b;
        Holder(ItemShopProductBinding b) { super(b.getRoot()); this.b = b; }
    }

    private static final DiffUtil.ItemCallback<Product> DIFF = new DiffUtil.ItemCallback<Product>() {
        @Override public boolean areItemsTheSame(@NonNull Product a, @NonNull Product b) { return a.id == b.id; }
        @Override public boolean areContentsTheSame(@NonNull Product a, @NonNull Product b) {
            return a.name.equals(b.name) && a.price == b.price && a.max_price == b.max_price
                    && a.regular_price == b.regular_price && a.image.equals(b.image) && a.in_stock == b.in_stock
                    && a.featured == b.featured && a.on_sale == b.on_sale;
        }
    };
}
