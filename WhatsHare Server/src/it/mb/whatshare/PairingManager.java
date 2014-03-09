/**
 * ManInTheMiddle.java Created on 1 Jul 2013 Copyright 2013 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package it.mb.whatshare;

import static it.mb.whatshare.ObjectifyCustomService.ofy;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 * Manages pairing between the Chrome Extension and Android devices.
 * 
 * @author Michele Bonazza
 * 
 */
@Path("/pairing")
public class PairingManager {

    private static final String CHROME_GCM_URL = "https://www.googleapis.com/gcm_for_chrome/v1/messages";

    private void checkValid(PairingResponse msg) {
        if (msg == null || !msg.isValid()) {
            ResponseBuilder builder = Response.status(Status.BAD_REQUEST);
            throw new WebApplicationException(builder.build());
        }
    }

    /**
     * Handles {@link PairingResponse}'s coming from Android devices.
     * 
     * @param msg
     *            the response sent by the Android device that's about to be
     *            paired
     * @return a successful response in case the caller device sent a valid
     *         message for an existing pairing request, a
     *         {@link Status#UNAUTHORIZED} message if otherwise
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response replyToRequest(PairingResponse msg) {
        System.out.println("reply to pairing request: " + msg);
        checkValid(msg);
        if (!sendToChrome(msg)) {
            return Response.status(Status.UNAUTHORIZED).build();
        }
        return Response.status(Status.OK).build();
    }

    /**
     * Checks if there's a valid {@link User} registered for the argument
     * <tt>channelId</tt>, and if so attempts to get a valid access token.
     * 
     * <p>
     * If the access token expired, the refresh token is used to get a new one;
     * if the procedure fails, this method gives up and informs the requestor.
     * 
     * @param channelId
     *            the channel ID (in GCM) to be searched
     * @return the String <code>"0"</code> if there's no registered user for the
     *         argument <code>channelId</code>, or if a valid access token can't
     *         be retrieved, <code>"1"</code> otherwise
     */
    @GET
    @Path("{channelId}")
    @Produces(MediaType.TEXT_PLAIN)
    public String isOAuthValid(@PathParam("channelId") String channelId) {
        String result = "1";
        User user = User.fromChannelId(channelId);
        if (user.isTokenExpired()) {
            if (!OAuthManager.refreshToken(user)) {
                result = "0";
            }
        } // else token is still valid, no need to update it
        return result;
    }

    private boolean sendToChrome(PairingResponse response) {
        User user = ofy().load().type(User.class)
                .filter("channelId", response.getRequestorId()).first().now();
        if (user != null) {
            // a reply to an actual user, wohoo!
            if (user.isTokenExpired()) {
                if (!OAuthManager.refreshToken(user)) {
                    return false;
                }
            }
            // token is valid, do the actual sending!
            List<NameValuePair> parms = new ArrayList<NameValuePair>();
            parms.add(new BasicNameValuePair("channelId", response
                    .getRequestorId()));
            parms.add(new BasicNameValuePair("subchannelId", "0"));
            NameValuePair authHeader = new BasicNameValuePair("Authorization",
                    "Bearer " + user.getAccessToken());
            JSONObject json = new JSONObject();
            try {
                json.put("paired", response.getPairedId());
                json.put("chosenID", response.getChosenId());
                parms.add(new BasicNameValuePair("payload", json.toString()));
                // parms.add(new BasicNameValuePair("payload", "ciao!"));
                String reply = NetworkingUtils.post(CHROME_GCM_URL, parms,
                        MediaType.APPLICATION_JSON_TYPE, authHeader);
                return reply != null;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

}
