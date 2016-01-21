package enkan.util;

import enkan.collection.OptionMap;
import enkan.data.HttpResponse;
import enkan.exception.UnrecoverableException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.*;
import java.util.Date;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * @author kawasima
 */
public class HttpResponseUtils {
    public static void header(HttpResponse response, String name, String value) {
        response.getHeaders().toMutable().put(name, value);
    }

    public static HttpResponse contentType(HttpResponse response, String type) {
        if (type != null) {
            response.getHeaders().toMutable().put("content-type", type);
        }
        return response;
    }

    public static HttpResponse contentLength(HttpResponse response, Long len) {
        if (len != null) {
            response.getHeaders().toMutable().put("content-length", len);
        }
        return response;
    }

    public static HttpResponse lastModified(HttpResponse response, Date lastModified) {
        if (lastModified != null) {
            response.getHeaders().toMutable().put("Last-Modified", HttpDateFormat.RFC1123.format(lastModified));
        }
        return response;
    }

    private static String addEndingSlash(String path) {
        return path.endsWith("/") ? path : path + "/";
    }

    public static boolean isJarDirectory(JarURLConnection conn) throws IOException {
        JarFile jarFile = conn.getJarFile();
        String entryName = conn.getEntryName();
        ZipEntry dirEntry = jarFile.getEntry(addEndingSlash(entryName));
        return dirEntry != null && dirEntry.isDirectory();
    }

    public static Long connectionContentLength(URLConnection conn) {
        long len = conn.getContentLengthLong();
        return len <= 0 ? null : len;
    }

    public static Date connectionLastModified(URLConnection conn) {
        long lastMod = conn.getLastModified();
        return lastMod > 0 ? new Date(lastMod) : null;
    }

    public static ContentData resourceData(URL url) {
        String protocol = url.getProtocol();
        if ("file".equals(protocol)) {
            try {
                File file = new File(url.toURI());
                if (!file.isDirectory()) {
                    return new FileContentData(file, file.length(),
                            new Date((file.lastModified() / 1000) * 1000));
                }
            } catch (URISyntaxException e) {
                UnrecoverableException.raise(e);
            }
        } else if ("jar".equals(protocol)) {
            try {
                JarURLConnection connection = (JarURLConnection) url.openConnection();
                if (connection != null && isJarDirectory(connection)) {
                    return new StreamContentData(connection.getInputStream(),
                            connectionContentLength(connection),
                            connectionLastModified(connection));
                }
            } catch (IOException e) {
                UnrecoverableException.raise(e);
            }
        }

        return null;
    }

    public static HttpResponse urlResponse(URL url) {
        ContentData data = resourceData(url);
        HttpResponse response = null;
        if (data instanceof FileContentData) {
            response = HttpResponse.of(((FileContentData) data).getContent());
        } else if (data instanceof StreamContentData) {
            response = HttpResponse.of(((StreamContentData) data).getContent());
        } else {
            UnrecoverableException.raise(new MalformedURLException("Unknown protocol: " + url));
        }
        contentLength(response, data.getContentLength());
        lastModified(response, data.getLastModifiedDate());
        return response;
    }

    public static HttpResponse resourceResponse(String path, OptionMap options) {
        String root = options.getString("root");
        path = (root != null ? root : "") + "/" + path;
        path.replace("//", "/").replaceAll("^/", "");

        ClassLoader loader = (ClassLoader) options.get("loader");
        if (loader == null) {
            loader = Thread.currentThread().getContextClassLoader();
        }
        URL url = loader.getResource(path);
        return urlResponse(url);
    }

    private static abstract class ContentData<T> implements Serializable {
        private T content;
        private Long contentLength;
        private Date lastModifiedDate;

        public ContentData(T content, Long contentLength, Date lastModifiedDate) {
            this.content = content;
            this.contentLength = contentLength;
            this.lastModifiedDate = lastModifiedDate;
        }

        public T getContent() {
            return content;
        }

        public void setContent(T content) {
            this.content = content;
        }

        public Long getContentLength() {
            return contentLength;
        }

        public void setContentLength(Long contentLength) {
            this.contentLength = contentLength;
        }

        public Date getLastModifiedDate() {
            return lastModifiedDate;
        }

        public void setLastModifiedDate(Date lastModifiedDate) {
            this.lastModifiedDate = lastModifiedDate;
        }
    }

    private static class FileContentData extends ContentData<File> {
        public FileContentData(File content, Long contentLength, Date lastModifiedDate) {
            super(content, contentLength, lastModifiedDate);
        }
    }

    private static class StreamContentData extends ContentData<InputStream> {
        public StreamContentData(InputStream content, Long contentLength, Date lastModifiedDate) {
            super(content, contentLength, lastModifiedDate);
        }
    }

}
