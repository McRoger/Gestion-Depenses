package com.app.gestiondepenses;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.app.dropbox.DropboxClientFactory;
import com.app.dropbox.LoginActivity;
import com.app.interfaceGestion.Callback;
import com.app.managers.ExpenseManager;
import com.app.models.Expense;
import com.app.tasks.DownloadFileTask;
import com.app.tasks.GetCurrentAccountTask;
import com.app.tasks.ListDriveTask;
import com.app.tasks.UploadFileTask;
import com.app.utils.AppExecutors;
import com.dropbox.core.v2.users.FullAccount;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Activité principale gérant la saisie, l'affichage local et la synchronisation Dropbox des dépenses.
 */
public class MainActivity extends LoginActivity {

    private static final String TAG = "MainActivity";
    private static final String PREFS_CAT_HISTORY = "category_history";
    private static final int SPEECH_REQUEST_CODE = 100;

    private static File storagePath;
    private ExpenseManager expenseManager;

    private ListView listFilePhone;
    private ListView listFileDrive;

    private Button creerDepense;
    private Button supprimerDepensesPhone;
    private ImageButton exportDepenses;
    private ImageButton importDepenses;
    private Button supprimerDepensesDrive;
    private ImageButton refreshListPhone;
    private ImageButton refreshListDrive;
    private ImageButton voiceButton;
    private ImageButton btnSettings;

    private EditText nomDepense;
    private EditText cout;
    private Spinner spinnerCategory;
    private TextView totalLocal;
    private TextView totalDrive;
    private ProgressBar progressBar;

    private final List<Expense> localExpenseList = new ArrayList<>();
    private final List<Expense> driveExpenseList = new ArrayList<>();

    private boolean accesInternet = true;
    private boolean isCliquable = true;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        File[] externalFilesDirs = ContextCompat.getExternalFilesDirs(getApplicationContext(), null);
        storagePath = (externalFilesDirs != null && externalFilesDirs.length > 0) ? externalFilesDirs[0] : getFilesDir();
        expenseManager = new ExpenseManager(storagePath);

        setContentView(R.layout.activity_main);

        nomDepense = findViewById(R.id.nomDepense);
        cout = findViewById(R.id.cout);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        totalLocal = findViewById(R.id.totalLocal);
        totalDrive = findViewById(R.id.totalDrive);

        listFilePhone = findViewById(R.id.listViewPhone);
        listFileDrive = findViewById(R.id.listViewDrive);

        creerDepense = findViewById(R.id.creerDepense);
        supprimerDepensesPhone = findViewById(R.id.supprimerDepenses);
        exportDepenses = findViewById(R.id.exportDepense);
        importDepenses = findViewById(R.id.importDepense);
        supprimerDepensesDrive = findViewById(R.id.supprimerDepensesDrive);
        progressBar = findViewById(R.id.progressBar);
        refreshListPhone = findViewById(R.id.refreshListPhone);
        refreshListDrive = findViewById(R.id.refreshListDrive);
        voiceButton = findViewById(R.id.search_voice_btn);
        btnSettings = findViewById(R.id.btnSettings);

        setupListeners();
        setupNetworkCallback();

        getFilesPhone();

        if (DropboxClientFactory.getClient() != null) {
            getFilesDrive();
        }
    }

    private void setupListeners() {
        creerDepense.setOnClickListener(view -> createFile());
        exportDepenses.setOnClickListener(view -> upload());
        supprimerDepensesPhone.setOnClickListener(v -> deletePhone());
        importDepenses.setOnClickListener(view -> download());
        supprimerDepensesDrive.setOnClickListener(view -> deleteDrive());

        refreshListPhone.setOnClickListener(view -> getFilesPhone());
        refreshListDrive.setOnClickListener(view -> getFilesDrive());

        voiceButton.setOnClickListener(view -> displaySpeechRecognizer());

        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> showSettingsDialog());
        }

        if (listFilePhone != null) {
            listFilePhone.setOnItemClickListener((parent, view, position, id) -> updateButtonStates());
        }
        if (listFileDrive != null) {
            listFileDrive.setOnItemClickListener((parent, view, position, id) -> updateButtonStates());
        }

        if (nomDepense != null) {
            nomDepense.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    suggestCategoryForName(nomDepense.getText().toString());
                }
            });
            nomDepense.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    suggestCategoryForName(s.toString());
                }
            });
        }
    }

    /**
     * Affiche la boîte de dialogue des préférences.
     */
    private void showSettingsDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_CAT_HISTORY, MODE_PRIVATE);
        Map<String, ?> allHistory = prefs.getAll();

        List<String> keys = new ArrayList<>();
        for (String key : allHistory.keySet()) {
            if (key != null && key.startsWith("cat_")) {
                keys.add(key);
            }
        }

        // Tri par ordre alphabétique insensible à la casse
        Collections.sort(keys, (k1, k2) -> {
            String name1 = k1.substring(4);
            String name2 = k2.substring(4);
            return name1.compareToIgnoreCase(name2);
        });

        StringBuilder historyText = new StringBuilder();
        if (keys.isEmpty()) {
            historyText.append("Aucune association enregistrée.");
        } else {
            for (String key : keys) {
                String name = key.substring(4);
                Object catVal = allHistory.get(key);
                if (!name.isEmpty() && catVal != null) {
                    name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                    historyText.append("• ").append(name).append(" ➔ ").append(catVal).append("\n");
                }
            }
        }

        boolean isDropboxConnected = DropboxClientFactory.getClient() != null;
        String statusText = isDropboxConnected ? "Connecté à Dropbox" : "Non connecté à Dropbox";

        new AlertDialog.Builder(this)
                .setTitle("Préférences & Paramètres")
                .setMessage("Statut : " + statusText + "\n\n" +
                        "Historique des Catégories :\n" + historyText)
                .setPositiveButton("Fermer", null)
                .setNeutralButton("Vider l'historique", (dialog, which) -> {
                    prefs.edit().clear().apply();
                    Toast.makeText(MainActivity.this, "Historique des catégories effacé", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(isDropboxConnected ? "Se déconnecter de Dropbox" : "Connexion Dropbox", (dialog, which) -> {
                    if (isDropboxConnected) {
                        getSharedPreferences("dropbox-sample", MODE_PRIVATE).edit().clear().apply();
                        DropboxClientFactory.clearClient();
                        Toast.makeText(MainActivity.this, "Déconnecté de Dropbox", Toast.LENGTH_SHORT).show();
                        driveExpenseList.clear();
                        updateDataDrive();
                    } else {
                        startOAuth2Authentication(MainActivity.this, getString(R.string.APP_KEY),
                                Arrays.asList("account_info.read", "files.content.write", "files.content.read"));
                    }
                })
                .show();
    }

    private void saveCategoryHistory(String name, String category) {
        if (name == null || name.trim().isEmpty() || category == null || category.trim().isEmpty()) return;
        SharedPreferences prefs = getSharedPreferences(PREFS_CAT_HISTORY, MODE_PRIVATE);
        prefs.edit().putString("cat_" + name.trim().toLowerCase(Locale.FRANCE), category.trim()).apply();
    }

    private void suggestCategoryForName(String name) {
        if (name == null || name.trim().isEmpty() || spinnerCategory == null) return;
        SharedPreferences prefs = getSharedPreferences(PREFS_CAT_HISTORY, MODE_PRIVATE);
        String savedCategory = prefs.getString("cat_" + name.trim().toLowerCase(Locale.FRANCE), null);
        if (savedCategory != null) {
            @SuppressWarnings("unchecked")
            ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) spinnerCategory.getAdapter();
            if (adapter != null) {
                int position = adapter.getPosition(savedCategory);
                if (position >= 0) {
                    spinnerCategory.setSelection(position);
                }
            }
        }
    }

    private void setupNetworkCallback() {
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                AppExecutors.getInstance().mainThread().execute(() -> {
                    boolean previousState = accesInternet;
                    accesInternet = true;
                    updateButtonStates();
                    if (!previousState && DropboxClientFactory.getClient() != null) {
                        getFilesDrive();
                        getFilesPhone();
                    }
                });
            }

            @Override
            public void onLost(@NonNull Network network) {
                AppExecutors.getInstance().mainThread().execute(() -> {
                    accesInternet = isNetworkAvailable();
                    updateButtonStates();
                });
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (connectivityManager != null && networkCallback != null) {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            try {
                connectivityManager.registerNetworkCallback(request, networkCallback);
            } catch (Exception e) {
                Log.e(TAG, "Erreur lors de l'enregistrement du NetworkCallback", e);
            }
        }
        accesInternet = isNetworkAvailable();
        updateButtonStates();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
            }
        }
    }

    private boolean isNetworkAvailable() {
        if (connectivityManager == null) return false;
        Network nw = connectivityManager.getActiveNetwork();
        if (nw == null) return false;
        NetworkCapabilities actNw = connectivityManager.getNetworkCapabilities(nw);
        return actNw != null && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH));
    }

    private void displaySpeechRecognizer() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Reconnaissance vocale non disponible", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String sentence = results.get(0);
                parseAndApplyVoiceText(sentence);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void parseAndApplyVoiceText(String sentence) {
        Pattern pattern = Pattern.compile("(?<=\\bdépense\\s)(.+)co(...?)\\s(([\\d\\W,])+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sentence);
        if (matcher.find()) {
            String name = modificationMot(matcher.group(1));
            nomDepense.setText(name);
            cout.setText(matcher.group(3));
            suggestCategoryForName(name);
            createFile();
        } else {
            pattern = Pattern.compile("(?<=\\bdépense\\s)(.+)co(.)*\\s(-?|moins)(([\\d\\W,])+)", Pattern.CASE_INSENSITIVE);
            matcher = pattern.matcher(sentence);
            if (matcher.find()) {
                String name = modificationMot(matcher.group(1));
                nomDepense.setText(name);
                String negVal = "-" + matcher.group(4);
                cout.setText(negVal);
                suggestCategoryForName(name);
                createFile();
            }
        }
    }

    private String modificationMot(String mot) {
        if (mot == null) return "";
        Pattern pattern = Pattern.compile(".ntermarc(.)+", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(mot);
        if (matcher.find()) {
            return "Intermarché";
        }
        return mot.trim();
    }

    @Override
    protected void loadData() {
        if (DropboxClientFactory.getClient() == null) return;
        new GetCurrentAccountTask(DropboxClientFactory.getClient(), new GetCurrentAccountTask.Callback() {
            @Override
            public void onComplete(FullAccount result) {
                Log.d(TAG, "Compte chargé: " + (result != null ? result.getName().getDisplayName() : "inconnu"));
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Échec de la récupération du compte Dropbox.", e);
            }
        }).execute();
    }

    private boolean areItemsChecked(ListView listView) {
        if (listView == null) return false;
        SparseBooleanArray checkItem = listView.getCheckedItemPositions();
        if (checkItem != null) {
            for (int i = 0; i < checkItem.size(); i++) {
                if (checkItem.valueAt(i)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void setLoadingState(boolean loading) {
        isCliquable = !loading;
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        updateButtonStates();
    }

    public void updateButtonStates() {
        boolean hasNetwork = accesInternet;
        boolean canClick = isCliquable;

        if (exportDepenses != null) {
            exportDepenses.setEnabled(hasNetwork && canClick && areItemsChecked(listFilePhone));
        }
        if (importDepenses != null) {
            importDepenses.setEnabled(hasNetwork && canClick && areItemsChecked(listFileDrive));
        }
        if (supprimerDepensesDrive != null) {
            supprimerDepensesDrive.setEnabled(hasNetwork && canClick && areItemsChecked(listFileDrive));
        }
        if (supprimerDepensesPhone != null) {
            supprimerDepensesPhone.setEnabled(canClick && areItemsChecked(listFilePhone));
        }
        if (listFileDrive != null) {
            listFileDrive.setEnabled(hasNetwork && canClick);
        }
        if (listFilePhone != null) {
            listFilePhone.setEnabled(canClick);
        }
        if (refreshListDrive != null) {
            refreshListDrive.setEnabled(hasNetwork && canClick);
        }
        if (refreshListPhone != null) {
            refreshListPhone.setEnabled(canClick);
        }
        if (creerDepense != null) {
            creerDepense.setEnabled(canClick);
        }
    }

    private List<Expense> getCheckedLocalExpenses() {
        List<Expense> result = new ArrayList<>();
        if (listFilePhone == null) return result;
        SparseBooleanArray checked = listFilePhone.getCheckedItemPositions();
        if (checked != null) {
            for (int i = 0; i < checked.size(); i++) {
                if (checked.valueAt(i)) {
                    int pos = checked.keyAt(i);
                    if (pos >= 0 && pos < localExpenseList.size()) {
                        result.add(localExpenseList.get(pos));
                    }
                }
            }
        }
        return result;
    }

    private List<Expense> getCheckedDriveExpenses() {
        List<Expense> result = new ArrayList<>();
        if (listFileDrive == null) return result;
        SparseBooleanArray checked = listFileDrive.getCheckedItemPositions();
        if (checked != null) {
            for (int i = 0; i < checked.size(); i++) {
                if (checked.valueAt(i)) {
                    int pos = checked.keyAt(i);
                    if (pos >= 0 && pos < driveExpenseList.size()) {
                        result.add(driveExpenseList.get(pos));
                    }
                }
            }
        }
        return result;
    }

    private boolean isExpenseSelected(Expense expense, List<Expense> selectedList) {
        if (expense == null || selectedList == null) return false;
        for (Expense sel : selectedList) {
            if (sel != null && Objects.equals(sel.getName(), expense.getName())
                    && Objects.equals(sel.getAmount(), expense.getAmount())
                    && Objects.equals(sel.getCategory(), expense.getCategory())) {
                return true;
            }
        }
        return false;
    }

    private void createFile() {
        final String nameStr = nomDepense.getText().toString().trim();
        final String coutStr = cout.getText().toString().trim();
        final String categoryStr = spinnerCategory != null && spinnerCategory.getSelectedItem() != null ?
                spinnerCategory.getSelectedItem().toString() : "Autre";

        if (nameStr.isEmpty() || coutStr.isEmpty()) {
            Toast.makeText(this, "Veuillez remplir le nom et le coût", Toast.LENGTH_SHORT).show();
            return;
        }

        saveCategoryHistory(nameStr, categoryStr);

        setLoadingState(true);
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                expenseManager.createExpense(nameStr, coutStr, categoryStr);
                AppExecutors.getInstance().mainThread().execute(() -> {
                    nomDepense.setText("");
                    cout.setText("");
                    setLoadingState(false);
                    getFilesPhone();
                    Toast.makeText(MainActivity.this, "Dépense créée avec succès !", Toast.LENGTH_SHORT).show();
                });
            } catch (IOException e) {
                Log.e(TAG, "Erreur lors de la création de la dépense", e);
                AppExecutors.getInstance().mainThread().execute(() -> {
                    setLoadingState(false);
                    Toast.makeText(MainActivity.this, "Erreur lors de la création", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void getFilesPhone() {
        setLoadingState(true);
        AppExecutors.getInstance().diskIO().execute(() -> {
            List<Expense> expenses = expenseManager.getLocalExpenses();
            AppExecutors.getInstance().mainThread().execute(() -> {
                localExpenseList.clear();
                localExpenseList.addAll(expenses);
                for (Expense e : expenses) {
                    if (e != null) {
                        saveCategoryHistory(e.getName(), e.getCategory());
                    }
                }
                updateDataPhone();
                setLoadingState(false);
            });
        });
    }

    private void getFilesDrive() {
        if (DropboxClientFactory.getClient() == null) {
            updateDataDrive();
            return;
        }
        setLoadingState(true);
        new ListDriveTask(DropboxClientFactory.getClient(), storagePath, new Callback() {
            @Override
            public void onTaskComplete(ArrayList<File> result) {
                driveExpenseList.clear();
                if (result != null) {
                    for (File file : result) {
                        List<Expense> expensesInFile = Expense.parseAllExpensesFromFile(file);
                        driveExpenseList.addAll(expensesInFile);
                        for (Expense e : expensesInFile) {
                            if (e != null) {
                                saveCategoryHistory(e.getName(), e.getCategory());
                            }
                        }
                        ExpenseManager.deleteFileQuietly(file);
                    }
                }
                updateDataDrive();
                setLoadingState(false);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Échec du listage Dropbox", e);
                setLoadingState(false);
            }
        }).execute();
    }

    private void deletePhone() {
        List<Expense> expensesToDelete = getCheckedLocalExpenses();
        if (expensesToDelete.isEmpty()) return;

        setLoadingState(true);
        AppExecutors.getInstance().diskIO().execute(() -> {
            for (Expense expense : expensesToDelete) {
                if (expense != null && expense.getFile() != null) {
                    ExpenseManager.deleteFileQuietly(expense.getFile());
                }
            }
            AppExecutors.getInstance().mainThread().execute(() -> {
                getFilesPhone();
                setLoadingState(false);
                Toast.makeText(MainActivity.this, "Dépenses supprimées de l'appareil !", Toast.LENGTH_LONG).show();
            });
        });
    }

    private void deleteDrive() {
        List<Expense> selectedExpenses = getCheckedDriveExpenses();
        if (selectedExpenses.isEmpty() || DropboxClientFactory.getClient() == null) return;

        List<File> filesToDownload = new ArrayList<>();
        for (Expense e : selectedExpenses) {
            if (e != null && e.getFile() != null && !filesToDownload.contains(e.getFile())) {
                filesToDownload.add(e.getFile());
            }
        }

        setLoadingState(true);
        new DownloadFileTask(getApplicationContext(), DropboxClientFactory.getClient(), storagePath, new Callback() {
            @Override
            public void onTaskComplete(ArrayList<File> result) {
                if (result != null) {
                    AppExecutors.getInstance().diskIO().execute(() -> {
                        List<Expense> unselectedExpensesToKeep = new ArrayList<>();

                        for (File file : result) {
                            List<Expense> expensesInFile = Expense.parseAllExpensesFromFile(file);
                            for (Expense expense : expensesInFile) {
                                if (!isExpenseSelected(expense, selectedExpenses)) {
                                    unselectedExpensesToKeep.add(expense);
                                }
                            }
                            ExpenseManager.deleteFileQuietly(file);
                        }

                        if (!unselectedExpensesToKeep.isEmpty()) {
                            try {
                                File unselectedCsv = expenseManager.generateConsolidatedCsvFile(unselectedExpensesToKeep);
                                if (unselectedCsv != null) {
                                    List<File> toReUpload = new ArrayList<>();
                                    toReUpload.add(unselectedCsv);
                                    new UploadFileTask(DropboxClientFactory.getClient(), new Callback() {
                                        @Override
                                        public void onTaskComplete(ArrayList<File> res) {
                                            ExpenseManager.deleteFileQuietly(unselectedCsv);
                                            AppExecutors.getInstance().mainThread().execute(() -> {
                                                getFilesDrive();
                                                setLoadingState(false);
                                                Toast.makeText(MainActivity.this, "Dépenses sélectionnées supprimées de Dropbox !", Toast.LENGTH_LONG).show();
                                            });
                                        }

                                        @Override
                                        public void onError(Exception e) {
                                            Log.e(TAG, "Échec de la ré-upload", e);
                                            setLoadingState(false);
                                        }
                                    }).execute(toReUpload);
                                    return;
                                }
                            } catch (IOException e) {
                                Log.e(TAG, "Erreur génération CSV", e);
                            }
                        }

                        AppExecutors.getInstance().mainThread().execute(() -> {
                            getFilesDrive();
                            setLoadingState(false);
                            Toast.makeText(MainActivity.this, "Dépenses sélectionnées supprimées de Dropbox !", Toast.LENGTH_LONG).show();
                        });
                    });
                } else {
                    setLoadingState(false);
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Échec de la suppression Dropbox", e);
                setLoadingState(false);
            }
        }).execute(filesToDownload);
    }

    private void upload() {
        List<Expense> selectedExpenses = getCheckedLocalExpenses();
        if (selectedExpenses.isEmpty() || DropboxClientFactory.getClient() == null) return;

        setLoadingState(true);
        // Exporte les dépenses sélectionnées sous la forme d'UN SEUL fichier CSV propre pour Excel
        AppExecutors.getInstance().diskIO().execute(() -> {
            File csvFile = null;
            try {
                csvFile = expenseManager.generateConsolidatedCsvFile(selectedExpenses);
            } catch (IOException e) {
                Log.e(TAG, "Erreur création CSV", e);
            }

            if (csvFile == null) {
                AppExecutors.getInstance().mainThread().execute(() -> setLoadingState(false));
                return;
            }

            final File finalCsvFile = csvFile;
            List<File> uploadList = new ArrayList<>();
            uploadList.add(csvFile);

            AppExecutors.getInstance().mainThread().execute(() -> {
                new UploadFileTask(DropboxClientFactory.getClient(), new Callback() {
                    @Override
                    public void onTaskComplete(ArrayList<File> result) {
                        AppExecutors.getInstance().diskIO().execute(() -> {
                            // Supprime uniquement les fichiers texte locaux des dépenses sélectionnées
                            for (Expense expense : selectedExpenses) {
                                if (expense != null && expense.getFile() != null) {
                                    ExpenseManager.deleteFileQuietly(expense.getFile());
                                }
                            }
                            ExpenseManager.deleteFileQuietly(finalCsvFile);
                            AppExecutors.getInstance().mainThread().execute(() -> {
                                getFilesPhone();
                                getFilesDrive();
                                setLoadingState(false);
                                Toast.makeText(MainActivity.this, "Export CSV envoyé avec succès sur Dropbox !", Toast.LENGTH_LONG).show();
                            });
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Échec de l'envoi Dropbox", e);
                        setLoadingState(false);
                    }
                }).execute(uploadList);
            });
        });
    }

    private void download() {
        List<Expense> selectedExpenses = getCheckedDriveExpenses();
        if (selectedExpenses.isEmpty() || DropboxClientFactory.getClient() == null) return;

        List<File> filesToDownload = new ArrayList<>();
        for (Expense e : selectedExpenses) {
            if (e != null && e.getFile() != null && !filesToDownload.contains(e.getFile())) {
                filesToDownload.add(e.getFile());
            }
        }

        setLoadingState(true);
        new DownloadFileTask(getApplicationContext(), DropboxClientFactory.getClient(), storagePath, new Callback() {
            @Override
            public void onTaskComplete(ArrayList<File> result) {
                if (result != null) {
                    AppExecutors.getInstance().diskIO().execute(() -> {
                        List<Expense> unselectedExpensesToKeep = new ArrayList<>();

                        for (File file : result) {
                            List<Expense> expensesInFile = Expense.parseAllExpensesFromFile(file);
                            for (Expense expense : expensesInFile) {
                                if (isExpenseSelected(expense, selectedExpenses)) {
                                    try {
                                        // Crée un fichier texte local individuel pour la dépense SÉLECTIONNÉE
                                        expenseManager.createExpense(expense.getName(), expense.getAmount(), expense.getCategory());
                                    } catch (IOException e) {
                                        Log.e(TAG, "Échec de la création de la dépense importée", e);
                                    }
                                } else {
                                    // Garde la dépense NON sélectionnée pour la conserver sur Dropbox
                                    unselectedExpensesToKeep.add(expense);
                                }
                            }
                            ExpenseManager.deleteFileQuietly(file);
                        }

                        // Si certaines dépenses du fichier CSV n'ont PAS été sélectionnées, les ré-exporter sur Dropbox !
                        if (!unselectedExpensesToKeep.isEmpty()) {
                            try {
                                File unselectedCsv = expenseManager.generateConsolidatedCsvFile(unselectedExpensesToKeep);
                                if (unselectedCsv != null) {
                                    List<File> toReUpload = new ArrayList<>();
                                    toReUpload.add(unselectedCsv);
                                    new UploadFileTask(DropboxClientFactory.getClient(), new Callback() {
                                        @Override
                                        public void onTaskComplete(ArrayList<File> res) {
                                            ExpenseManager.deleteFileQuietly(unselectedCsv);
                                            AppExecutors.getInstance().mainThread().execute(() -> {
                                                getFilesPhone();
                                                getFilesDrive();
                                                setLoadingState(false);
                                                Toast.makeText(MainActivity.this, "Dépenses sélectionnées importées !", Toast.LENGTH_LONG).show();
                                            });
                                        }

                                        @Override
                                        public void onError(Exception e) {
                                            Log.e(TAG, "Échec de la ré-upload", e);
                                            setLoadingState(false);
                                        }
                                    }).execute(toReUpload);
                                    return;
                                }
                            } catch (IOException e) {
                                Log.e(TAG, "Erreur génération CSV", e);
                            }
                        }

                        AppExecutors.getInstance().mainThread().execute(() -> {
                            getFilesPhone();
                            getFilesDrive();
                            setLoadingState(false);
                            Toast.makeText(MainActivity.this, "Dépenses sélectionnées importées !", Toast.LENGTH_LONG).show();
                        });
                    });
                } else {
                    setLoadingState(false);
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Échec du téléchargement Dropbox", e);
                setLoadingState(false);
            }
        }).execute(filesToDownload);
    }

    public void updateDataPhone() {
        List<String> phoneDisplayList = new ArrayList<>();
        for (Expense expense : localExpenseList) {
            if (expense != null) {
                phoneDisplayList.add(expense.getDisplayText());
            }
        }
        ArrayAdapter<String> adapterPhone = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, phoneDisplayList);
        if (listFilePhone != null) {
            listFilePhone.setAdapter(adapterPhone);
        }
        double sumLocal = ExpenseManager.calculateTotalAmount(localExpenseList);
        if (totalLocal != null) {
            totalLocal.setText(String.format(Locale.FRANCE, "Total : %.2f €", sumLocal));
        }
        updateButtonStates();
    }

    public void updateDataDrive() {
        List<String> driveDisplayList = new ArrayList<>();
        for (Expense expense : driveExpenseList) {
            if (expense != null) {
                driveDisplayList.add(expense.getDisplayText());
            }
        }
        ArrayAdapter<String> adapterDrive = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, driveDisplayList);
        if (listFileDrive != null) {
            listFileDrive.setAdapter(adapterDrive);
        }
        double sumDrive = ExpenseManager.calculateTotalAmount(driveExpenseList);
        if (totalDrive != null) {
            totalDrive.setText(String.format(Locale.FRANCE, "Total : %.2f €", sumDrive));
        }
        updateButtonStates();
    }
}
