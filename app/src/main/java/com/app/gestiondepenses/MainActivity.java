package com.app.gestiondepenses;


import static android.os.storage.StorageManager.ACTION_MANAGE_STORAGE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.app.dropbox.DropboxClient;
import com.app.dropbox.DropboxClientFactory;
import com.app.dropbox.LoginActivity;
import com.app.interfaceGestion.Callback;
import com.app.tasks.DeleteFileTask;
import com.app.tasks.DownloadFileTask;
import com.app.tasks.GetCurrentAccountTask;
import com.app.tasks.ListDriveTask;
import com.app.tasks.UploadFileTask;
import com.dropbox.core.v2.users.FullAccount;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

public class MainActivity extends LoginActivity {

    private String ACCESS_TOKEN;

    private static final Logger LOGGER = Logger.getLogger(MainActivity.class.getName());

    private static final int REQUEST_PERMISSION = 1;
    private static File PATH ;

    private ListView listFilePhone = null;

    private ListView listFileDrive = null;

    private Button creerDepense = null;

    private Button supprimerDepensesPhone = null;

    private ImageButton exportDepenses = null;

    private ImageButton importDepenses = null;

    private Button supprimerDepensesDrive = null;

    private ImageButton refreshListPhone = null;

    private ImageButton refreshListDrive = null;

    private EditText nomDepense = null;
    private EditText cout = null;

    private final HashMap<File, String> mapFilePhone = new HashMap<>();
    private final HashMap<File, String> mapFileDrive = new HashMap<>();

    private Boolean accesInternet = true;
    private Boolean isCliquable = true;

    private ProgressBar progressBar;
    private final android.os.Handler handler = new android.os.Handler();


    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread thread = new Thread() {

            @Override
            public void run() {
                try {
                    while (!isInterrupted()) {
                        Thread.sleep(100);
                        runOnUiThread(MainActivity.this::checkInternetAccess);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Logger.getLogger(Objects.requireNonNull(e.getMessage()));
                }
            }
        };

        thread.start();

        PATH = ContextCompat.getExternalFilesDirs(getApplicationContext(), null)[0];

        setContentView(R.layout.activity_main);


            ACCESS_TOKEN = "1";

            nomDepense = findViewById(R.id.nomDepense);

            cout = findViewById(R.id.cout);

            listFilePhone = findViewById(R.id.listViewPhone);

            supprimerDepensesPhone = findViewById(R.id.supprimerDepenses);
            creerDepense = this.findViewById(R.id.creerDepense);

            exportDepenses = findViewById(R.id.exportDepense);
            importDepenses = findViewById(R.id.importDepense);
            supprimerDepensesDrive = findViewById(R.id.supprimerDepensesDrive);
            listFileDrive = findViewById(R.id.listViewDrive);
            progressBar = findViewById(R.id.progressBar);
            refreshListPhone = findViewById(R.id.refreshListPhone);
            refreshListDrive = findViewById(R.id.refreshListDrive);


        getFilesPhone();

        if(null!=DropboxClientFactory.getClient()) {
                getFilesDrive();
            }
            creerDepense.setOnClickListener(view -> {
                isCliquable = false;
                createFile();
                isCliquable = true;
            });

            exportDepenses.setOnClickListener(view -> new Thread(() -> {
                isCliquable = false;

                MainActivity.this.handler.post(() -> progressBar.setVisibility(View.VISIBLE));
                upload();
                MainActivity.this.handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    isCliquable = true;
                });

            }
            ).start());

            supprimerDepensesPhone.setOnClickListener(v -> new Thread(() -> {

                MainActivity.this.handler.post(() -> {
                    isCliquable = false;

                    progressBar.setVisibility(View.VISIBLE);
                });
                deletePhone();

                MainActivity.this.handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    isCliquable = true;
                    updateDataPhone();
                    Toast.makeText(MainActivity.this, "Les dépenses sélectionnées ont été supprimées de l'appareil !", Toast.LENGTH_LONG).show();
                });

            }
            ).start());


            importDepenses.setOnClickListener(view -> new Thread(() -> {


                MainActivity.this.handler.post(() -> {
                    isCliquable = false;

                    progressBar.setVisibility(View.VISIBLE);
                });
                download();
                MainActivity.this.handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    isCliquable = true;

                    Toast.makeText(MainActivity.this, "Les dépenses sélectionnées ont été importées dans l'appareil !", Toast.LENGTH_LONG).show();
                });

            }
            ).start());

            supprimerDepensesDrive.setOnClickListener(v -> new Thread(() -> {


                MainActivity.this.handler.post(() -> {
                    isCliquable = false;

                    progressBar.setVisibility(View.VISIBLE);
                });
                deleteDrive();
                MainActivity.this.handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    isCliquable = true;

                    Toast.makeText(MainActivity.this, "Les dépenses sélectionnées ont été supprimées du drive !", Toast.LENGTH_LONG).show();
                });

            }
            ).start());
            refreshListPhone.setOnClickListener(view -> {
                isCliquable = false;
                getFilesPhone();
                isCliquable = true;
            });

            refreshListDrive.setOnClickListener(view -> new Thread(() -> {

                MainActivity.this.handler.post(() -> {
                    isCliquable = false;
                    progressBar.setVisibility(View.VISIBLE);
                });
                getFilesDrive();
                MainActivity.this.handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    isCliquable = true;
                });

            }
            ).start());


    }

    @Override
    protected void loadData() {
        new GetCurrentAccountTask(DropboxClientFactory.getClient(), new GetCurrentAccountTask.Callback() {
            @Override
            public void onComplete(FullAccount result) {
            }

            @Override
            public void onError(Exception e) {
                Log.e(getClass().getName(), "Failed to get account details.", e);
            }
        }).execute();
    }

    /**
     * Vérifie si les items sont sélectionnés dans les listes
     *
     * @param listViewP
     * @return
     */
    private boolean areItemsChecked(ListView listViewP) {

        SparseBooleanArray checkItem = listViewP.getCheckedItemPositions();

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
     * Suppression de fichiers présent dans l'appareil
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void deletePhone() {
        if (ACCESS_TOKEN == null) return;

        SparseBooleanArray checkItem = listFilePhone.getCheckedItemPositions();

        ArrayList<File> files = new ArrayList<>();

        if (checkItem != null) {
            for (int i = 0; i < checkItem.size(); i++) {
                if (checkItem.valueAt(i)) {
                    String item = listFilePhone.getAdapter().getItem(
                            checkItem.keyAt(i)).toString();

                    files.add(getFileValue(mapFilePhone, item, files));
                }
            }
        }

        if (!files.isEmpty()) {
            for (File file : files) {
                removeListPhone(file);
            }
        }

    }

    /**
     * Suppression de dépenses présentent sur le drive
     */
    private void deleteDrive() {
        if (ACCESS_TOKEN == null) return;
        SparseBooleanArray checkItem = listFileDrive.getCheckedItemPositions();
        ArrayList<File> files = new ArrayList<>();

        if (checkItem != null) {
            for (int i = 0; i < checkItem.size(); i++) {
                if (checkItem.valueAt(i)) {
                    String item = listFileDrive.getAdapter().getItem(
                            checkItem.keyAt(i)).toString();

                    if (null != getFileValue(mapFileDrive, item, files))
                        files.add(getFileValue(mapFileDrive, item, files));
                }
            }
        }

        try {

            DeleteFileTask deleteFileTask = new DeleteFileTask(DropboxClientFactory.getClient(), new Callback() {
                @Override
                public void onTaskComplete(ArrayList<File> result) {
                    for (File file : result) {
                        removeListDrive(file);
                    }
                    updateDataDrive();
                }

                @Override
                public void onError(Exception e) {
                    Logger.getLogger(Objects.requireNonNull(e.getMessage()));
                }
            });
            deleteFileTask.execute(files).get();

        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
        }

    }

    /**
     * Ajout du fichier texte de l'appareil dans la map
     *
     * @param file Fichier txt
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void addListPhone(File file) {
        if (readFile(file) != null) {
            mapFilePhone.put(file, readFile(file));
        } else {
            deleteFolder(file);
        }
    }

    /**
     * Suppression du fichier txt de l'appareil de la map
     *
     * @param file Fichier txt
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void removeListPhone(File file) {
        mapFilePhone.remove(file);

        MainActivity.deleteFolder(file);

    }

    /**
     * Ajout d'un fichier présent sur le drive dans la map
     *
     * @param file Fichier txt
     */
    private void addListDrive(File file) {
        if (readFile(file) != null) {
            mapFileDrive.put(file, readFile(file));
        }
    }

    /**
     * Supprimer le fichier de la map drive
     *
     * @param file Fichier txt
     */
    private void removeListDrive(File file) {
        mapFileDrive.remove(file);
    }

    /**
     * Exporte la dépense vers le drive
     */
    private void upload() {
        if (ACCESS_TOKEN == null) return;


        SparseBooleanArray checkItem = listFilePhone.getCheckedItemPositions();

        ArrayList<File> files = new ArrayList<>();

        if (checkItem != null) {
            for (int i = 0; i < checkItem.size(); i++) {
                if (checkItem.valueAt(i)) {
                    String item = listFilePhone.getAdapter().getItem(
                            checkItem.keyAt(i)).toString();

                    files.add(getFileValue(mapFilePhone, item, files));
                }
            }
        }

        if (!files.isEmpty()) {

            try {
                new UploadFileTask(DropboxClientFactory.getClient(), new Callback() {
                    @RequiresApi(api = Build.VERSION_CODES.O)
                    @Override
                    public void onTaskComplete(ArrayList<File> result) {
                        for (File file : result) {
                            addListDrive(file);
                            removeListPhone(file);
                        }
                        updateDataPhone();
                        updateDataDrive();
                    }

                    @Override
                    public void onError(Exception e) {
                        Logger.getLogger(Objects.requireNonNull(e.getMessage()));
                    }
                }).execute(files).get();

            } catch (ExecutionException | InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            }
        }
    }

    /**
     * Importe les dépenses sur l'appareil
     */
    private void download() {
        if (ACCESS_TOKEN == null) return;

        SparseBooleanArray checkItem = listFileDrive.getCheckedItemPositions();

        ArrayList<File> files = new ArrayList<>();

        if (checkItem != null) {
            for (int i = 0; i < checkItem.size(); i++) {
                if (checkItem.valueAt(i)) {
                    String item = listFileDrive.getAdapter().getItem(
                            checkItem.keyAt(i)).toString();

                    if (null != getFileValue(mapFileDrive, item, files))
                        files.add(getFileValue(mapFileDrive, item, files));
                }
            }
        }


        try {
            DownloadFileTask downloadFileTask = new DownloadFileTask(getApplicationContext(), DropboxClientFactory.getClient(), PATH, new Callback() {
                @RequiresApi(api = Build.VERSION_CODES.O)
                @Override
                public void onTaskComplete(ArrayList<File> result) {
                    for (File file : result) {
                        removeListDrive(file);
                        if (!"".equals(readFile(file))) {
                            addListPhone(file);
                        } else {
                            deleteFolder(file);
                        }
                    }
                    updateDataPhone();
                    updateDataDrive();
                }

                @Override
                public void onError(Exception e) {
                    Logger.getLogger(Objects.requireNonNull(e.getMessage()));
                }
            });
            downloadFileTask.execute(files).get();

        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
        }


    }

    /**
     * Récupère la clé de la map en fonction de la value
     *
     * @param map
     * @param value
     * @param files
     * @param <T>
     * @param <E>
     * @return
     */
    public static <T, E> File getFileValue(Map<T, E> map, E value, List<T> files) {

        File file = null;
        for (Map.Entry<T, E> entry : map.entrySet()) {
            if (Objects.equals(value, entry.getValue()) && !files.contains(entry.getKey())) {
                file = (File) entry.getKey();
                break;
            }
        }
        return file;
    }


    /**
     * Supprime un fichier
     *
     * @param folder Fichier
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        try {
            if (files != null) { //some JVMs return null for empty dirs

                for (File f : files) {
                    if (f.isDirectory()) {
                        deleteFolder(f);
                    } else {

                        Files.delete(f.toPath());

                    }
                }
            }
            Files.delete(folder.toPath());
        } catch (IOException e) {
            Logger.getLogger(Objects.requireNonNull(e.getMessage()));
        }

    }


    /**
     * Création d'une dépense
     */
    private void createFile() {

        if (!nomDepense.getText().toString().isEmpty() && !cout.getText().toString().isEmpty()) {
            List<String> lines = Collections.singletonList(nomDepense.getText().toString() + "|" + cout.getText().toString());
            Path pathFile;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String nomFichier = new Timestamp(System.currentTimeMillis()).toString();
                pathFile = Paths.get(PATH+ "/" + nomFichier.replace(":", " ") + ".txt");

                try {
                    Files.write(pathFile, lines, StandardCharsets.UTF_8);
                    File file = pathFile.toFile();
                    addListPhone(file);
                    updateDataPhone();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }


            Toast.makeText(MainActivity.this, "Une dépense a été créée !", Toast.LENGTH_LONG).show();
            nomDepense.setText("");
            cout.setText("");
        }

    }

    /**
     * Récupère tous les fichiers présents sur l'appareil
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void getFilesPhone() {

        Logger.getLogger("Permission granted");

        File[] files = PATH.listFiles();

        mapFilePhone.clear();

        if (files != null)
            Arrays.stream(files).forEach(this::addListPhone);

        updateDataPhone();
    }


    /**
     * Récupère et liste tous les fichiers présents sur le drive
     */
    private void getFilesDrive() {
        mapFileDrive.clear();

        try {

            ListDriveTask listDriveTask = new ListDriveTask(DropboxClientFactory.getClient(), PATH, new Callback() {
                @RequiresApi(api = Build.VERSION_CODES.O)
                @Override
                public void onTaskComplete(ArrayList<File> result) {
                    for (File file : result) {
                        if (readFile(file) != null) {
                            addListDrive(file);
                        }
                        deleteFolder(file);
                    }
                    updateDataDrive();
                }

                @Override
                public void onError(Exception e) {
                    Logger.getLogger(e.getMessage());
                }
            });
            listDriveTask.execute().get();

        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
    }

    /**
     * Lit le fichier txt
     *
     * @param fichier Fichier txt
     * @return
     */
    public static String readFile(File fichier) {
        String line;
        List<String> listeLignes = new ArrayList<>();

        // FileReader reads text files in the default encoding.
        // Always wrap FileReader in BufferedReader.
        try (FileReader fileReader =
                     new FileReader(fichier); BufferedReader bufferedReader =
                     new BufferedReader(fileReader)) {

            while ((line = bufferedReader.readLine()) != null) {
                listeLignes.add(line);
            }

        } catch (FileNotFoundException ex) {
            Logger.getLogger("Impossible d'ouvrir le fichier " + fichier);
        } catch (IOException ex) {
            Logger.getLogger("Impossible de lire le fichier " + fichier);
        }

        if (!listeLignes.isEmpty()) {
            return listeLignes.get(0);
        } else {
            return null;
        }
    }


    /**
     * Met à jour la liste affichée des dépenses de l'appareil
     */
    public void updateDataPhone() {

        ArrayAdapter<String> mAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, new ArrayList<>(mapFilePhone.values()));

        listFilePhone.setAdapter(mAdapter);
    }


    /**
     * Met à jour la liste affichée des dépenses du drive
     */
    public void updateDataDrive() {

        ArrayAdapter<String> mAdapterDrive = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, new ArrayList<>(mapFileDrive.values()));

        listFileDrive.setAdapter(mAdapterDrive);
    }

    /**
     * Vérifie l'existence du token
     *
     * @return
     */
    private boolean tokenExists() {
        SharedPreferences prefs = getSharedPreferences("com.app.gestiondepenses", Context.MODE_PRIVATE);
        String accessToken = prefs.getString("access-token", null);
        return accessToken != null;
    }

    /**
     * Vérifie si un token est déjà disponible sur l'appareil
     *
     * @return
     */
    private String retrieveAccessToken() {
        //check if ACCESS_TOKEN is stored on previous app launches
        SharedPreferences prefs = getSharedPreferences("com.app.gestiondepenses", Context.MODE_PRIVATE);
        String accessToken = prefs.getString("access-token", null);
        if (accessToken == null) {
            Log.d("AccessToken Status", "No token found");
            return null;
        } else {
            //accessToken already exists
            Log.d("AccessToken Status", "Token exists");
            return accessToken;
        }
    }

    /**
     * Demande l'autorisation pour écrire sur l'appareil
     */
    private void requestPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_PERMISSION);
    }

    /**
     * Indique si le réseau internet est disponible
     *
     * @return True si un accès réseau est disponible
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        Network nw = connectivityManager.getActiveNetwork();
        if (nw == null) return false;
        NetworkCapabilities actNw = connectivityManager.getNetworkCapabilities(nw);
        return actNw != null && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) || actNw.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH));
    }


    /**
     * Donne la possibilité de cliquer sur les boutons en fonctions du réseau et de la contenance des listes
     *
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void checkInternetAccess() {

        if (!isNetworkAvailable()) {
            accesInternet = false;
        } else {
            if (!accesInternet) {
                getFilesDrive();
                getFilesPhone();
            }
            accesInternet = true;

        }

        if (exportDepenses != null) {
            exportDepenses.setEnabled(accesInternet && areItemsChecked(listFilePhone) && isCliquable);

        }

        if (importDepenses != null) {
            importDepenses.setEnabled(accesInternet && areItemsChecked(listFileDrive) && isCliquable);

        }

        if (supprimerDepensesDrive != null) {
            supprimerDepensesDrive.setEnabled(accesInternet && areItemsChecked(listFileDrive) && isCliquable);

        }

        if (supprimerDepensesPhone != null) {
            supprimerDepensesPhone.setEnabled(areItemsChecked(listFilePhone) && isCliquable);

        }

        if (listFileDrive != null) {
            listFileDrive.setEnabled(accesInternet && isCliquable);

        }

        if (listFilePhone != null) {
            listFilePhone.setEnabled(isCliquable);

        }

        if (refreshListDrive != null) {
            refreshListDrive.setEnabled(accesInternet && isCliquable);

        }

        if (refreshListPhone != null) {
            refreshListPhone.setEnabled(isCliquable);

        }

        if (null != creerDepense) {
            creerDepense.setEnabled(isCliquable);
        }

    }

}

