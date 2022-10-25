package org.example;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class Main {
    static void usage() {
        System.err.println("usage: java -jar WatchDir.jar [-r] dir destination");
        System.exit(-1);
    }

    public static void main(String[] args) {
        if (args.length == 0 || args.length > 3) {
            usage();
        }
        boolean recursive = false;

        int dirArg = 0;
        if (args[0].equals("-r")) {
            if (args.length < 2)
                usage();
            recursive = true;
            dirArg++;
        }
        System.out.println("rec="+recursive);
        String sourceStr = args[dirArg];
        String destStr = args[dirArg + 1];
        System.out.println("source="+sourceStr);
        System.out.println("targ="+destStr);
        File source = new File(sourceStr);
        File destination = new File(destStr);
        int sourceCount = source.toPath().getNameCount();
        try {
            FileUtil.copyDirectory(source, destination);
            new WatchDir(source.toPath(), recursive)
                .watchDirectoryPath(sourceCount, destination.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}