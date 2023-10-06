package io.github.wenjunxiao.zookeeper.downloader;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;

public class Main {

    private static URL toUrl(String file) throws MalformedURLException {
        if (file.startsWith("http")) {
            return new URL(file);
        }
        return new File(file).toURI().toURL();
    }

    public static void download(String source, String target) throws IOException {
        if (source.startsWith("http")) {
            URL url = new URL(source);
            File dest = new File(target, new File(url.getFile()).getName());
            if (!dest.exists()) {
                System.out.println("Download " + url.toString() + " to " + dest.getAbsolutePath());
                try (InputStream input = url.openStream(); OutputStream output = new FileOutputStream(dest)) {
                    IOUtils.copy(input, output);
                }
            }
            source = dest.getAbsolutePath();
        }
        System.out.println("Uncompress start " + source);
        try (TarArchiveInputStream stream = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(source)))) {
            TarArchiveEntry entry;
            while ((entry = stream.getNextTarEntry()) != null) {
                File dest = new File(target, entry.getName());
                if (entry.isDirectory()) {
                    if (!dest.mkdirs()) {
                        System.out.println("Unable to create directory " + dest.getAbsolutePath());
                    }
                } else if (!dest.createNewFile()) {
                    System.out.println("Unable to create file " + dest.getAbsolutePath());
                } else {
                    try (OutputStream output = new FileOutputStream(dest)) {
                        IOUtils.copy(stream, output);
                    }
                }
            }
        }
        System.out.println("Uncompress done " + source);
    }

    public static void main(String[] args) throws IOException {
        // download("D:\\Downloads\\zookeeper-3.4.5.tar.gz", "E:\\srcs\\lab-zookeeper-session-moved\\target");
        download(args[0], args[1]);
    }
}
