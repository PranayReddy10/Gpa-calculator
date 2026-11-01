package com.gpa.calculator.ui.shop;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.gpa.calculator.R;
import com.gpa.calculator.data.remote.Analytics;
import com.gpa.calculator.data.remote.ConfigStore;

/** Opens shop screens; product pages open in the app or in the phone's browser (admin setting). */
public final class ShopNav {
    private ShopNav() {}

    public static void openShop(Context c, String source) { openShop(c, source, null); }

    /** Opens the Shop tab, optionally on one top-level category (e.g. "More Gift Combo"). */
    public static void openShop(Context c, String source, @androidx.annotation.Nullable String categorySlug) {
        Analytics.get(c).log("shop_open", "source", source);
        Intent i = new Intent(c, com.gpa.calculator.ui.MainActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(com.gpa.calculator.ui.MainActivity.EXTRA_TAB, "shop")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (categorySlug != null) i.putExtra(com.gpa.calculator.ui.MainActivity.EXTRA_SHOP_CATEGORY, categorySlug);
        c.startActivity(i);
    }

    public static void openProduct(Context c, int productId, String source) {
        c.startActivity(new Intent(c, ShopProductActivity.class)
                .putExtra(ShopProductActivity.EXTRA_ID, productId)
                .putExtra(ShopProductActivity.EXTRA_SOURCE, source));
    }

    /** "Personalise & buy": the shop's own product page, where photos are uploaded and the order is placed. */
    public static void openCheckout(Context c, String url, String title, int productId) {
        if (url == null || url.isEmpty()) return;
        Analytics.get(c).log("shop_buy", "product", String.valueOf(productId));
        if (ConfigStore.get(c).config().shop.checkout_in_app) {
            c.startActivity(new Intent(c, ShopCheckoutActivity.class)
                    .putExtra(ShopCheckoutActivity.EXTRA_URL, url)
                    .putExtra(ShopCheckoutActivity.EXTRA_TITLE, title));
        } else {
            openExternal(c, Uri.parse(url));
        }
    }

    public static void openExternal(Context c, Uri uri) {
        try {
            c.startActivity(new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (Exception e) {
            Toast.makeText(c, R.string.shop_no_app, Toast.LENGTH_SHORT).show();
        }
    }
}
