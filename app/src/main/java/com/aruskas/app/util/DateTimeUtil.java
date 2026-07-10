package com.aruskas.app.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateTimeUtil {

    private static final String ISO_DATE_FORMAT = "yyyy-MM-dd";
    private static final String ISO_MONTH_FORMAT = "yyyy-MM";
    private static final String DISPLAY_DATE_FORMAT = "dd MMMM yyyy";
    private static final String DISPLAY_MONTH_FORMAT = "MMMM yyyy";
    private static final Locale LOCALE_ID = new Locale("id", "ID");

    public static String getCurrentDateIso() {
        SimpleDateFormat sdf = new SimpleDateFormat(ISO_DATE_FORMAT, Locale.US);
        return sdf.format(new Date());
    }

    public static String getCurrentMonthIso() {
        SimpleDateFormat sdf = new SimpleDateFormat(ISO_MONTH_FORMAT, Locale.US);
        return sdf.format(new Date());
    }

    public static String formatIsoToDisplayDate(String isoDateString) {
        if (isoDateString == null || isoDateString.trim().isEmpty()) return "";
        try {
            SimpleDateFormat isoFormat = new SimpleDateFormat(ISO_DATE_FORMAT, Locale.US);
            Date date = isoFormat.parse(isoDateString);
            SimpleDateFormat displayFormat = new SimpleDateFormat(DISPLAY_DATE_FORMAT, LOCALE_ID);
            return displayFormat.format(date);
        } catch (ParseException e) {
            return isoDateString; // fallback to original if parsing fails
        }
    }

    public static String formatIsoToDisplayMonth(String isoMonthString) {
        if (isoMonthString == null || isoMonthString.trim().isEmpty()) return "";
        try {
            SimpleDateFormat isoFormat = new SimpleDateFormat(ISO_MONTH_FORMAT, Locale.US);
            Date date = isoFormat.parse(isoMonthString);
            SimpleDateFormat displayFormat = new SimpleDateFormat(DISPLAY_MONTH_FORMAT, LOCALE_ID);
            return displayFormat.format(date);
        } catch (ParseException e) {
            return isoMonthString; // fallback to original if parsing fails
        }
    }

    public static String getNextMonthIso(String currentMonthIso) {
        if (currentMonthIso == null) return getCurrentMonthIso();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(ISO_MONTH_FORMAT, Locale.US);
            Date date = sdf.parse(currentMonthIso);
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.add(Calendar.MONTH, 1);
            return sdf.format(cal.getTime());
        } catch (ParseException e) {
            return currentMonthIso;
        }
    }

    public static String getPreviousMonthIso(String currentMonthIso) {
        if (currentMonthIso == null) return getCurrentMonthIso();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(ISO_MONTH_FORMAT, Locale.US);
            Date date = sdf.parse(currentMonthIso);
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.add(Calendar.MONTH, -1);
            return sdf.format(cal.getTime());
        } catch (ParseException e) {
            return currentMonthIso;
        }
    }
    
    public static Date parseIsoDate(String isoDateString) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(ISO_DATE_FORMAT, Locale.US);
            return sdf.parse(isoDateString);
        } catch (ParseException | NullPointerException e) {
            return new Date();
        }
    }

    public static Date parseIsoMonth(String isoMonthString) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(ISO_MONTH_FORMAT, Locale.US);
            return sdf.parse(isoMonthString);
        } catch (ParseException | NullPointerException e) {
            return new Date();
        }
    }
    
    public static String formatDisplayDateToIso(int year, int monthOfYear, int dayOfMonth) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, monthOfYear);
        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        SimpleDateFormat sdf = new SimpleDateFormat(ISO_DATE_FORMAT, Locale.US);
        return sdf.format(calendar.getTime());
    }
}
