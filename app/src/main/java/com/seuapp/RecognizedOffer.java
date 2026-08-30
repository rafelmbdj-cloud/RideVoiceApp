package com.seuapp;

public class RecognizedOffer {
    public String pickupAddress = "";
    public double pricePerKm = 0.0;
    public double pickupKm = 0.0;

    public String calculatedClassification() {
        return calculatedClassification(3.00, 2.00);
    }

    public String calculatedClassification(double excellentMinimum, double goodMinimum) {
        if (pricePerKm >= excellentMinimum) {
            return "EXCELENTE";
        }
        if (pricePerKm >= goodMinimum) {
            return "BOA";
        }
        return "RUIM";
    }
}
