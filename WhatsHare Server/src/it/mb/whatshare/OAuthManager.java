/**
 * OAuthManager.java Created on 5 Jul 2013 Copyright 2013 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package it.mb.whatshare;

import static it.mb.whatshare.ObjectifyCustomService.ofy;
import static java.lang.String.format;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import com.sun.jersey.api.view.Viewable;

/**
 * Manages requests involving Google's OAuth2 authentication.
 * 
 * @author Michele Bonazza
 * 
 */
@Path("/oauth2callback")
public class OAuthManager {

    // don't show anyone!
    private static final String REDIRECT_URI = "http://localhost:8888/oauth2callback";
    private static final String OAUTH_URL = "https://accounts.google.com/o/oauth2/token";
    private static final String CLIENT_ID = "INSERT_CLIENT_ID.apps.googleusercontent.com";
    private static final String CLIENT_SECRET = "SET_CLIENT_SECRET";

    private static final List<NameValuePair> REQUEST_TOKENS_ATTRIBUTES = new ArrayList<NameValuePair>() {

        private static final long serialVersionUID = -3979045467453748796L;

        {
            add(new BasicNameValuePair("client_id", CLIENT_ID));
            add(new BasicNameValuePair("client_secret", CLIENT_SECRET));
            add(new BasicNameValuePair("redirect_uri", REDIRECT_URI));
            add(new BasicNameValuePair("grant_type", "authorization_code"));
        }
    };

    private static final List<NameValuePair> REFRESH_TOKENS_ATTRIBUTES = new ArrayList<NameValuePair>() {

        private static final long serialVersionUID = 5838936899713550654L;

        {
            add(new BasicNameValuePair("client_id", CLIENT_ID));
            add(new BasicNameValuePair("client_secret", CLIENT_SECRET));
            add(new BasicNameValuePair("grant_type", "refresh_token"));
        }
    };

    private static final String SUCCESS_PAGE_FORMAT = "/static/%ssuccess.html";
    private static final String FAIL_PAGE_FORMAT = "/static/%serror.html";

    @Context
    private ServletContext context;

    /**
     * Called by Google's OAuth2 server after users of the Chrome extension
     * accept the dialog that requests approval to connect their google account
     * with WhatsHare.
     * 
     * @param authCode
     *            the authorization code to be used for Google's OAuth2
     *            authentication process
     * @param piggybacked
     *            a String of the form <code>locale/channelId</code>, where
     *            <code>locale</code> is the locale set in the requestor device,
     *            and <code>channelId</code> is the sender's GCM channel ID
     * @return the appropriate generated page, <code>success.html</code> if
     *         <code>authCode</code> is not <code>null</code>,
     *         <code>fail.html</code> if it is
     */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public Viewable oauthReply(@QueryParam("state") String piggybacked,
            @QueryParam("code") String authCode) {
        System.out.println("piggybacked is " + piggybacked);
        int slashIndex = piggybacked.indexOf('/') + 1;
        String channelId = null, locale = "";
        if (slashIndex > 0) {
            locale = piggybacked.substring(0, slashIndex);
            channelId = piggybacked.substring(slashIndex);
        }
        System.out.println("locale is " + locale + ", channelId is "
                + channelId + ", authCode is " + authCode);
        boolean success = false;
        if (channelId != null && authCode != null) {
            User user = User.fromChannelId(channelId);
            if (user == null) {
                // this user needs a new token
                if (User.fromAuthCode(authCode, channelId) != null) {
                    success = true;
                } // else something is wrong with OAuth's reply
            } else if (user.isTokenExpired()) {
                success = refreshToken(user);
            } else {
                // token is still valid, no need to update it
                success = true;
            }
        }
        try {
            // redirect, so 'error' is within the URL in case of errors
            // (see the chrome extension's showQR.js for details)
            throw new WebApplicationException(Response.temporaryRedirect(
                    new URI(getLocalizedURL(success ? SUCCESS_PAGE_FORMAT
                            : FAIL_PAGE_FORMAT, locale, context))).build());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        // we really screwed up big time with paths!
        return success ? getLocalized(SUCCESS_PAGE_FORMAT, locale, context)
                : getLocalized(FAIL_PAGE_FORMAT, locale, context);
    }

    /**
     * Formats <tt>pageFormat</tt> using <tt>locale</tt>, and uses
     * <tt>context</tt> to check if a file with that URL exists. If it doesn't,
     * the empty string is used instead of <tt>locale</tt> to format
     * <tt>pageFormat</tt> and that page is returned.
     * 
     * @param pageFormat
     *            the page format, something like
     *            <code>"/static/%swhatever.html"</code>
     * @param locale
     *            the requestor's locale, including a trailing slash (e.g.
     *            <code>"en_US/"</code>)
     * @param context
     *            the servlet context
     * @return a viewable created according to the description above
     */
    private static Viewable getLocalized(String pageFormat, String locale,
            ServletContext context) {
        return new Viewable(getLocalizedURL(pageFormat, locale, context), null);
    }

    /**
     * Formats <tt>pageFormat</tt> using <tt>locale</tt>, and uses
     * <tt>context</tt> to check if a file with that URL exists. If it doesn't,
     * the empty string is used instead of <tt>locale</tt> to format
     * <tt>pageFormat</tt> and that URL is returned.
     * 
     * @param pageFormat
     *            the page format, something like
     *            <code>"/static/%swhatever.html"</code>
     * @param locale
     *            the requestor's locale, including a trailing slash (e.g.
     *            <code>"en_US/"</code>)
     * @param context
     *            the servlet context
     * @return the URL to create a viewable with according to the description
     *         above
     */
    private static String getLocalizedURL(String pageFormat, String locale,
            ServletContext context) {
        String localized = format(pageFormat, locale.toLowerCase());
        try {
            if (context.getResource(localized) != null) {
                return localized;
            }
        } catch (MalformedURLException e) {
            // it's wrong, we get it
        }
        // if there's not a localized version, go for the default
        return format(pageFormat, "");
    }

    /**
     * Retrieves an access token from Google's OAuth2 servers using the argument
     * <code>authCode</code>.
     * 
     * @param authCode
     *            the authorization code used to get tokens
     * @return a map of <code>&lt;key, value&gt;</code> pairs returned by
     *         Google's OAuth2 servers, or <code>null</code> if the request
     *         couldn't be performed or finished in error
     */
    public static Map<String, String> getAccessToken(String authCode) {
        List<NameValuePair> parms = new ArrayList<NameValuePair>(
                REQUEST_TOKENS_ATTRIBUTES);
        parms.add(new BasicNameValuePair("code", authCode));
        String response = NetworkingUtils.post(OAUTH_URL, parms,
                MediaType.APPLICATION_FORM_URLENCODED_TYPE);
        return NetworkingUtils.toStringMap(response);
    }

    /**
     * Refreshes the access token for <code>user</code>, using the refresh token
     * returned by {@link User#getRefreshToken()}.
     * 
     * <p>
     * The argument <code>user</code> is also updated on the datastore after the
     * access tokens are renewed.
     * 
     * @param user
     *            the user whose access token must be refreshed
     * @return <code>true</code> if the access token was successfully refreshed
     */
    public static boolean refreshToken(User user) {
        System.out.println("refreshing token...");
        List<NameValuePair> parms = new ArrayList<NameValuePair>(
                REFRESH_TOKENS_ATTRIBUTES);
        parms.add(new BasicNameValuePair("refresh_token", user
                .getRefreshToken()));
        String response = NetworkingUtils.post(OAUTH_URL, parms,
                MediaType.APPLICATION_FORM_URLENCODED_TYPE);
        Map<String, String> map = NetworkingUtils.toStringMap(response);
        boolean success = user.setOauthParms(map, null);
        ofy().save().entity(user).now();
        return success;
    }

}
