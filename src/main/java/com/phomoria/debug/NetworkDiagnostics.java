package com.phomoria.debug;

import java.net.SocketTimeoutException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.SocketException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

/**
 * Consistent diagnostics for operations that depend on network access.
 * The diagnosis is deliberately evidence-based: client-side exceptions cannot
 * reliably identify whether the Wi-Fi, ISP, proxy, or remote server caused a reset.
 */
public final class NetworkDiagnostics {
    private NetworkDiagnostics() {}

    public static void started(String operation, String url, String context) {
        DebugLog.info("NETWORK START operation=" + safe(operation)
                + ", host=" + host(url) + ", " + safe(context));
    }

    public static void succeeded(String operation, long startedNanos, int httpStatus, String context) {
        String event = httpStatus >= 200 && httpStatus < 300 ? "NETWORK SUCCESS" : "NETWORK RESPONSE";
        DebugLog.info(event + " operation=" + safe(operation)
                + ", httpStatus=" + (httpStatus > 0 ? httpStatus : "not-exposed-by-service")
                + ", elapsedMs=" + elapsed(startedNanos) + ", " + safe(context));
    }

    public static void failed(String operation, long startedNanos, String url,
                              Throwable error, String context) {
        Throwable root = rootCause(error);
        String diagnosis = classify(root);
        DebugLog.error("NETWORK FAILURE operation=" + safe(operation)
                + ", host=" + host(url)
                + ", elapsedMs=" + elapsed(startedNanos)
                + ", exception=" + root.getClass().getSimpleName()
                + ", message=" + safe(String.valueOf(root.getMessage()))
                + ", diagnosis=" + diagnosis
                + ", source=UNCONFIRMED"
                + ", " + safe(context), error);
    }

    public static String classify(Throwable error) {
        if (error instanceof UnknownHostException) return "DNS_RESOLUTION_FAILED";
        if (error instanceof ConnectException) return "CONNECTION_ESTABLISHMENT_FAILED";
        if (error instanceof SocketTimeoutException || error instanceof TimeoutException)
            return "NETWORK_TIMEOUT_OR_REMOTE_RESPONSE_DELAY";
        if (error instanceof SocketException) return "CONNECTION_INTERRUPTED_OR_RESET";
        if (error instanceof java.io.EOFException) return "CONNECTION_CLOSED_BEFORE_RESPONSE_COMPLETED";
        if (error instanceof javax.net.ssl.SSLException) return "TLS_CONNECTION_ERROR";
        return "NETWORK_OR_REMOTE_SERVICE_ERROR";
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null && current.getCause() != current)
            current = current.getCause();
        return current == null ? new Exception("Unknown error") : current;
    }

    private static long elapsed(long startedNanos) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private static String host(String value) {
        try { return URI.create(value).getHost(); }
        catch (Exception ignored) { return "unknown"; }
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "context=none";
        return value.replaceAll("[\\r\\n\\t]", " ").replaceAll("(?i)(token|password|authorization)=\\S+", "$1=[REDACTED]");
    }
}
