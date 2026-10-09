package com.app.gestiondepenses;

import com.app.managers.ExpenseManager;
import com.app.models.Expense;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ExpenseUnitTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testExpenseCategoryAndCsvLine() {
        Expense expense = new Expense(null, "24/02/2025", "14:30", "Intermarché", "25.50", "Nourriture / Courses");
        assertEquals("Intermarché", expense.getName());
        assertEquals("25.50", expense.getAmount());
        assertEquals("Nourriture / Courses", expense.getCategory());
        assertEquals("24/02/2025;14:30;Intermarché;25.50;Nourriture / Courses", expense.toCsvLine());
        assertEquals("Intermarché (Nourriture / Courses) | 25.50 €", expense.getDisplayText());
        assertEquals(25.50, expense.getNumericAmount(), 0.001);
    }

    @Test
    public void testExpenseManagerCalculationsAndCsvExport() throws IOException {
        File folder = temporaryFolder.newFolder("expenses");
        ExpenseManager manager = new ExpenseManager(folder);

        manager.createExpense("Intermarché", "12.30", "Nourriture / Courses");
        manager.createExpense("Péage", "50.00", "Déplacement");

        List<Expense> expenses = manager.getLocalExpenses();
        assertEquals(2, expenses.size());

        double total = ExpenseManager.calculateTotalAmount(expenses);
        assertEquals(62.30, total, 0.001);

        File csvFile = manager.generateConsolidatedCsvFile(expenses);
        assertNotNull(csvFile);
        assertTrue(csvFile.exists());

        List<String> lines = Files.readAllLines(csvFile.toPath());
        assertEquals(3, lines.size());
        assertEquals("\uFEFFDate;Heure;Nom;Montant;Categorie", lines.get(0));
    }
}
