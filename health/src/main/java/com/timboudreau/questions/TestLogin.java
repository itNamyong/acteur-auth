package com.timboudreau.questions;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.util.BasicCredentials;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.acteur.util.Method;
import com.mastfrog.acteur.util.PasswordHasher;
import com.mastfrog.acteur.util.Realm;
import com.mastfrog.settings.Settings;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import static com.timboudreau.trackerapi.Properties.name;
import com.timboudreau.trackerapi.Timetracker;
import com.timboudreau.trackerapi.support.AuthSupport;
import com.timboudreau.trackerapi.support.UserCollectionFinder;
import io.netty.handler.codec.http.DefaultCookie;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.ServerCookieEncoder;
import java.util.HashMap;
import java.util.Map;
import org.joda.time.Duration;

/**
 *
 * @author Tim Boudreau
 */
public final class TestLogin extends Page {

    @Inject
    public TestLogin(ActeurFactory af) {
        // Helper for using basic auth from webapp without nasty popups
        // This will need at least some throttling of repeated calls
        add(af.matchPath("^testLogin$", "^users\\/.*?\\/testLogin$"));
        add(af.matchMethods(Method.GET, Method.POST, Method.PUT, Method.OPTIONS));
        add(UserCollectionFinder.class);
        add(CheckIt.class);
    }

    private static class CheckIt extends Acteur {

        @Inject
        CheckIt(Event evt, AuthSupport supp) {
            System.out.println("REQUEST " + evt.getPath());
            for (String key : evt.getRequest().headers().names()) {
                System.out.println(key + ':' + evt.getHeader(key));
            }
            if ("true".equals(evt.getParameter("auth"))) {
                add(Headers.WWW_AUTHENTICATE, Realm.createSimple(Timetracker.REALM_NAME));
            }
            int code = 200;
            if (evt.getParameter("respondWith") != null) {
                code = Integer.parseInt(evt.getParameter("respondWith"));
            }
            BasicCredentials credentials = evt.getHeader(Headers.AUTHORIZATION);
            Map<String, Object> result = new HashMap<>();
            result.put("credentialsPresent", credentials != null);
            AuthSupport.Result authResult = supp.get();
            result.put("userFound", authResult.username != null);
            if (authResult.isSuccess()) {
                if (!authResult.cookie) {
                    String cookie = supp.encodeLoginCookie(authResult);
                    System.out.println("NEW COOKIE: " + cookie);
                    add(Headers.SET_COOKIE, cookie);
                }
                result.put("valid", true);
                result.put("byCookie", authResult.cookie);
                result.put("user", authResult.username);
                System.out.println("OK user " + authResult.username);
                setState(new RespondWith(code, result));
            } else {
                System.out.println("Fail " + authResult.type + " for " + authResult.username);
                setState(new RespondWith(code, result));
            }
        }
    }
}