package com.app.managers;

import com.app.models.Expense;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Gestionnaire des fichiers de dépenses et de la génération des exports CSV.
 */
public class ExpenseManager {

    private final File storageDir;

    public ExpenseManager(File storageDir) {
        this.storageDir = storageDir;
    }

    /**
     * Crée un nouveau fichier texte individuel de dépense avec date, heure, nom, montant et catégorie.
     */
    public Expense createExpense(String name, String amount, String category) throws IOException {
        if (storageDir == null || name == null || amount == null || name.trim().isEmpty()) {
            return null;
        }

        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        String date = new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(new Date());
        String time = new SimpleDateFormat("HH:mm", Locale.FRANCE).format(new Date());

        String fileName = new Timestamp(System.currentTimeMillis()).toString().replace(":", " ") + "_" + Math.abs(name.hashCode()) + ".txt";
        Path pathFile = Paths.get(storageDir.getAbsolutePath(), fileName);

        Expense expense = new Expense(pathFile.toFile(), date, time, name, amount, category);
        Files.write(pathFile, Collections.singletonList(expense.toFileContent()), StandardCharsets.UTF_8);

        return expense;
    }

    /**
     * Lit et retourne la liste de toutes les dépenses locales individuelles.
     * Si un fichier CSV local existe, il est automatiquement scindé en fichiers texte individuels.
     */
    public List<Expense> getLocalExpenses() {
        List<Expense> expenses = new ArrayList<>();
        if (storageDir == null || !storageDir.exists()) {
            return expenses;
        }

        File[] files = storageDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".csv")) {
                    // Convertit les fichiers CSV locaux en dépenses individuelles autonomes
                    List<Expense> csvExpenses = Expense.parseAllExpensesFromFile(file);
                    for (Expense e : csvExpenses) {
                        try {
                            Expense ind = createExpense(e.getName(), e.getAmount(), e.getCategory());
                            if (ind != null) {
                                expenses.add(ind);
                            }
                        } catch (IOException ignored) {
                        }
                    }
                    deleteFileQuietly(file);
                } else if (file.isFile() && file.getName().endsWith(".txt")) {
                    List<Expense> parsed = Expense.parseAllExpensesFromFile(file);
                    if (!parsed.isEmpty()) {
                        expenses.addAll(parsed);
                    } else {
                        deleteFileQuietly(file);
                    }
                }
            }
        }
        return expenses;
    }

    /**
     * Calcule le total numérique cumulé d'une liste de dépenses.
     */
    public static double calculateTotalAmount(List<Expense> expenses) {
        if (expenses == null) return 0.0;
        double sum = 0.0;
        for (Expense expense : expenses) {
            if (expense != null) {
                sum += expense.getNumericAmount();
            }
        }
        return sum;
    }

    /**
     * Génère un fichier CSV consolidé pour l'import direct dans Excel,
     * contenant UNIQUEMENT les dépenses transmises en paramètre.
     */
    public File generateConsolidatedCsvFile(List<Expense> expenses) throws IOException {
        if (storageDir == null) return null;
        if (!storageDir.exists()) storageDir.mkdirs();

        String fileName = "export_depenses_" + new SimpleDateFormat("yyyy_MM_dd_HHmmss", Locale.FRANCE).format(new Date()) + ".csv";
        Path pathFile = Paths.get(storageDir.getAbsolutePath(), fileName);

        List<String> lines = new ArrayList<>();
        // En-tête CSV avec BOM UTF-8 (\uFEFF) pour Excel Windows
        lines.add("\uFEFFDate;Heure;Nom;Montant;Categorie");

        if (expenses != null) {
            for (Expense expense : expenses) {
                if (expense != null) {
                    lines.add(expense.toCsvLine());
                }
            }
        }

        Files.write(pathFile, lines, StandardCharsets.UTF_8);
        return pathFile.toFile();
    }

    public static void deleteFileQuietly(File file) {
        if (file == null || !file.exists()) return;
        try {
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteFileQuietly(child);
                    }
                }
            }
            Files.deleteIfExists(file.toPath());
        } catch (IOException ignored) {
        }
    }
}
