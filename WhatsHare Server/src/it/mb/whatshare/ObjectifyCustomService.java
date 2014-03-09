/**
 * ObjectifyService.java Created on 3 Jul 2013 Copyright 2013 Michele Bonazza
 * <michele.bonazza@gmail.com>
 */
package it.mb.whatshare;

import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyFactory;
import com.googlecode.objectify.ObjectifyService;

/**
 * A wrapper around objectify's {@link ObjectifyService} class used to register
 * all the entities used by this server.
 * 
 * @author Michele Bonazza
 * 
 */
public class ObjectifyCustomService {

    static {
        factory().register(User.class);
    }

    /**
     * Proxies calls to {@link ObjectifyService#ofy()}.
     * 
     * @return whatever {@link ObjectifyService#ofy()} returns
     */
    public static Objectify ofy() {
        return ObjectifyService.ofy();
    }

    /**
     * Proxies calls to {@link ObjectifyService#factory()}.
     * 
     * @return whatever {@link ObjectifyService#factory()} returns
     */
    public static ObjectifyFactory factory() {
        return ObjectifyService.factory();
    }
}
