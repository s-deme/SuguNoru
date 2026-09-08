package jp.sugunoru.app.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;

/** Downloads one public timetable page over HTTPS and passes its text to the parser. */
public final class OfficialTimetableFetcher {
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 15_000;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;
    private static final Pattern CHARSET_IN_HEADER = Pattern.compile(
            "(?i)charset\\s*=\\s*[\\\"']?([^;\\s\\\"']+)");
    private static final Pattern CHARSET_IN_META = Pattern.compile(
            "(?i)<meta[^>]+charset\\s*=\\s*[\\\"']?([^\\s\\\"'>/]+)");

    public record FetchResult(OfficialTimetableParser.Timetable timetable, String resolvedUrl) {}

    public FetchResult fetch(String sourceUrl) throws FetchException {
        URI current = secureUri(sourceUrl);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            HttpsURLConnection connection = null;
            try {
                connection = (HttpsURLConnection) current.toURL().openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
                connection.setReadTimeout(READ_TIMEOUT_MILLIS);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json,text/plain;q=0.8");
                connection.setRequestProperty("User-Agent", "SuguNoru/2.1 (official-timetable-refresh)");

                int status = connection.getResponseCode();
                if (isRedirect(status)) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isBlank()) {
                        throw new FetchException("公式サイトの転送先を確認できませんでした");
                    }
                    current = secureUri(current.resolve(location).toString());
                    continue;
                }
                if (status < HttpURLConnection.HTTP_OK || status >= HttpURLConnection.HTTP_MULT_CHOICE) {
                    throw new FetchException("公式サイトが時刻表を返しませんでした（HTTP " + status + "）");
                }
                int length = connection.getContentLength();
                if (length > MAX_RESPONSE_BYTES) {
                    throw new FetchException("公式ページのサイズが大きすぎます");
                }
                byte[] body;
                try (InputStream input = connection.getInputStream()) {
                    body = readLimited(input);
                }
                String contentType = connection.getContentType();
                Charset charset = responseCharset(contentType, body);
                String document = new String(body, charset);
                OfficialTimetableParser.Timetable timetable = OfficialTimetableParser.parse(
                        document, contentType);
                return new FetchResult(timetable, current.toString());
            } catch (FetchException error) {
                throw error;
            } catch (SocketTimeoutException error) {
                throw new FetchException("公式サイトへの接続がタイムアウトしました", error);
            } catch (UnknownHostException error) {
                throw new FetchException("インターネットに接続できません", error);
            } catch (SSLException error) {
                throw new FetchException("公式サイトへの安全な接続を確認できません", error);
            } catch (IllegalArgumentException error) {
                throw new FetchException(error.getMessage(), error);
            } catch (IOException error) {
                throw new FetchException("公式サイトに接続できませんでした", error);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        throw new FetchException("公式サイトの転送が多すぎます");
    }

    private static boolean isRedirect(int status) {
        return status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_SEE_OTHER
                || status == 307 || status == 308;
    }

    private static URI secureUri(String value) throws FetchException {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getHost().isBlank() || uri.getUserInfo() != null) {
                throw new FetchException("公式時刻表URLは https:// のページを指定してください");
            }
            return uri;
        } catch (URISyntaxException error) {
            throw new FetchException("公式時刻表URLの形式を確認してください", error);
        }
    }

    private static byte[] readLimited(InputStream input) throws IOException, FetchException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8_192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) >= 0) {
            total += count;
            if (total > MAX_RESPONSE_BYTES) {
                throw new FetchException("公式ページのサイズが大きすぎます");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static Charset responseCharset(String contentType, byte[] body) {
        String candidate = findCharset(CHARSET_IN_HEADER, contentType == null ? "" : contentType);
        if (candidate == null) {
            String prefix = new String(body, 0, Math.min(body.length, 4096), StandardCharsets.ISO_8859_1);
            candidate = findCharset(CHARSET_IN_META, prefix);
        }
        if (candidate != null) {
            try {
                return Charset.forName(candidate);
            } catch (RuntimeException ignored) {
                // A broken charset declaration should not prevent a UTF-8 timetable from working.
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static String findCharset(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? matcher.group(1).trim().toLowerCase(Locale.ROOT) : null;
    }

    public static final class FetchException extends Exception {
        public FetchException(String message) {
            super(message);
        }

        public FetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
