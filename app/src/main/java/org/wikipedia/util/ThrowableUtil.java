package org.wikipedia.util;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.github.kevinsawicki.http.HttpRequest;

import org.json.JSONException;
import org.mediawiki.api.json.ApiException;
import org.wikipedia.R;
import org.wikipedia.createaccount.CreateAccountException;
import org.wikipedia.dataclient.mwapi.MwException;
import org.wikipedia.dataclient.okhttp.HttpStatusException;
import org.wikipedia.login.LoginClient;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLException;

public final class ThrowableUtil {

    // TODO: replace with Apache Commons Lang ExceptionUtils.
    @NonNull
    public static Throwable getInnermostThrowable(@NonNull Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    // TODO: replace with Apache Commons Lang ExceptionUtils.
    public static boolean throwableContainsException(@NonNull Throwable e, Class<?> exClass) {
        Throwable t = e;
        while (t != null) {
            if (t.getClass().equals(exClass)) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    /**
     * DEPRECATED: This is a rarely-used function intended to sift through server error responses
     * and pass through the relevant bits along with standardized strings in certain cases.
     *
     * Getting the handful of canned strings depends on processing contexts that might be null by
     * the time we make it here.  Further, we're moving away from using raw server messages in favor
     * of statically defined XML error views, which are safer.  This should no longer be used.
     */
    @NonNull @Deprecated
    public static AppError getAppError(@NonNull Context context, @NonNull Throwable e) {
        Throwable inner = ThrowableUtil.getInnermostThrowable(e);
        AppError result;
        // look at what kind of exception it is...
        if (inner instanceof ApiException) {
            // it's a well-formed error response from the server!
            result = new AppError(getApiError(context, (ApiException) inner),
                                  getApiErrorMessage(context, (ApiException) inner));
        } else if (isNetworkError(e)) {
            result = new AppError(context.getString(R.string.error_network_error),
                    context.getString(R.string.format_error_server_message,
                            inner.getLocalizedMessage()));
        } else if (e instanceof HttpStatusException) {
            result = new AppError(e.getMessage(), Integer.toString(((HttpStatusException) e).code()));
        } else if (inner instanceof LoginClient.LoginFailedException
                || inner instanceof CreateAccountException
                || inner instanceof MwException) {
            result = new AppError(inner.getLocalizedMessage(), "");
        } else if (ThrowableUtil.throwableContainsException(e, JSONException.class)) {
            result = new AppError(context.getString(R.string.error_response_malformed),
                                  inner.getLocalizedMessage());
        } else {
            // everything else has fallen through, so just treat it as an "unknown" error
            result = new AppError(context.getString(R.string.error_unknown),
                    inner.getLocalizedMessage());
        }
        return result;
    }

    public static boolean isOffline(@NonNull Throwable caught) {
        return caught instanceof UnknownHostException
                || caught instanceof SocketException
                || caught instanceof SocketTimeoutException;
    }

    @SuppressWarnings("checkstyle:magicnumber") public static boolean is404(@NonNull Throwable caught) {
        return caught instanceof HttpStatusException && ((HttpStatusException) caught).code() == 404;
    }

    private static boolean isNetworkError(@NonNull Throwable e) {
        return ThrowableUtil.throwableContainsException(e, HttpRequest.HttpRequestException.class)
                || ThrowableUtil.throwableContainsException(e, HttpStatusException.class)
                || ThrowableUtil.throwableContainsException(e, UnknownHostException.class)
                || ThrowableUtil.throwableContainsException(e, TimeoutException.class)
                || ThrowableUtil.throwableContainsException(e, SSLException.class);
    }

    @NonNull @Deprecated
    private static String getApiError(@NonNull Context context, @NonNull ApiException e) {
        String text;
        if ("missingtitle".equals(e.getCode()) || "invalidtitle".equals(e.getCode())) {
            text = context.getResources().getString(R.string.page_does_not_exist_error);
        } else {
            text = context.getString(R.string.error_server_response);
        }
        return text;
    }

    // TODO: migrate this to ApiException.toString()
    @NonNull @Deprecated
    private static String getApiErrorMessage(@NonNull Context c, @NonNull ApiException e) {
        String text;
        if (e.getInfo() != null) {
            // if we have an actual message from the server, then prefer it
            text = c.getString(R.string.format_error_server_message, e.getInfo());
        } else if (e.getCode() != null) {
            // otherwise, just show the error code
            text = c.getString(R.string.format_error_server_code, e.getCode());
        } else {
            // if all else fails, show the message of the exception
            text = e.getMessage();
        }
        return text;
    }

    @Deprecated public static class AppError {
        private String error;
        private String detail;
        public AppError(@NonNull String error, @Nullable String detail) {
            this.error = error;
            this.detail = detail;
        }
        @NonNull
        public String getError() {
            return error;
        }
        @Nullable
        public String getDetail() {
            return detail;
        }
    }

    private ThrowableUtil() { }
}
