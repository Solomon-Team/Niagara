package me.ayydxn.niagara.utils;

import me.ayydxn.luminescence.internal.UltralightNativeLoader;
import me.ayydxn.niagara.NiagaraClientMod;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class NativeLibraryLoader
{
    private static final String ULTRALIGHT_SDK_VERSION = "1.4.0";
    private static final String SENTINEL_FILE = "ultralight-sdk-" + ULTRALIGHT_SDK_VERSION + ".installed";
    private static final String RELEASE_BASE_URL = "https://github.com/Ayydxn/Voxellight-Ultralight-SDKs/releases/download/" + ULTRALIGHT_SDK_VERSION + "/";

    private NativeLibraryLoader()
    {
    }

    public static void load()
    {
        ensureDirectoryExists(NiagaraPaths.NIAGARA_DIRECTORY);
        ensureDirectoryExists(NiagaraPaths.NIAGARA_RESOURCES_DIRECTORY);

        if (!Files.exists(NiagaraPaths.NIAGARA_DIRECTORY.resolve(SENTINEL_FILE)))
        {
            downloadAndExtractSDK();
            extractBundledResources();
            writeSentinel();
        }
        else
        {
            extractBundledResources();
        }

        loadNatives();
    }

    private static void ensureDirectoryExists(Path directory)
    {
        try
        {
            Files.createDirectories(directory);
        }
        catch (IOException exception)
        {
            throw new RuntimeException("Niagara: Failed to create Ultralight directory: " + directory, exception);
        }
    }

    private static void downloadAndExtractSDK()
    {
        String archiveName = resolveArchiveName();
        String url = RELEASE_BASE_URL + archiveName;
        Path archivePath = NiagaraPaths.NIAGARA_DIRECTORY.resolve(archiveName);

        NiagaraClientMod.LOGGER.info("Downloading Ultralight SDK from {}", url);

        try
        {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(archivePath));
            if (response.statusCode() != 200)
                throw new RuntimeException("Niagara: SDK download failed with HTTP " + response.statusCode() + " from " + url);
        }
        catch (IOException | InterruptedException exception)
        {
            throw new RuntimeException("Niagara: Failed to download Ultralight SDK from " + url, exception);
        }

        NiagaraClientMod.LOGGER.info("Extracting Ultralight SDK...");

        extractSevenZip(archivePath);

        try
        {
            Files.deleteIfExists(archivePath);
        }
        catch (IOException exception)
        {
            NiagaraClientMod.LOGGER.warn("Failed to delete SDK archive after extraction", exception);
        }

        NiagaraClientMod.LOGGER.info("Ultralight SDK installed successfully");
    }

    private static void extractSevenZip(Path archivePath)
    {
        String extension = resolveNativeExtension();

        try (SevenZFile sevenZFile = new SevenZFile(archivePath.toFile()))
        {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null)
            {
                if (entry.isDirectory())
                    continue;

                String name = Path.of(entry.getName()).getFileName().toString();
                if (!isNativeLibrary(name, extension))
                    continue;

                Path outPath = NiagaraPaths.NIAGARA_DIRECTORY.resolve(name);
                Files.createDirectories(outPath.getParent());

                try (OutputStream outputStream = Files.newOutputStream(outPath))
                {
                    byte[] buf = new byte[8192];
                    int len;
                    InputStream stream = sevenZFile.getInputStream(entry);

                    while ((len = stream.read(buf)) != -1)
                        outputStream.write(buf, 0, len);
                }
            }
        }
        catch (IOException exception)
        {
            throw new RuntimeException("Niagara: Failed to extract Ultralight native libraries", exception);
        }
    }

    private static void extractBundledResources()
    {
        extractResource("assets/niagara/ultralight/icudt67l.dat", NiagaraPaths.NIAGARA_RESOURCES_DIRECTORY.resolve("icudt67l.dat"));
        extractResource("assets/niagara/ultralight/cacert.pem", NiagaraPaths.NIAGARA_RESOURCES_DIRECTORY.resolve("cacert.pem"));
    }

    private static void extractResource(String resourcePath, Path targetPath)
    {
        if (Files.exists(targetPath))
            return;

        try (InputStream inputStream = NativeLibraryLoader.class.getClassLoader().getResourceAsStream(resourcePath))
        {
            if (inputStream == null)
                throw new RuntimeException("Niagara: Bundled resource not found: " + resourcePath);

            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException exception)
        {
            throw new RuntimeException("Niagara: Failed to extract bundled resource: " + resourcePath, exception);
        }
    }

    private static void writeSentinel()
    {
        try
        {
            Files.createFile(NiagaraPaths.NIAGARA_DIRECTORY.resolve(SENTINEL_FILE));
        }
        catch (IOException e)
        {
            throw new RuntimeException("Niagara: Failed to write SDK sentinel file", e);
        }
    }

    private static void loadNatives()
    {
        NiagaraClientMod.LOGGER.info("Loading Ultralight natives from {}", NiagaraPaths.NIAGARA_DIRECTORY);

        try
        {
            UltralightNativeLoader.load(NiagaraPaths.NIAGARA_DIRECTORY);
        }
        catch (Exception exception)
        {
            throw new RuntimeException("Niagara: Failed to load Ultralight native libraries", exception);
        }
    }

    private static String resolveArchiveName()
    {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        String baseArchiveName = "ultralight-free-sdk-" + ULTRALIGHT_SDK_VERSION + "-";

        boolean isWindows = os.contains("win");
        boolean isMac = os.contains("mac") || os.contains("darwin");
        boolean isLinux = os.contains("linux");
        boolean isArm64 = arch.contains("aarch64") || arch.contains("arm64");

        if (isWindows)
        {
            if (isArm64)
                throw unsupportedPlatform(os, arch);

            return baseArchiveName + "win-x64.7z";
        }
        else if (isMac)
        {
            return isArm64 ? baseArchiveName + "mac-arm64.7z" : baseArchiveName + "mac-x64.7z";
        }
        else if (isLinux)
        {
            return isArm64 ? baseArchiveName + "linux-arm64.7z" : baseArchiveName + "linux-x64.7z";
        }

        throw unsupportedPlatform(os, arch);
    }

    private static String resolveNativeExtension()
    {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win"))
            return ".dll";

        if (os.contains("mac") || os.contains("darwin"))
            return ".dylib";

        return ".so";
    }

    private static boolean isNativeLibrary(String filename, String extension)
    {
        // .so files may be versioned: libWebCore.so, libWebCore.so.1, etc.
        return filename.endsWith(extension) || (extension.equals(".so") && filename.contains(".so"));
    }

    private static RuntimeException unsupportedPlatform(String os, String arch)
    {
        return new RuntimeException(String.format("Niagara: Unsupported platform: os='%s' arch='%s'. " +
                "Supported: Windows x64, macOS x64/ARM64, Linux x64/ARM64.", os, arch));
    }
}
