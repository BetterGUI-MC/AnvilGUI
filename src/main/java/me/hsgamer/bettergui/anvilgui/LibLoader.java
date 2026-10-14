package me.hsgamer.bettergui.anvilgui;

import me.hsgamer.hscore.logger.common.LogLevel;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import sun.misc.Unsafe;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

public class LibLoader {
    private static final String REPOSITORY = "https://mvn.wesjd.net/";
    private static final String GROUP_PATH = "net/wesjd";
    private static final String ARTIFACT = "anvilgui";
    private static final String JAR_PREFIX = ARTIFACT + "-";
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 30000;

    private final Main main;
    private final Path saveDirectory;
    private final MethodHandle addUrl;
    private final Function<List<Path>, List<Path>> REMAPPER;

    public LibLoader(Main main) throws Exception {
        this.main = main;
        this.saveDirectory = main.getDataFolder().toPath().resolve("lib");
        this.addUrl = addUrlHandle(main.getExpansionClassLoader());

        Function<List<Path>, List<Path>> remapper;
        try {
            Class<?> clazz = Class.forName("org.bukkit.plugin.java.LibraryLoader");
            Field remapperField = clazz.getDeclaredField("REMAPPER");

            //noinspection unchecked
            remapper = (Function<List<Path>, List<Path>>) remapperField.get(null);
        } catch (Throwable e) {
            // no remapper available (older servers or missing dependencies)
            remapper = null;
        }
        REMAPPER = remapper;
    }

    private static URLConnection open(String path) throws IOException {
        URLConnection connection = new URL(REPOSITORY + path).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        // GitHub Pages rejects the default Java user agent
        connection.setRequestProperty("User-Agent", "BetterGUI-AnvilGUI");
        return connection;
    }

    private static Document fetchXml(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (InputStream inputStream = open(path).getInputStream()) {
            return factory.newDocumentBuilder().parse(inputStream);
        }
    }

    private static String childText(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    private static String latestVersion() throws Exception {
        NodeList nodes = fetchXml(GROUP_PATH + "/" + ARTIFACT + "/maven-metadata.xml").getElementsByTagName("version");
        String latest = null;
        for (int i = 0; i < nodes.getLength(); i++) {
            String version = nodes.item(i).getTextContent().trim();
            if (latest == null || compareVersions(version, latest) > 0) {
                latest = version;
            }
        }
        if (latest == null) {
            throw new IllegalStateException("Cannot find any version of " + ARTIFACT);
        }
        return latest;
    }

    private static int compareVersions(String left, String right) {
        String[] leftParts = left.split("[.-]");
        String[] rightParts = right.split("[.-]");
        for (int i = 0; i < Math.max(leftParts.length, rightParts.length); i++) {
            int leftPart = i < leftParts.length ? toNumber(leftParts[i]) : 0;
            int rightPart = i < rightParts.length ? toNumber(rightParts[i]) : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }

    private static int toNumber(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String jarName(String version) throws Exception {
        if (!version.endsWith("-SNAPSHOT")) {
            return ARTIFACT + "-" + version + ".jar";
        }
        NodeList nodes = fetchXml(GROUP_PATH + "/" + ARTIFACT + "/" + version + "/maven-metadata.xml").getElementsByTagName("snapshotVersion");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if ("jar".equals(childText(element, "extension"))) {
                return ARTIFACT + "-" + childText(element, "value") + ".jar";
            }
        }
        throw new IllegalStateException("Cannot find the jar of " + version);
    }

    private static String fetchChecksum(String path) throws IOException {
        try (InputStream inputStream = open(path + ".sha1").getInputStream()) {
            // the checksum file may also contain the file name
            return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).readLine().trim().split("\\s+")[0];
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    private static String sha1(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (InputStream inputStream = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder builder = new StringBuilder();
        for (byte b : digest.digest()) {
            builder.append(String.format("%02x", b & 0xff));
        }
        return builder.toString();
    }

    private void download(String version, Path target) throws IOException {
        String path = GROUP_PATH + "/" + ARTIFACT + "/" + version + "/" + target.getFileName();
        Path temp = Files.createTempFile(target.getParent(), JAR_PREFIX, ".tmp");
        try {
            try (InputStream inputStream = open(path).getInputStream()) {
                Files.copy(inputStream, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            String expected = fetchChecksum(path);
            if (expected == null) {
                main.getLogger().log(LogLevel.WARN, "No checksum published for " + target.getFileName() + ". Skipping the verification");
            } else {
                String actual = sha1(temp);
                if (!actual.equalsIgnoreCase(expected)) {
                    throw new IOException("Checksum mismatch for " + target.getFileName() + ": expected " + expected + " but got " + actual);
                }
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static boolean isLibrary(Path path) {
        String name = path.getFileName().toString();
        return name.startsWith(JAR_PREFIX) && name.endsWith(".jar");
    }

    // java.net is not open to unnamed modules on Java 9+, so the protected URLClassLoader#addURL
    // can only be reached through a trusted lookup obtained via Unsafe
    private static MethodHandle addUrlHandle(URLClassLoader classLoader) throws Exception {
        Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        try {
            method.setAccessible(true);
            return MethodHandles.lookup().unreflect(method).bindTo(classLoader);
        } catch (RuntimeException e) {
            Field implLookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) theUnsafeField.get(null);
            MethodHandles.Lookup trusted = (MethodHandles.Lookup) unsafe.getObject(
                    unsafe.staticFieldBase(implLookupField),
                    unsafe.staticFieldOffset(implLookupField)
            );
            return trusted.findVirtual(URLClassLoader.class, "addURL", MethodType.methodType(void.class, URL.class)).bindTo(classLoader);
        }
    }

    private Path newestLocalJar() {
        try (Stream<Path> stream = Files.list(saveDirectory)) {
            return stream
                    .filter(LibLoader::isLibrary)
                    .max(Comparator.comparingLong(path -> path.toFile().lastModified()))
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private void deleteStaleJars(Path kept) {
        try (Stream<Path> stream = Files.list(saveDirectory)) {
            stream.filter(LibLoader::isLibrary).filter(path -> !path.equals(kept)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // the file is probably still in use
                }
            });
        } catch (IOException ignored) {
            // EMPTY
        }
    }

    private void addToClasspath(Path file) throws IOException {
        try {
            addUrl.invoke(file.toUri().toURL());
        } catch (Throwable e) {
            throw new IllegalStateException("Cannot add " + file.getFileName() + " to the classpath", e);
        }
    }

    private Path remap(Path file) {
        return REMAPPER != null ? REMAPPER.apply(Collections.singletonList(file)).get(0) : file;
    }

    // This method's only purpose is to add the "paperweight-mappings-namespace" attribute to the MANIFEST.MF file of the library
    // So that it can be remapped by Paper's PluginRemapper
    private void modifyManifest(Path file) throws IOException {
        Path tempJarPath = Files.createTempFile("temp-", ".jar");

        try (JarFile jarFile = new JarFile(file.toFile());
             JarOutputStream tempJarOutputStream = new JarOutputStream(Files.newOutputStream(tempJarPath))) {

            // Copy existing entries
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (JarFile.MANIFEST_NAME.equals(entry.getName())) {
                    continue; // Skip the existing MANIFEST.MF
                }
                try (InputStream entryInputStream = jarFile.getInputStream(entry)) {
                    tempJarOutputStream.putNextEntry(new JarEntry(entry.getName()));
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = entryInputStream.read(buffer)) != -1) {
                        tempJarOutputStream.write(buffer, 0, bytesRead);
                    }
                    tempJarOutputStream.closeEntry();
                }
            }

            // Modify the MANIFEST.MF
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) {
                manifest = new Manifest();
            }
            manifest.getMainAttributes().putValue("paperweight-mappings-namespace", "spigot");

            // Write the modified MANIFEST.MF
            tempJarOutputStream.putNextEntry(new JarEntry(JarFile.MANIFEST_NAME));
            manifest.write(tempJarOutputStream);
            tempJarOutputStream.closeEntry();
        }

        Files.move(tempJarPath, file, StandardCopyOption.REPLACE_EXISTING);
    }

    public void loadLibrary() throws Exception {
        Path jar;
        try {
            String version = latestVersion();
            Path target = saveDirectory.resolve(jarName(version));
            if (Files.exists(target)) {
                main.getLogger().log(LogLevel.INFO, "Using the downloaded AnvilGUI " + target.getFileName());
            } else {
                main.getLogger().log(LogLevel.INFO, "Downloading AnvilGUI " + target.getFileName() + " from " + REPOSITORY);
                Files.createDirectories(saveDirectory);
                download(version, target);
                if (REMAPPER != null) {
                    modifyManifest(target);
                }
            }
            jar = target;
        } catch (Exception e) {
            jar = newestLocalJar();
            if (jar == null) {
                throw e;
            }
            main.getLogger().log(LogLevel.WARN, "Cannot fetch the latest AnvilGUI. Falling back to " + jar.getFileName(), e);
        }

        deleteStaleJars(jar);
        addToClasspath(remap(jar));
    }
}
