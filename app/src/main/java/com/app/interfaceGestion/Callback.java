package com.app.interfaceGestion;

import java.io.File;
import java.util.ArrayList;

public interface Callback {

    void onTaskComplete(ArrayList<File> result);

    void onError(Exception e);

}
