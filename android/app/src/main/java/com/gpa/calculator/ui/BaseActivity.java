package com.gpa.calculator.ui;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Edge-to-edge + common inset helpers. */
public abstract class BaseActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
    }

    /** Adds the navigation-bar inset as bottom padding (for bottom-anchored ad containers etc.). */
    protected void padBottomInset(View v) {
        final int base = v.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(v, (view, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), base + sb.bottom);
            return insets;
        });
    }

    protected void padTopInset(View v) {
        final int base = v.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(v, (view, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(view.getPaddingLeft(), base + sb.top, view.getPaddingRight(), view.getPaddingBottom());
            return insets;
        });
    }
}
