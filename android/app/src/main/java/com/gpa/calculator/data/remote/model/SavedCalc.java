package com.gpa.calculator.data.remote.model;

/** Row of the saved_calculations table as exchanged with the backend. */
public class SavedCalc {
    public long id;
    public long local_id;
    public String calc_id;
    public String title;
    public String inputs;
    public String results;
    public String summary;
    public long created_at;
}
