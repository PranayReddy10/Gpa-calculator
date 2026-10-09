package com.gpa.calculator.core;

/** Convenience base class carrying the static metadata so subclasses only declare inputs + compute. */
public abstract class SimpleCalc extends Calculator {
    private final String id, name, description, icon, keywords;
    private final Category category;

    protected SimpleCalc(String id, String name, String description, Category category, String icon, String keywords) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.icon = icon;
        this.keywords = keywords;
    }

    @Override public String id() { return id; }
    @Override public String name() { return name; }
    @Override public String description() { return description; }
    @Override public Category category() { return category; }
    @Override public String icon() { return icon; }
    @Override public String keywords() { return keywords; }
}
