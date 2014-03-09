/**
 * NetworkingUtils.java Created on 11 Jul 2013 Copyright 2013 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package it.mb.whatshare;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 * A set of utility methods to send and mangle stuff to/from servers.
 * 
 * @author Michele Bonazza
 * 
 */
public class NetworkingUtils {

    /**
     * Amount of attempts made for requests to servers before giving up.
     */
    public static final int RETRY_COUNT = 3;
    private static final long RETRY_SLEEP_TIME = 1000L;
    private static final boolean DEBUG = true;
    private static final String DEBUG_HOST = "http://192.168.0.8/";

    private NetworkingUtils() {
        // don't instantiate me!
    }

    /**
     * Returns a map containing <code>&lt;key, value&gt;</code> pairs for each
     * JSON field in the argument string.
     * 
     * @param json
     *            the server reply to be parsed
     * @return a map containing all values in the argument JSON string,
     *         <code>null</code> if the argument <code>json</code> is
     *         <code>null</code>
     */
    public static Map<String, String> toStringMap(String json) {
        if (json != null) {
            ObjectMapper mapper = new ObjectMapper();
            try {
                Map<String, String> map = mapper.readValue(
                        json,
                        mapper.getTypeFactory().constructMapLikeType(
                                HashMap.class, String.class, String.class));
                return map;
            } catch (JsonParseException e) {
                e.printStackTrace();
            } catch (JsonMappingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Makes a POST request to the argument <code>url</code> using the specified
     * <code>contentType</code> and the optional <code>extraHeaders</code> which
     * will have the argument list of name-value pairs as content.
     * 
     * <p>
     * The method makes up to {@link #RETRY_COUNT} attempts to execute the
     * request while it's in error without returning anything; if the last
     * attempt fails, the error message is returned.
     * 
     * <p>
     * When in debug mode, this method also routes the post to the configured
     * {@link #DEBUG_HOST} for logging the request (to see what's going on).
     * 
     * @param url
     *            the URL to post content to
     * @param parms
     *            the content of the POST message, which will be formatted
     *            according to the argument <code>contentType</code>
     * @param contentType
     *            the HTTP content type of the request, which determines how
     *            <code>parms</code> are encoded (currently supported:
     *            {@link MediaType#APPLICATION_FORM_URLENCODED_TYPE} and
     *            {@link MediaType#APPLICATION_JSON_TYPE})
     * @param extraHeaders
     *            a list of extra headers you may want to attach to the request
     * @return the potentially empty server response, which is <code>null</code>
     *         in case network exceptions were thrown during the process
     */
    public static String post(String url, List<NameValuePair> parms,
            MediaType contentType, NameValuePair... extraHeaders) {
        HttpPost post = new HttpPost(url);
        int tries = 1;
        String response = null;
        HttpResponse serverResponse = null;
        try {
            if (contentType
                    .isCompatible(MediaType.APPLICATION_FORM_URLENCODED_TYPE)) {
                post.setEntity(new UrlEncodedFormEntity(parms));
            } else {
                try {
                    post.setEntity(new StringEntity(toJson(parms), "UTF-8"));
                } catch (JSONException e) {
                    e.printStackTrace();
                    return null;
                }
            }
            post.setHeader("Content-Type", contentType.toString());
            for (NameValuePair header : extraHeaders) {
                post.setHeader(header.getName(), header.getValue());
            }
            long timeout = RETRY_SLEEP_TIME * tries;
            HttpParams params = new BasicHttpParams();
            DefaultHttpClient client = updateTimeout(params, timeout);
            int statusCode = Integer.MAX_VALUE;
            while (isError(statusCode) && tries < RETRY_COUNT) {
                try {
                    System.out.println("attempt " + tries);
                    serverResponse = client.execute(post);
                    statusCode = serverResponse.getStatusLine().getStatusCode();
                    if (isError(statusCode)) {
                        try {
                            System.err.println("Failed request, response is: "
                                    + EntityUtils.toString(serverResponse
                                            .getEntity()));
                        } catch (ParseException e1) {
                            e1.printStackTrace();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    }
                    response = new BasicResponseHandler()
                            .handleResponse(serverResponse);
                } catch (IOException e) {
                    // maybe just try again...
                    try {
                        // life is too short for exponential backoff
                        Thread.sleep(timeout);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }
                tries++;
                timeout = RETRY_SLEEP_TIME * tries;
                client = updateTimeout(params, timeout);
            }
            if (response != null) {
                System.out.println("response: " + response);
            } else if (DEBUG && isError(statusCode)) {
                post.setURI(URI.create(DEBUG_HOST
                        + url.substring(url.indexOf("//") + 2)));
                try {
                    client.execute(post, new BasicResponseHandler());
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            } else {
                // even if response is null, it's not an error
                response = "";
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return response;
    }

    private static boolean isError(int statusCode) {
        return statusCode > 399;
    }

    private static String toJson(List<NameValuePair> parms)
            throws JSONException {
        JSONObject json = new JSONObject();
        for (NameValuePair pair : parms) {
            json.put(pair.getName(), pair.getValue());
        }
        return json.toString();
    }

    private static DefaultHttpClient updateTimeout(HttpParams params,
            long timeout) {
        HttpConnectionParams.setConnectionTimeout(params, (int) timeout);
        HttpConnectionParams.setSoTimeout(params, (int) timeout * 3);
        DefaultHttpClient client = new DefaultHttpClient(params);
        client.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(0,
                false));
        return client;
    }
}
