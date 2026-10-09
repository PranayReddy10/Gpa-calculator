package com.gpa.calculator.core;

/** Top-level grouping shown as chips on the home screen. */
public enum Category {
    PERCENTAGE("Percentage", "ic_percent"),
    ACADEMIC("Academic", "ic_school"),
    FINANCE("Finance", "ic_money"),
    EVERYDAY("Everyday", "ic_calendar"),
    CONVERTERS("Converters", "ic_swap");

    public final String title;
    public final String iconName;

    Category(String title, String iconName) {
        this.title = title;
        this.iconName = iconName;
    }
}
