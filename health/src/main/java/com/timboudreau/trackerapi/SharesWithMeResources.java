package com.timboudreau.trackerapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.auth.AuthenticationActeur;
import com.mastfrog.acteur.mongo.CursorWriterActeur;
import com.mastfrog.acteur.mongo.userstore.TTUser;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.timboudreau.trackerapi.support.UserCollectionFinder;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;

/**
 *
 * @author Tim Boudreau
 */
public class SharesWithMeResources extends Page {

    @Inject
    SharesWithMeResources(ActeurFactory af) {
        add(af.matchPath("^users/.*?/sharers/?$"));
        add(af.matchMethods(Method.GET));
        add(AuthenticationActeur.class);
        add(UserCollectionFinder.class);
        add(FindSharers.class);
        add(CursorWriterActeur.class);
    }

    @Override
    protected String getDescription() {
        return "Authenticate login and fetch user name";
    }
    
    private static class FindSharers extends Acteur {
        @Inject
        FindSharers(HttpEvent evt, TTUser user, DBCollection coll, ObjectMapper mapper) throws IOException {
            add(Headers.header("UserID"), user.id().toStringMongod());
            BasicDBObject projection = new BasicDBObject("_id", 1).append("name", 1).append("displayName", 1);
            DBCursor cursor = coll.find(new BasicDBObject("authorizes", user.id()), projection);
            if (cursor == null) {
                setState(new RespondWith(HttpResponseStatus.GONE, "No record of " + user.name()));
                return;
            }
            if (!cursor.hasNext()) {
                ok("[]\n");
                cursor.close();
            } else {
                next(cursor);
            }
        }
    }
}
