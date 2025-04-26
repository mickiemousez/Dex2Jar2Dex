package com.goldenboot.d2j;
import com.googlecode.d2j.dex.Dex2jar;
import com.googlecode.dex2jar.tools.BaseCmd;
import com.googlecode.dex2jar.tools.Jar2Dex;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GoldenBoot {


    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage:");
            System.out.println("  d2j <input>(dex file/folder) <output_dir>(optional)");
            System.out.println("\n  j2d <input>(jar file/folder) <output>(file/folder;optional) <min-sdk>(optional; default is 13)");
            return;
        }

        String mode = args[0];

        try {
            if ("d2j".equals(mode)) {
                handleD2J(args);
            } else if ("j2d".equals(mode)) {
                handleJ2D(args);
            } else {
                System.out.println("Unknown command: " + mode);
                System.out.println("Usage:");
                System.out.println("  d2j <input>(dex file/folder) <output_dir>(optional)");
                System.out.println("\n  j2d <input>(jar file/folder) <output>(file/folder;optional) <min-sdk>(optional; default is 13)");
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    


    public static void handleD2J(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            System.out.println("Usage:\n  d2j <input>(dex file/folder) <output_dir>(optional)");
            return;
        }

        File input = new File(args[1]);
        if (!input.exists()) {
            System.out.println("\nInput \"" + args[1] + "\" not exist\n");
            return;
        }

        String outDir = args.length == 3 ? args[2] : null;

        if (input.isDirectory()) {
            List<String> files = listAllFiles(input.getAbsolutePath());
            for (String file : files) {
                if (file.endsWith(".dex")) {
                    System.out.println("Input: " + file);
                    if (outDir != null) {
                        Dex2jar.main(new String[]{file, outDir});
                    } else {
                        Dex2jar.main(new String[]{file});
                    }
                }
            }
        } else {
            if (outDir != null) {
                Dex2jar.main(new String[]{args[1], outDir});
            } else {
                Dex2jar.main(new String[]{args[1]});
            }
        }
    }
    
    
    
    
    
    public static void handleJ2D(String[] args) throws Exception {
        /*
        if (args.length < 2 || args.length > 4) {
            System.out.println("Usage:\n  j2d <input.jar>(file/folder) <output.dex>(optional) <min-sdk>(optional; default is 13)");
            return;
        }
        */
        File input = new File(args[1]);
        if (!input.exists()) {
            System.out.println("\nInput \"" + args[1] + "\" not exist\n");
            return;
        }

        String minSdk = args.length == 4 ? args[3] : (args.length == 3 && args[2].matches("\\d+") ? args[2] : "13");
        try {
            Integer.parseInt(minSdk);
        } catch (Exception e) {
            System.out.println("min sdk should be an integer");
            return;
        }

        String outputBase;
        if (args.length >= 3 && !args[2].matches("\\d+")) {
            outputBase = args[2];
        } else {
            outputBase = input.isDirectory()
                ? new File(input, "output").getAbsolutePath()
                : new File(input.getParentFile(), "output").getAbsolutePath();
        }

        if (input.isDirectory()) {
            List<String> files = listAllFiles(input.getAbsolutePath());
            for (String file : files) {
                if (file.endsWith(".jar")) {
                    String fileName = new File(file).getName();
                    String baseName = fileName.substring(0, fileName.length() - 4);
                    String outDex = outputBase + File.separator + baseName;
                    new File(outputBase).mkdirs();
                    Jar2Dex.main(new String[]{"-o", outDex, file, "-s", minSdk});
                }
            }
        } else {
            new File(outputBase).mkdirs();
            String outDex = outputBase + File.separator + input.getName().replace(".jar", ".dex");
            Jar2Dex.main(new String[]{"-o", outDex, input.getAbsolutePath(), "-s", minSdk});
        }
    }
    
    
    
    
    
    
    public static List<String> listAllFiles(String path) {
        final List<String> allFiles = new ArrayList<String>();
        Path startPath = Paths.get(path);

        try {
            Files.walkFileTree(startPath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        allFiles.add(file.toString().trim());
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });
        } catch (IOException e) {
            System.out.println(e.toString());
        }

        return allFiles;
    }




}

