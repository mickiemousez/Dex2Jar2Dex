package com.googlecode.d2j.dex;

import com.googlecode.d2j.converter.IR2JConverter;
import com.googlecode.d2j.node.DexClassNode;
import com.googlecode.d2j.node.DexFileNode;
import com.googlecode.d2j.node.DexMethodNode;
import com.googlecode.d2j.reader.BaseDexFileReader;
import com.googlecode.d2j.reader.DexFileReader;
import com.googlecode.d2j.reader.MultiDexFileReader;
import com.googlecode.dex2jar.ir.IrMethod;
import com.googlecode.dex2jar.tools.Constants;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

public final class Dex2jar {

    
    /**
     * For rather deterministic output, we use a fixed seed for random number generator.
     * This field is freely writable by any thread. Use carefully.
     */
    public static Random random = new Random(0);

    private DexExceptionHandler exceptionHandler;
    
    public static File input;

    private final BaseDexFileReader reader;

    private int readerConfig;

    private int v3Config;

    private Dex2jar(BaseDexFileReader reader) {
        super();
        this.reader = reader;
        readerConfig |= DexFileReader.SKIP_DEBUG;
    }
    
    
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            //System.err.println("Usage: java -jar dex2jar.jar <input.dex> <output-dir>");
            //System.exit(1);
            File inputFile = new File(args[0]);
            Path outputDir = new File(inputFile.getParentFile().getParentFile(), "output").toPath();
            from(inputFile).to(outputDir);
            return;
        } else if(args.length < 3){
            File inputFile = new File(args[0]);
            Path outputDir = Paths.get(args[1]);
            from(inputFile).to(outputDir);
            return;
        } else{
            System.err.println("Usage: java -jar dex2jar.jar <input.dex> <output-dir>(output dir is optional)");
            return;
        }
        
    }
    
    
    
    

    public void doTranslate(final Path dist) {
        doTranslate(dist, null);
    }

    public void doTranslate(final ByteArrayOutputStream baos) {
        doTranslate(null, baos);
    }

    private static String toInternalClassName(String key) {
        if (key.endsWith(";")) key = key.substring(1, key.length() - 1);
        return key;
    }

    
    
    /**
     * Translates a dex file to a class file and writes it to the specified destination path and stream.
     *
     * @param dist The destination path where the translated class file should be written, or {@code null} if unwanted.
     * @param baos An output stream used for intermediate data storage, or {@code null} if unwanted.
     */
    public void doTranslate(final Path dist, final ByteArrayOutputStream baos) {

        DexFileNode fileNode = new DexFileNode();
        try {
            reader.accept(fileNode, readerConfig | DexFileReader.IGNORE_READ_EXCEPTION);
        } catch (Exception ex) {
            exceptionHandler.handleFileException(ex);
        }

        final Map<String, String> parentsByName = new HashMap<String, String>();

        for (DexClassNode c : fileNode.clzs) {
            if (c.superClass != null) {
                parentsByName.put(toInternalClassName(c.className), toInternalClassName(c.superClass));
            }
        }

        final Map<String, byte[]> allClasses = new HashMap<>();

        ClassVisitorFactory cvf = new ClassVisitorFactory() {
            @Override
            public ClassVisitor create(final String name) {
                final ClassWriter cw = (readerConfig & DexFileReader.COMPUTE_FRAMES) == 0
                    ? new ClassWriter(ClassWriter.COMPUTE_MAXS)
                    : new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
                    @Override
                    protected String getCommonSuperClass(String type1, String type2) {
                        if (type1.equals(type2)) return type1;

                        Set<String> parentsOfType1 = new HashSet<>();
                        parentsOfType1.add(type1);
                        while (parentsByName.containsKey(type1)) {
                            type1 = parentsByName.get(type1);
                            parentsOfType1.add(type1);
                        }

                        while (parentsByName.containsKey(type2)) {
                            type2 = parentsByName.get(type2);
                            if (parentsOfType1.contains(type2)) return type2;
                        }

                        try {
                            return super.getCommonSuperClass(type1, type2);
                        } catch (Throwable t) {
                            return "java/util/Object";
                        }
                    }
                };

                final LambadaNameSafeClassAdapter rca = new LambadaNameSafeClassAdapter(cw,
                         (readerConfig & DexFileReader.DONT_SANITIZE_NAMES) != 0);

                return new ClassVisitor(Constants.ASM_VERSION, rca) {
                    @Override
                    public void visitEnd() {
                        super.visitEnd();
                        String className = rca.getClassName();
                        try {
                            byte[] data = cw.toByteArray();
                            allClasses.put(className + ".class", data);
                        } catch (Exception ex) {
                            System.err.printf("ASM failed to generate .class file: %s%n", className);
                            exceptionHandler.handleFileException(ex);
                        }
                    }
                };
            }
        };

        new ExDex2Asm(exceptionHandler) {
            public void convertCode(DexMethodNode methodNode, MethodVisitor mv, ClzCtx clzCtx) {
                if ((readerConfig & DexFileReader.SKIP_CODE) != 0 && methodNode.method.getName().equals("<clinit>")) {
                    return;
                }
                super.convertCode(methodNode, mv, clzCtx);
            }

            @Override
            public void optimize(IrMethod irMethod) {
                T_CLEAN_LABEL.transform(irMethod);
                T_DEAD_CODE.transform(irMethod);
                T_REMOVE_LOCAL.transform(irMethod);
                T_REMOVE_CONST.transform(irMethod);
                T_ZERO.transform(irMethod);
                if (T_NPE.transformReportChanged(irMethod)) {
                    T_DEAD_CODE.transform(irMethod);
                    T_REMOVE_LOCAL.transform(irMethod);
                    T_REMOVE_CONST.transform(irMethod);
                }
                T_NEW.transform(irMethod);
                T_FILL_ARRAY.transform(irMethod);
                T_AGG.transform(irMethod);
                T_MULTI_ARRAY.transform(irMethod);
                T_VOID_INVOKE.transform(irMethod);
                T_DEAD_CODE.transform(irMethod);
                T_REMOVE_LOCAL.transform(irMethod);
                T_REMOVE_CONST.transform(irMethod);
                T_TYPE.transform(irMethod);
                T_UNSSA.transform(irMethod);
                T_IR_2_J_REG_ASSIGN.transform(irMethod);
                T_TRIM_EX.transform(irMethod);
            }

            @Override
            public void ir2j(IrMethod irMethod, MethodVisitor mv, ClzCtx clzCtx) {
                new IR2JConverter()
                    .optimizeSynchronized(0 != (V3.OPTIMIZE_SYNCHRONIZED & v3Config))
                    .clzCtx(clzCtx)
                    .ir(irMethod)
                    .asm(mv)
                    .convert();
            }
        }.convertDex(fileNode, cvf);

        // Write all classes to zip
        if (dist != null) {
            try {
                OutputStream fos = Files.newOutputStream(dist);
                ZipOutputStream zos = new ZipOutputStream(fos);

                int totalClasses = allClasses.size();  // Total classes to write
                int writtenClasses = 0;

                for (Map.Entry<String, byte[]> entry : allClasses.entrySet()) {
                    String name = entry.getKey();
                    byte[] data = entry.getValue();

                    ZipEntry zipEntry = new ZipEntry(name);
                    zos.putNextEntry(zipEntry);
                    zos.write(data);
                    zos.closeEntry();

                    // Update progress
                    writtenClasses++;
                    int progress = (writtenClasses * 100) / totalClasses;
                    System.out.print("\r\033[32mProcessing: " + progress + "% (" + writtenClasses + "/" + totalClasses + ")\033[0m");
                }

                zos.close();
                System.out.println("\nOutput: " + dist.toAbsolutePath()+"\n");

            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
        }
    }
    
    public DexExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    public BaseDexFileReader getReader() {
        return reader;
    }

    public Dex2jar reUseReg(boolean b) {
        if (b) {
            this.v3Config |= V3.REUSE_REGISTER;
        } else {
            this.v3Config &= ~V3.REUSE_REGISTER;
        }
        return this;
    }

    public Dex2jar topoLogicalSort(boolean b) {
        if (b) {
            this.v3Config |= V3.TOPOLOGICAL_SORT;
        } else {
            this.v3Config &= ~V3.TOPOLOGICAL_SORT;
        }
        return this;
    }

    public Dex2jar noCode(boolean b) {
        if (b) {
            this.readerConfig |= DexFileReader.SKIP_CODE | DexFileReader.KEEP_CLINIT;
        } else {
            this.readerConfig &= ~(DexFileReader.SKIP_CODE | DexFileReader.KEEP_CLINIT);
        }
        return this;
    }

    public Dex2jar optimizeSynchronized(boolean b) {
        if (b) {
            this.v3Config |= V3.OPTIMIZE_SYNCHRONIZED;
        } else {
            this.v3Config &= ~V3.OPTIMIZE_SYNCHRONIZED;
        }
        return this;
    }

    public Dex2jar printIR(boolean b) {
        if (b) {
            this.v3Config |= V3.PRINT_IR;
        } else {
            this.v3Config &= ~V3.PRINT_IR;
        }
        return this;
    }

    public Dex2jar reUseReg() {
        this.v3Config |= V3.REUSE_REGISTER;
        return this;
    }

    public Dex2jar optimizeSynchronized() {
        this.v3Config |= V3.OPTIMIZE_SYNCHRONIZED;
        return this;
    }

    public Dex2jar printIR() {
        this.v3Config |= V3.PRINT_IR;
        return this;
    }

    public Dex2jar topoLogicalSort() {
        this.v3Config |= V3.TOPOLOGICAL_SORT;
        return this;
    }

    public void setExceptionHandler(DexExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public Dex2jar skipDebug(boolean b) {
        if (b) {
            this.readerConfig |= DexFileReader.SKIP_DEBUG;
        } else {
            this.readerConfig &= ~DexFileReader.SKIP_DEBUG;
        }
        return this;
    }

    public Dex2jar skipDebug() {
        this.readerConfig |= DexFileReader.SKIP_DEBUG;
        return this;
    }

    public void to(Path file) {
        if(!Files.exists(file)){
            file.toFile().mkdirs();
        }
        File temp = new File(file.toFile(), input.getName()+".jar");

        if (Files.exists(file) && Files.isDirectory(file)) {
            try{
                dontSanitizeNames(true);
                doTranslate(temp.toPath());
            } finally{
                //createZip(temp, file.toFile());
            }
        }
    }
    
    public Dex2jar withExceptionHandler(DexExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
        return this;
    }

    public Dex2jar skipExceptions(boolean b) {
        if (b) {
            this.readerConfig |= DexFileReader.SKIP_EXCEPTION;
        } else {
            this.readerConfig &= ~DexFileReader.SKIP_EXCEPTION;
        }
        return this;
    }

    public Dex2jar dontSanitizeNames(boolean b) {
        if (b) {
            this.readerConfig |= DexFileReader.DONT_SANITIZE_NAMES;
        } else {
            this.readerConfig &= ~DexFileReader.DONT_SANITIZE_NAMES;
        }
        return this;
    }

    public Dex2jar computeFrames(boolean b) {
        if (b) {
            this.readerConfig |= DexFileReader.COMPUTE_FRAMES;
        } else {
            this.readerConfig &= ~DexFileReader.COMPUTE_FRAMES;
        }
        return this;
    }

    public Dex2jar setRandom(Random random) {
        Dex2jar.random = random;
        return this;
    }

    public Dex2jar resetRandom() {
        return setRandom(new Random(0));
    }

    public static Dex2jar from(byte[] in) throws IOException {
        return from(MultiDexFileReader.open(in));
    }

    public static Dex2jar from(ByteBuffer in) throws IOException {
        return from(MultiDexFileReader.open(in.array()));
    }

    public static Dex2jar from(BaseDexFileReader reader) {
        return new Dex2jar(reader);
    }

    public static Dex2jar from(File in) throws IOException {
        input = in;
        return from(Files.readAllBytes(in.toPath()));
    }

    public static Dex2jar from(InputStream in) throws IOException {
        return from(MultiDexFileReader.open(in));
    }

    public static Dex2jar from(String in) throws IOException {
        return from(new File(in));
    }

}
