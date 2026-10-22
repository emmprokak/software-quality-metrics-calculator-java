package com.unipi.mns;

import java.io.File;
import java.util.List;

public class DirectoryTraversal {

    public static void findJavaFiles(File directory, List<File> javaFiles) {
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    findJavaFiles(file, javaFiles);
                } else if (file.getName().endsWith(".java")) {
                    javaFiles.add(file);
                }
            }
        }
    }
}
