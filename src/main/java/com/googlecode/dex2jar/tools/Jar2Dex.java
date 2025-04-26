package com.googlecode.dex2jar.tools;

import com.android.dx.command.Main;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@BaseCmd.Syntax(cmd = "d2j-jar2dex", syntax = "[options] <dir>", desc = "Convert jar to dex by invoking dx.")
public class Jar2Dex extends BaseCmd {

    
    public static void main(String... args) {
        new Jar2Dex().doMain(args);
    }

    @Opt(opt = "f", longOpt = "force", hasArg = false, description = "force overwrite")
    private boolean forceOverwrite = false;

    @Opt(opt = "s", longOpt = "sdk", description = "set minSdkVersion")
    private int minSdkVersion = 13;

    @Opt(opt = "d", longOpt = "debug", description = "debug output")
    private boolean debug = false;

    @Opt(opt = "v", longOpt = "verbose", description = "verbose output")
    private boolean verbose = false;

    @Opt(opt = "o", longOpt = "output", description = "output .dex file, default is $current_dir/[jar-name]-jar2dex.dex", argName = "out-dex-file")
    private File output;

    @Override
    protected void doCommandLine() throws Exception {
        if (remainingArgs.length != 1) {
            usage();
            return;
        }

        File jar = new File(remainingArgs[0]);
        if (!jar.exists()) {
            System.err.println(jar + " doesn't exist");
            usage();
            return;
        }

        if (output == null) {
            String baseName = getBaseName(jar.getName());
            output = new File(baseName + "-jar2dex.dex");
        }

        if (output.exists()) {
            output.delete();
        }

        File tmp = null;
        final File realJar;
        try {
            if (jar.isDirectory()) {
                realJar = File.createTempFile("d2j", ".jar");
                tmp = realJar;
                System.out.println("zipping " + jar + " -> " + realJar);
                zipDirToJar(jar, realJar);
            } else {
                realJar = jar;
            }

            System.out.println("jar2dex " + realJar + " -> " + output);

            List<String> ps = new ArrayList<String>(Arrays.asList(
                                                        "--dex", "--no-strict",
                                                        "--output=" + output.getAbsolutePath(),
                                                        "--min-sdk-version=" + minSdkVersion
                                                    ));
            if (verbose) ps.add("--verbose");
            if (debug) ps.add("--debug");
            ps.add(realJar.getAbsolutePath());

            //System.out.println("call com.android.dx.command.Main.main " + ps);
            System.out.println("Minimum SDK: " + minSdkVersion);
            
            Main.main(ps.toArray(new String[ps.size()]));
        } catch(Exception e){
            //Toast.makeText(MainActivity.ctx, e.toString(), Toast.LENGTH_LONG).show();
            System.out.print("\nerror on doCommandLine (Jar2dex): "+e.toString());
        } finally {
            if (tmp != null && tmp.exists()) {
                tmp.delete();
            }
        }
    }

    private void zipDirToJar(File sourceDir, File jarFile) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(jarFile));
        zipFilesRecursive(sourceDir, sourceDir, zos);
        zos.close();
    }

    private void zipFilesRecursive(File rootDir, File src, ZipOutputStream zos) throws IOException {
        if (src.isDirectory()) {
            File[] files = src.listFiles();
            if (files != null) {
                for (File f : files) {
                    zipFilesRecursive(rootDir, f, zos);
                }
            }
        } else if (src.getName().endsWith(".class")) {
            String name = rootDir.toURI().relativize(src.toURI()).getPath();
            ZipEntry entry = new ZipEntry(name);
            zos.putNextEntry(entry);
            FileInputStream fis = new FileInputStream(src);
            byte[] buffer = new byte[4096];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                zos.write(buffer, 0, len);
            }
            fis.close();
            zos.closeEntry();
        }
    }

    public static String getBaseName(String filename) {
        int index = filename.lastIndexOf('.');
        if (index == -1) return filename;
        return filename.substring(0, index);
    }
}

