package com.example.gestiondepenses;


import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

public class MainActivity<mainActivity> extends AppCompatActivity {

    private String ACCESS_TOKEN;
    private static final int REQUEST_PERMISSION = 1;
    private static final String path = Environment.getExternalStorageDirectory() + "/Comptes";

    private static Logger logger = Logger.getLogger("InfoLogging");

    private ArrayAdapter mAdapter = null;

    private ArrayAdapter mAdapterDrive = null;

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

    private HashMap<File, String> mapFilePhone = new HashMap<>();
    private HashMap<File, String> mapFileDrive = new HashMap<>();

    private Boolean accesInternet = true;
    private Boolean isCliquable = true;

    private ProgressBar progressBar;
    private final android.os.Handler handler = new android.os.Handler();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread thread = new Thread() {

            @Override
            public void run() {
                try {
                    while (!isInterrupted()) {
                        Thread.sleep(100);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                testButton();
                            }
                        });
                    }
                } catch (InterruptedException e) {
                }
            }
        };

        thread.start();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            logger.info("Permission not granted");
            //demande l'autorisation en écriture
            requestPermission();
            int i = 0;
            while (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED && i < 30) {
                try {
                    Thread.sleep(1000);
                    i++;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (i >= 30) {
                finish();
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            setContentView(R.layout.activity_main);

            if (!tokenExists()) {
                //No token
                //Back to LoginActivity
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);

                startActivity(intent);
            }

            ACCESS_TOKEN = retrieveAccessToken();

            nomDepense = findViewById(R.id.nomDepense);

            cout = findViewById(R.id.cout);

            listFilePhone = (ListView) findViewById(R.id.listViewPhone);

            supprimerDepensesPhone = (Button) findViewById(R.id.supprimerDepenses);
            creerDepense = (Button) findViewById(R.id.creerDepense);

            exportDepenses = findViewById(R.id.exportDepense);
            importDepenses = findViewById(R.id.importDepense);
            supprimerDepensesDrive = findViewById(R.id.supprimerDepensesDrive);
            listFileDrive = findViewById(R.id.listViewDrive);
            progressBar = findViewById(R.id.progressBar);
            refreshListPhone = (ImageButton) findViewById(R.id.refreshListPhone);
            refreshListDrive = (ImageButton) findViewById(R.id.refreshListDrive);


            getFilesPhone();
            getFilesDrive();
            creerDepense.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    isCliquable = false;
                    createFile();
                    isCliquable = true;
                }
            });

            exportDepenses.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    new Thread(new Runnable() {
                        public void run() {
                            isCliquable = false;

                            MainActivity.this.handler.post(new Runnable() {
                                public void run() {
                                    progressBar.setVisibility(View.VISIBLE);
                                }
                            });
                            upload();
                            MainActivity.this.handler.post(new Runnable() {
                                public void run() {
                                    progressBar.setVisibility(View.GONE);
                                    isCliquable = true;
                                }
                            });

                        }
                    }
                    ).start();

                }
            });

            supprimerDepensesPhone.setOnClickListener(new AdapterView.OnClickListener() {

                @Override
                public void onClick(View v) {
                 new Thread(new Runnable() {
                        public void run() {

                            MainActivity.this.handler.post(new Runnable() {
                                public void run() {
                                    isCliquable = false;

                                    progressBar.setVisibility(View.VISIBLE);
                                }
                            });
                            deletePhone();

                            MainActivity.this.handler.post(new Runnable() {
                                public void run() {
                                    progressBar.setVisibility(View.GONE);
                                    isCliquable = true;
                                    updateDataPhone();
                                    Toast.makeText(MainActivity.this, "Les dépenses sélectionnées ont été supprimées de l'appareil !", Toast.LENGTH_LONG).show();
                                }
                            });

                        }
                    }
                    ).start();

                }
            });


            importDepenses.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    new Thread(new Runnable() {
                        public void run() {


                            MainActivity.this.handler.post(new Runnable() {
                                public void run() {
                                    isCliquable = false;

                                    progressBar.setVisibility(View.VISIBLE);
                                }
                            });
                            download();
                            MainActivity.this.handler.post(new Runnable() {
                                public void run() {
                                    progressBar.setVisibility(View.GONE);
                                    isCliquable = true;

                                    Toast.makeText(MainActivity.this, "Les dépenses sélectionnées ont été importées dans l'appareil !", Toast.LENGTH_LONG).show();
                                }
                            });

                        }
                    }
                    ).start();

                }
            });

            supprimerDepensesDrive.setOnClickListener(new AdapterView.OnClickListener() {

                @Override
                public void onClick(View v) {
                    new Thread(new Runnable() {
                        public void run() {


                            MainActivity.this.handler.post(new Runnable() {
                                public void run() {
                                    isCliquable = false;

                                    progressBar.setVisibility(View.VISIBLE);
                                }
                            });
                            deleteDrive();
                            MainActivity.this.handler.post(new Runnable() {
                                public void run() {
                                    progressBar.setVisibility(View.GONE);
                                    isCliquable = true;

                                    Toast.makeText(MainActivity.this, "Les dépenses sélectionnées ont été supprimées du drive !", Toast.LENGTH_LONG).show();
                                }
                            });

                        }
                    }
                    ).start();

                }
            });
            refreshListPhone.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    isCliquable = false;
                    getFilesPhone();
                    isCliquable = true;
                }
            });

            refreshListDrive.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    new Thread(new Runnable() {
                        public void run() {


                            MainActivity.this.handler.post(new Runnable() {
                                public void run() {
                                    isCliquable = false;
                                    progressBar.setVisibility(View.VISIBLE);
                                }
                            });
                            getFilesDrive();
                            MainActivity.this.handler.post(new Runnable() {
                                public void run() {
                                    progressBar.setVisibility(View.GONE);
                                    isCliquable = true;
                                }
                            });

                        }
                    }
                    ).start();

                }
            });
        }

    }

    /**
     * Vérifie si les items sont sélectionnés dans les listes
     *
     * @param listView_p
     * @return
     */
    private boolean areItemsChecked(ListView listView_p) {

        SparseBooleanArray checkItem = listView_p.getCheckedItemPositions();

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

        if (files.size() > 0) {
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

            new DeleteFileTask(getApplicationContext(), DropboxClient.getClient(ACCESS_TOKEN), new DeleteFileTask.Callback() {
                @Override
                public void onDeleteComplete(Object o) {
                    if (o != null) {
                        ArrayList<File> result = (ArrayList<File>) o;
                        for (File file : result) {
                            removeListDrive(file);
                        }
                        updateDataDrive();
                    }

                }

                @Override
                public void onError(Exception e) {

                }
            }).execute(files).get();

        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (Exception e) {
            logger.info(e.getMessage());
        }

    }

    /**
     * Ajout du fichier texte de l'appareil dans la map
     *
     * @param file Fichier txt
     */
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

        if (files.size() > 0) {

            try {
                new UploadFileTask(DropboxClient.getClient(ACCESS_TOKEN), new UploadFileTask.Callback() {
                    @Override
                    public void onUploadComplete(Object o) {
                        ArrayList<File> result = (ArrayList<File>) o;
                        for (File file : result) {
                            addListDrive(file);
                            removeListPhone(file);
                        }
                        updateDataPhone();
                        updateDataDrive();
                    }

                    @Override
                    public void onError(Exception e) {

                    }
                }).execute(files).get();

            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (Exception e) {
                logger.info(e.getMessage());
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
            new DownloadFileTask(getApplicationContext(), DropboxClient.getClient(ACCESS_TOKEN), new DownloadFileTask.Callback() {
                @Override
                public void onDownloadComplete(Object o) {
                    if (o != null) {
                        ArrayList<File> result = (ArrayList<File>) o;
                        for (File file : result) {
                            removeListDrive(file);
                            if ("" != readFile(file)) {
                                addListPhone(file);
                            } else {
                                deleteFolder(file);
                            }
                        }
                        updateDataPhone();
                        updateDataDrive();
                    }
                }

                @Override
                public void onError(Exception e) {

                }
            }).execute(files).get();

        } catch (ExecutionException e) {
            logger.info(e.getMessage());
        } catch (InterruptedException e) {
            logger.info(e.getMessage());
        } catch (Exception e) {
            logger.info(e.getMessage());
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
    public static void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) { //some JVMs return null for empty dirs
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteFolder(f);
                } else {
                    f.delete();
                }
            }
        }
        folder.delete();
    }


    /**
     * Création d'une dépense
     */
    private void createFile() {

        if (!nomDepense.getText().toString().isEmpty() && !cout.getText().toString().isEmpty()) {
            List<String> lines = Arrays.asList(nomDepense.getText().toString() + "|" + cout.getText().toString());
            Path pathFile = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String nomFichier = new Timestamp(System.currentTimeMillis()).toString();
                pathFile = Paths.get(path + "/" + nomFichier.replaceAll(":", " ") + ".txt");

                try {
                    Files.write(pathFile, lines, Charset.forName("UTF-8"));
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
    private void getFilesPhone() {

        logger.info("Permission granted");

        File directory = new File(path);

        //si le dossier n'existe pas, il est créé
        if (!directory.exists())
            (new File(path)).mkdirs();

        File[] files = directory.listFiles();

        mapFilePhone.clear();

        for (int i = 0; i < files.length; i++) {
            addListPhone(files[i]);
        }

        updateDataPhone();
    }


    /**
     * Récupère et liste tous les fichiers présents sur le drive
     */
    private void getFilesDrive() {
        mapFileDrive.clear();

        try {

            new ListDriveTask(getApplicationContext(), DropboxClient.getClient(ACCESS_TOKEN), new ListDriveTask.Callback() {
                @Override
                public void onDownloadComplete(Object o) {
                    if (o != null) {
                        ArrayList<File> result = (ArrayList<File>) o;
                        for (File file : result) {
                            if (readFile(file) != null) {
                                addListDrive(file);
//                                updateDataDrive();
                            }
                            deleteFolder(file);
                        }
                    }
                    updateDataDrive();
                }

                @Override
                public void onError(Exception e) {

                }
            }).execute().get();

        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
    }

    /**
     * Lit le fichier txt
     *
     * @param fichier Fichier txt
     * @return
     */
    public static String readFile(File fichier) {
        String line = null;
        List<String> listeLignes = new ArrayList<>();

        try {
            // FileReader reads text files in the default encoding.
            FileReader fileReader =
                    new FileReader(fichier);

            // Always wrap FileReader in BufferedReader.
            BufferedReader bufferedReader =
                    new BufferedReader(fileReader);

            while ((line = bufferedReader.readLine()) != null) {
                listeLignes.add(line);
            }

            bufferedReader.close();
        } catch (FileNotFoundException ex) {
            logger.info("Impossible d'ouvrir le fichier " + fichier);
        } catch (IOException ex) {
            logger.info("Impossible de lire le fichier " + fichier);
        }

        if (listeLignes.size() > 0) {
            return listeLignes.get(0);
        } else {
            return null;
        }
    }


    /**
     * Met à jour la liste affichée des dépenses de l'appareil
     */
    public void updateDataPhone() {

        mAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_multiple_choice, new ArrayList<String>(mapFilePhone.values()));

        listFilePhone.setAdapter(mAdapter);
    }


    /**
     * Met à jour la liste affichée des dépenses du drive
     */
    public void updateDataDrive() {

        mAdapterDrive = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_multiple_choice, new ArrayList<String>(mapFileDrive.values()));

        listFileDrive.setAdapter(mAdapterDrive);
    }

    /**
     * Vérifie l'existence du token
     *
     * @return
     */
    private boolean tokenExists() {
        SharedPreferences prefs = getSharedPreferences("com.example.gestiondepenses", Context.MODE_PRIVATE);
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
        SharedPreferences prefs = getSharedPreferences("com.example.gestiondepenses", Context.MODE_PRIVATE);
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
     * Donne la possibilité de cliquer sur les boutons en fonctions du réseau et de la contenance des listes
     *
     * @return
     */
    public boolean testButton() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

        if (networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected()) {
            if (accesInternet == false) {
                getFilesDrive();
                getFilesPhone();
            }
            accesInternet = true;

        } else {
            accesInternet = false;
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

        return accesInternet;
    }

}

