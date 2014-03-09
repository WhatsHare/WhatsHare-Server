/**
 * PairingResponse.java Created on 2 Jul 2013 Copyright 2013 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package it.mb.whatshare;

/**
 * Sent by a paired Android device when replying to a pairing request made by
 * the Chrome Extension.
 * 
 * @author Michele Bonazza
 * 
 */
public class PairingResponse implements Message {

    private String requestorId; // plain text
    private String pairedId; // encrypted
    private String chosenId; // encrypted

    /**
     * Returns the requestor ID, which is Chrome's channelId for GCM.
     * 
     * @return the requestorId
     */
    public String getRequestorId() {
        return requestorId;
    }

    /**
     * Sets the requestor ID, which is Chrome's channelId for GCM.
     * 
     * @param requestorId
     *            the requestorId to set
     */
    public void setRequestorId(String requestorId) {
        this.requestorId = requestorId;
    }

    /**
     * Returns the encrypted paired ID, which is the Android's GCM ID encrypted
     * using the key generated by the Chrome Extension.
     * 
     * @return the pairedId
     */
    public String getPairedId() {
        return pairedId;
    }

    /**
     * Sets the encrypted paired ID, which is the Android's GCM ID encrypted
     * using the key generated by the Chrome Extension.
     * 
     * @param pairedId
     *            the pairedId to set
     */
    public void setPairedId(String pairedId) {
        this.pairedId = pairedId;
    }

    /**
     * Returns the chosen ID, which is the ID given by the user of the Android
     * device to the Chrome Extension, encrypted using the key generated by the
     * Chrome Extension.
     * 
     * @return the chosenId
     */
    public String getChosenId() {
        return chosenId;
    }

    /**
     * Sets the chosen ID, which is the ID given by the user of the Android
     * device to the Chrome Extension, encrypted using the key generated by the
     * Chrome Extension.
     * 
     * @param chosenId
     *            the chosenId to set
     */
    public void setChosenId(String chosenId) {
        this.chosenId = chosenId;
    }

    /**
     * Returns whether all fields are populated for this message.
     * 
     * @return <code>true</code> if all the fields in this message have a
     *         non-empty value
     */
    public boolean isValid() {
        return chosenId != null && !chosenId.isEmpty() && pairedId != null
                && !pairedId.isEmpty() && requestorId != null
                && !requestorId.isEmpty();
    }

    public String toString() {
        // @formatter:off
        return new StringBuilder("pairingResponse {")
                    .append("requestorId: '")
                    .append(requestorId)
                    .append("', pairedId: '")
                    .append(pairedId)
                    .append("', chosenId: '")
                    .append(chosenId)
                    .append("'}")
                    .toString();
        // @formatter:on
    }

}