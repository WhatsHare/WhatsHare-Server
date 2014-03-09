/**
 * User.java Created on 9 Jul 2013 Copyright 2013 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package it.mb.whatshare;

import static it.mb.whatshare.ObjectifyCustomService.ofy;

import java.util.Map;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

/**
 * OAuth2 credentials for Chrome Extensions.
 * 
 * @author Michele Bonazza
 * 
 */
@Entity
public class User {

    private static final long TOKEN_EXPIRATION_TOLERANCE = 60 * 1000;

    /**
     * This entity's ID, as assigned by GAE's datastore.
     */
    @Id
    private Long id;

    @Index
    private String channelId;

    private String refreshToken;
    private String accessToken;
    private long tokenExpiration;

    /**
     * Updates fields of this object according to the argument
     * <code>parms</code>.
     * 
     * <p>
     * This method can be called both when registering a user for the first time
     * and when updating the access token for an already registered user.
     * 
     * @param parms
     *            a list of parameters coming from Google's OAuth server
     *            response, or <code>null</code> if refreshing a registered
     *            user's access token
     * @param channelId
     *            the Chrome Extension's GCM id, can be <code>null</code> if
     *            calling this method to refresh a user's access token
     * @return <code>true</code> if this user is in a working OAuth state after
     *         this method is called
     */
    public boolean setOauthParms(Map<String, String> parms, String channelId) {
        if (parms != null && !parms.isEmpty()) {
            try {
                tokenExpiresIn(Integer.valueOf(parms.get("expires_in")));
            } catch (NumberFormatException e) {
                return false;
            }
            accessToken = parms.get("access_token");
            if (refreshToken == null)
                // it's only returned the first time, whereas this can be called
                // even after that
                refreshToken = parms.get("refresh_token");
        }
        if (accessToken == null || refreshToken == null)
            return false;
        if (channelId != null)
            // null can be passed for token refresh, not before that
            this.channelId = channelId;
        return true;
    }

    /**
     * Returns the user found in the datastore matching the argument
     * <code>channelId</code>.
     * 
     * @param channelId
     *            the GCM ID
     * @return the user if found, <code>null</code> otherwise
     */
    public static User fromChannelId(String channelId) {
        return ofy().load().type(User.class).filter("channelId", channelId)
                .first().now();
    }

    /**
     * Creates a new user for the argument <code>channelId</code> using the
     * argument <code>authCode</code>.
     * 
     * <p>
     * The returned user is saved on the datastore.
     * 
     * @param authCode
     *            the authorization code to be used with Google's OAuth2 servers
     * @param channelId
     *            the GCM ID of the user
     * @return a user with its tokens set (and saved in the datastore) or
     *         <code>null</code> if a user couldn't be created for the argument
     *         parameters
     */
    public static User fromAuthCode(String authCode, String channelId) {
        if (authCode != null && authCode.length() > 0) {
            Map<String, String> auth = OAuthManager.getAccessToken(authCode);
            User user = new User();
            if (user.setOauthParms(auth, channelId)) {
                ofy().save().entity(user).now();
                return user;
            }
        }
        return null;
    }

    /**
     * Returns the refresh token in use for this user.
     * 
     * @return the refresh token
     */
    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * Returns the access token in use for this user.
     * 
     * @return the access token
     */
    public String getAccessToken() {
        return accessToken;
    }

    private void tokenExpiresIn(int delayInSeconds) {
        this.tokenExpiration = System.currentTimeMillis()
                + (delayInSeconds * 1000);
    }

    /**
     * Checks whether the access token for this user has already expired, or is
     * about to.
     * 
     * @return <code>true</code> if the access token for this user is about to
     *         expire or has already expired
     */
    public boolean isTokenExpired() {
        System.out.println("is token expired? is " + tokenExpiration + " < "
                + (System.currentTimeMillis() - TOKEN_EXPIRATION_TOLERANCE)
                + "?");
        return tokenExpiration < System.currentTimeMillis()
                - TOKEN_EXPIRATION_TOLERANCE;
    }
}
