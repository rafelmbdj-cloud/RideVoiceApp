package com.seuapp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OfferParser {
    private static final Pattern PRICE_PER_KM = Pattern.compile(
            "(?i)(?:r\\$\\s*)?(\\d+(?:[.,]\\d{1,2})?)\\s*(?:/\\s*km|por\\s*km)"
    );

    public static String placeForSpeech(String value) {
        return value == null ? "" : value;
    }

    public static RecognizedOffer parse(String text) {
        if (text == null || text.isBlank()) return null;

        Matcher matcher = PRICE_PER_KM.matcher(text);
        if (!matcher.find()) return null;

        try {
            RecognizedOffer offer = new RecognizedOffer();
            offer.pricePerKm = Double.parseDouble(matcher.group(1).replace(',', '.'));
            offer.pickupAddress = text.trim();
            return offer;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
