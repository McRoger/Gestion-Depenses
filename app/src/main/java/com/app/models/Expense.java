package com.app.models;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Modèle représentant une dépense complète avec date, heure, nom, montant et catégorie.
 */
public class Expense {

    private final File file;
    private final String date;
    private final String time;
    private final String name;
    private final String amount;
    private final String category;
    private final double numericAmount;
    private final String displayText;

    public Expense(File file, String date, String time, String name, String amount, String category) {
        this.file = file;
        this.date = date != null && !date.isEmpty() ? date : getCurrentDate();
        this.time = time != null && !time.isEmpty() ? time : getCurrentTime();
        this.name = name != null ? name.trim() : "";
        this.amount = amount != null ? amount.trim().replace(',', '.') : "0";
        this.category = category != null && !category.isEmpty() ? category : "Autre";

        double parsed = 0.0;
        try {
            parsed = Double.parseDouble(this.amount);
        } catch (NumberFormatException ignored) {
        }
        this.numericAmount = parsed;
        this.displayText = formatDisplayText(this.date, this.name, this.amount, this.category);
    }

    public File getFile() {
        return file;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }

    public String getName() {
        return name;
    }

    public String getAmount() {
        return amount;
    }

    public String getCategory() {
        return category;
    }

    public double getNumericAmount() {
        return numericAmount;
    }

    public String getDisplayText() {
        return displayText;
    }

    /**
     * Génère la ligne CSV au format francisé : Date;Heure;Nom;Montant;Catégorie
     */
    public String toCsvLine() {
        return date + ";" + time + ";" + name + ";" + amount + ";" + category;
    }

    /**
     * Génère la ligne stockée dans le fichier texte individuel.
     */
    public String toFileContent() {
        return date + "|" + time + "|" + name + "|" + amount + "|" + category;
    }

    /**
     * Parse un fichier (.txt ou .csv) et retourne TOUTES les dépenses qu'il contient.
     */
    public static List<Expense> parseAllExpensesFromFile(File file) {
        List<Expense> expenses = new ArrayList<>();
        if (file == null || !file.exists() || !file.isFile() || file.length() == 0) {
            return expenses;
        }

        try (FileReader fileReader = new FileReader(file);
             BufferedReader bufferedReader = new BufferedReader(fileReader)) {

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1).trim();
                }

                // Ignorer l'en-tête CSV
                if (line.toLowerCase().contains("date;") && line.toLowerCase().contains("heure;")) {
                    continue;
                }

                if (line.contains(";")) {
                    String[] parts = line.split(";");
                    if (parts.length >= 5) {
                        expenses.add(new Expense(file, parts[0].trim(), parts[1].trim(), parts[2].trim(), parts[3].trim(), parts[4].trim()));
                    } else if (parts.length >= 2) {
                        expenses.add(new Expense(file, getCurrentDate(), getCurrentTime(), parts[0].trim(), parts[1].trim(), "Autre"));
                    }
                } else if (line.contains("|")) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 5) {
                        expenses.add(new Expense(file, parts[0].trim(), parts[1].trim(), parts[2].trim(), parts[3].trim(), parts[4].trim()));
                    } else if (parts.length == 2) {
                        expenses.add(new Expense(file, getCurrentDate(), getCurrentTime(), parts[0].trim(), parts[1].trim(), "Autre"));
                    }
                } else {
                    expenses.add(new Expense(file, getCurrentDate(), getCurrentTime(), line, "0", "Autre"));
                }
            }
        } catch (IOException e) {
            return expenses;
        }
        return expenses;
    }

    /**
     * Parse la première dépense d'un fichier.
     */
    public static Expense fromFile(File file) {
        List<Expense> all = parseAllExpensesFromFile(file);
        return all.isEmpty() ? null : all.get(0);
    }

    private static String formatDisplayText(String date, String name, String amount, String category) {
        return name + " (" + category + ") | " + amount + " €";
    }

    private static String getCurrentDate() {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(new Date());
    }

    private static String getCurrentTime() {
        return new SimpleDateFormat("HH:mm", Locale.FRANCE).format(new Date());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Expense expense = (Expense) o;
        return Objects.equals(file, expense.file);
    }

    @Override
    public int hashCode() {
        return Objects.hash(file);
    }

    @Override
    public String toString() {
        return displayText;
    }
}
