package com.app.gestiondepenses;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.app.dropbox.DropboxClientFactory;
import com.app.dropbox.LoginActivity;
import com.app.interfaceGestion.Callback;
import com.app.managers.ExpenseManager;
import com.app.models.Expense;
import com.app.tasks.DeleteFileTask;
import com.app.tasks.DownloadFileTask;
import com.app.tasks.GetCurrentAccountTask;
import com.app.tasks.ListDriveTask;
import com.app.tasks.UploadFileTask;
import com.app.utils.AppExecutors;
import com.dropbox.core.v2.users.FullAccount;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Activité principale gérant la saisie, l'affichage local et la synchronisation Dropbox des dépenses.
 */
public class MainActivity extends LoginActivity {

    private static final String TAG = "MainActivity";
    private static final int SPEECH_REQUEST_CODE = 100;

    // Dossier de stockage local sécurisé spécifique à l'application
    private static File storagePath;
    private ExpenseManager expenseManager;

    // Composants de l'interface graphique (UI)
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

    private EditText nomDepense;
    private EditText cout;
    private ProgressBar progressBar;

    // Cartes associant le fichier physique (.txt) à son texte d'affichage ("Nom | Coût €")
    private final HashMap<File, String> mapFilePhone = new HashMap<>();
    private final HashMap<File, String> mapFileDrive = new HashMap<>();

    // États du réseau et d'interaction utilisateur
    private boolean accesInternet = true;
    private boolean isCliquable = true;

    // Gestionnaire réactif de l'état du réseau (remplace l'ancienne boucle infinie de 100ms)
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialisation du dossier de stockage interne/externe sans dépendre de permissions obsolètes
        File[] externalFilesDirs = ContextCompat.getExternalFilesDirs(getApplicationContext(), null);
        storagePath = (externalFilesDirs != null && externalFilesDirs.length > 0) ? externalFilesDirs[0] : getFilesDir();
        expenseManager = new ExpenseManager(storagePath);

        setContentView(R.layout.activity_main);

        // Liaison des vues XML aux variables Java
        nomDepense = findViewById(R.id.nomDepense);
        cout = findViewById(R.id.cout);

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

        // Configuration des événements clics et de l'écouteur de réseau
        setupListeners();
        setupNetworkCallback();

        // Chargement initial des dépenses locales
        getFilesPhone();

        // Chargement des dépenses distantes Dropbox si le client est authentifié
        if (DropboxClientFactory.getClient() != null) {
            getFilesDrive();
        }
    }

    /**
     * Attache les écouteurs d'événements aux boutons et aux listes.
     */
    private void setupListeners() {
        creerDepense.setOnClickListener(view -> createFile());
        exportDepenses.setOnClickListener(view -> upload());
        supprimerDepensesPhone.setOnClickListener(v -> deletePhone());
        importDepenses.setOnClickListener(view -> download());
        supprimerDepensesDrive.setOnClickListener(view -> deleteDrive());

        refreshListPhone.setOnClickListener(view -> getFilesPhone());
        refreshListDrive.setOnClickListener(view -> getFilesDrive());

        voiceButton.setOnClickListener(view -> displaySpeechRecognizer());

        // Mise à jour automatique de l'état des boutons lors de la sélection/décochage d'éléments
        if (listFilePhone != null) {
            listFilePhone.setOnItemClickListener((parent, view, position, id) -> updateButtonStates());
        }
        if (listFileDrive != null) {
            listFileDrive.setOnItemClickListener((parent, view, position, id) -> updateButtonStates());
        }
    }

    /**
     * Configure un NetworkCallback pour être notifié des changements de connexion
     * (évite de consommer la batterie avec une boucle de vérification constante).
     */
    private void setupNetworkCallback() {
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                // Exécution sur le thread principal pour mettre à jour l'UI
                AppExecutors.getInstance().mainThread().execute(() -> {
                    boolean previousState = accesInternet;
                    accesInternet = true;
                    updateButtonStates();
                    // Si la connexion vient de revenir, rafraîchir les listes
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
        // Enregistrement de l'écouteur réseau lors du démarrage de l'activité
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
        // Désinscription de l'écouteur réseau pour éviter toute fuite de mémoire
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Teste si un réseau actif dispose d'une connexion internet utilisable.
     */
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

    /**
     * Déclenche l'assistant de reconnaissance vocale Android.
     */
    private void displaySpeechRecognizer() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Reconnaissance vocale non disponible", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Traite le résultat retourné par la reconnaissance vocale.
     */
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

    /**
     * Extrait le nom du commerce et le coût à partir d'une phrase dictée (ex: "dépense Courses coûte 25,50").
     */
    private void parseAndApplyVoiceText(String sentence) {
        Pattern pattern = Pattern.compile("(?<=\\bdépense\\s)(.+)co(...?)\\s(([\\d\\W,])+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sentence);
        if (matcher.find()) {
            nomDepense.setText(modificationMot(matcher.group(1)));
            cout.setText(matcher.group(3));
            createFile();
        } else {
            pattern = Pattern.compile("(?<=\\bdépense\\s)(.+)co(.)*\\s(-?|moins)(([\\d\\W,])+)", Pattern.CASE_INSENSITIVE);
            matcher = pattern.matcher(sentence);
            if (matcher.find()) {
                nomDepense.setText(modificationMot(matcher.group(1)));
                String negValue = "-" + matcher.group(4);
                cout.setText(negValue);
                createFile();
            }
        }
    }

    /**
     * Corrige automatiquement certaines erreurs courantes de dictée (ex: "Intermarché").
     */
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
                Log.e(TAG, "Échec lors de la récupération du compte Dropbox.", e);
            }
        }).execute();
    }

    /**
     * Vérifie si au moins un élément est coché dans une ListView.
     */
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

    /**
     * Active/désactive l'indicateur de chargement et verrouille l'interface durant une tâche d'arrière-plan.
     */
    private void setLoadingState(boolean loading) {
        isCliquable = !loading;
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        updateButtonStates();
    }

    /**
     * Met à jour dynamiquement l'état activé/désactivé des boutons en fonction du réseau,
     * de l'état de chargement et des éléments cochés.
     */
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

    /**
     * Récupère la liste des fichiers correspondant aux éléments actuellement cochés dans une ListView.
     */
    private List<File> getCheckedFiles(ListView listView, Map<File, String> map) {
        List<File> files = new ArrayList<>();
        if (listView == null || map == null) return files;

        SparseBooleanArray checked = listView.getCheckedItemPositions();
        if (checked != null) {
            for (int i = 0; i < checked.size(); i++) {
                if (checked.valueAt(i)) {
                    Object itemObj = listView.getAdapter().getItem(checked.keyAt(i));
                    if (itemObj != null) {
                        String itemText = itemObj.toString();
                        File match = getFileValue(map, itemText, files);
                        if (match != null) {
                            files.add(match);
                        }
                    }
                }
            }
        }
        return files;
    }

    /**
     * Recherche la clé (File) dans une Map à partir de sa valeur affichée.
     */
    public static <T, E> File getFileValue(Map<T, E> map, E value, List<T> files) {
        if (map == null || value == null) return null;
        for (Map.Entry<T, E> entry : map.entrySet()) {
            if (Objects.equals(value, entry.getValue()) && (files == null || !files.contains(entry.getKey()))) {
                return (File) entry.getKey();
            }
        }
        return null;
    }

    /**
     * Crée une nouvelle dépense locale de manière asynchrone sans bloquer l'IHM.
     */
    private void createFile() {
        final String nameStr = nomDepense.getText().toString().trim();
        final String coutStr = cout.getText().toString().trim();

        if (nameStr.isEmpty() || coutStr.isEmpty()) {
            Toast.makeText(this, "Veuillez remplir le nom et le coût", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoadingState(true);
        // Exécution en arrière-plan sur le pool de threads de stockage
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                expenseManager.createExpense(nameStr, coutStr);
                // Retour sur le thread principal pour mettre à jour l'IHM
                AppExecutors.getInstance().mainThread().execute(() -> {
                    nomDepense.setText("");
                    cout.setText("");
                    setLoadingState(false);
                    getFilesPhone();
                    Toast.makeText(MainActivity.this, "Une dépense a été créée !", Toast.LENGTH_SHORT).show();
                });
            } catch (IOException e) {
                Log.e(TAG, "Erreur lors de la création du fichier de dépense", e);
                AppExecutors.getInstance().mainThread().execute(() -> {
                    setLoadingState(false);
                    Toast.makeText(MainActivity.this, "Erreur lors de la création de la dépense", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * Charge de manière asynchrone les dépenses enregistrées localement sur le téléphone.
     */
    private void getFilesPhone() {
        setLoadingState(true);
        AppExecutors.getInstance().diskIO().execute(() -> {
            List<Expense> expenses = expenseManager.getLocalExpenses();
            AppExecutors.getInstance().mainThread().execute(() -> {
                mapFilePhone.clear();
                for (Expense expense : expenses) {
                    mapFilePhone.put(expense.getFile(), expense.getDisplayText());
                }
                updateDataPhone();
                setLoadingState(false);
            });
        });
    }

    /**
     * Récupère la liste des dépenses stockées sur le compte Dropbox.
     */
    private void getFilesDrive() {
        if (DropboxClientFactory.getClient() == null) {
            updateDataDrive();
            return;
        }
        setLoadingState(true);
        new ListDriveTask(DropboxClientFactory.getClient(), storagePath, new Callback() {
            @Override
            public void onTaskComplete(ArrayList<File> result) {
                mapFileDrive.clear();
                if (result != null) {
                    for (File file : result) {
                        Expense expense = Expense.fromFile(file);
                        if (expense != null) {
                            mapFileDrive.put(file, expense.getDisplayText());
                        }
                        ExpenseManager.deleteFileQuietly(file);
                    }
                }
                updateDataDrive();
                setLoadingState(false);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Échec du listage des fichiers Dropbox", e);
                setLoadingState(false);
            }
        }).execute();
    }

    /**
     * Supprime les dépenses locales sélectionnées.
     */
    private void deletePhone() {
        List<File> filesToDelete = getCheckedFiles(listFilePhone, mapFilePhone);
        if (filesToDelete.isEmpty()) return;

        setLoadingState(true);
        AppExecutors.getInstance().diskIO().execute(() -> {
            for (File file : filesToDelete) {
                mapFilePhone.remove(file);
                ExpenseManager.deleteFileQuietly(file);
            }
            AppExecutors.getInstance().mainThread().execute(() -> {
                updateDataPhone();
                setLoadingState(false);
                Toast.makeText(MainActivity.this, "Les dépenses sélectionnées ont été supprimées de l'appareil !", Toast.LENGTH_LONG).show();
            });
        });
    }

    /**
     * Supprime de Dropbox les dépenses sélectionnées dans la liste Drive.
     */
    private void deleteDrive() {
        List<File> filesToDelete = getCheckedFiles(listFileDrive, mapFileDrive);
        if (filesToDelete.isEmpty() || DropboxClientFactory.getClient() == null) return;

        setLoadingState(true);
        new DeleteFileTask(DropboxClientFactory.getClient(), new Callback() {
            @Override
            public void onTaskComplete(ArrayList<File> result) {
                if (result != null) {
                    for (File file : result) {
                        mapFileDrive.remove(file);
                    }
                }
                updateDataDrive();
                setLoadingState(false);
                Toast.makeText(MainActivity.this, "Les dépenses sélectionnées ont été supprimées du drive !", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Échec de la suppression sur Dropbox", e);
                setLoadingState(false);
            }
        }).execute(filesToDelete);
    }

    /**
     * Exporte les dépenses sélectionnées du téléphone vers Dropbox, puis les retire du téléphone.
     */
    private void upload() {
        List<File> filesToUpload = getCheckedFiles(listFilePhone, mapFilePhone);
        if (filesToUpload.isEmpty() || DropboxClientFactory.getClient() == null) return;

        setLoadingState(true);
        new UploadFileTask(DropboxClientFactory.getClient(), new Callback() {
            @Override
            public void onTaskComplete(ArrayList<File> result) {
                if (result != null) {
                    AppExecutors.getInstance().diskIO().execute(() -> {
                        for (File file : result) {
                            Expense expense = Expense.fromFile(file);
                            if (expense != null) {
                                mapFileDrive.put(file, expense.getDisplayText());
                            }
                            mapFilePhone.remove(file);
                            ExpenseManager.deleteFileQuietly(file);
                        }
                        AppExecutors.getInstance().mainThread().execute(() -> {
                            updateDataPhone();
                            updateDataDrive();
                            setLoadingState(false);
                            Toast.makeText(MainActivity.this, "Les dépenses ont été exportées vers Dropbox !", Toast.LENGTH_LONG).show();
                        });
                    });
                } else {
                    setLoadingState(false);
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Échec de l'envoi vers Dropbox", e);
                setLoadingState(false);
            }
        }).execute(filesToUpload);
    }

    /**
     * Importe les dépenses sélectionnées de Dropbox vers le téléphone.
     */
    private void download() {
        List<File> filesToDownload = getCheckedFiles(listFileDrive, mapFileDrive);
        if (filesToDownload.isEmpty() || DropboxClientFactory.getClient() == null) return;

        setLoadingState(true);
        new DownloadFileTask(getApplicationContext(), DropboxClientFactory.getClient(), storagePath, new Callback() {
            @Override
            public void onTaskComplete(ArrayList<File> result) {
                if (result != null) {
                    AppExecutors.getInstance().diskIO().execute(() -> {
                        for (File file : result) {
                            mapFileDrive.remove(file);
                            Expense expense = Expense.fromFile(file);
                            if (expense != null) {
                                mapFilePhone.put(file, expense.getDisplayText());
                            } else {
                                ExpenseManager.deleteFileQuietly(file);
                            }
                        }
                        AppExecutors.getInstance().mainThread().execute(() -> {
                            updateDataPhone();
                            updateDataDrive();
                            setLoadingState(false);
                            Toast.makeText(MainActivity.this, "Les dépenses sélectionnées ont été importées dans l'appareil !", Toast.LENGTH_LONG).show();
                        });
                    });
                } else {
                    setLoadingState(false);
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Échec du téléchargement depuis Dropbox", e);
                setLoadingState(false);
            }
        }).execute(filesToDownload);
    }

    /**
     * Met à jour l'adaptateur de la ListView des dépenses locales du téléphone.
     */
    public void updateDataPhone() {
        ArrayAdapter<String> adapterPhone = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, new ArrayList<>(mapFilePhone.values()));
        if (listFilePhone != null) {
            listFilePhone.setAdapter(adapterPhone);
        }
        updateButtonStates();
    }

    /**
     * Met à jour l'adaptateur de la ListView des dépenses présentes sur Dropbox.
     */
    public void updateDataDrive() {
        ArrayAdapter<String> adapterDrive = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, new ArrayList<>(mapFileDrive.values()));
        if (listFileDrive != null) {
            listFileDrive.setAdapter(adapterDrive);
        }
        updateButtonStates();
    }
}
