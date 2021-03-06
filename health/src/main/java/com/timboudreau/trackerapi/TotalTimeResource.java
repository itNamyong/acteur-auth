package com.timboudreau.trackerapi;

import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.auth.AuthenticationActeur;
import com.mastfrog.acteur.headers.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import static com.timboudreau.trackerapi.Properties.*;
import com.timboudreau.trackerapi.support.AuthorizedChecker;
import com.timboudreau.trackerapi.support.CreateCollectionPolicy;
import com.timboudreau.trackerapi.support.Intervals;
import com.timboudreau.trackerapi.support.TimeCollectionFinder;
import java.io.IOException;
import org.joda.time.Interval;

/**
 *
 * @author Tim Boudreau
 */
final class TotalTimeResource extends Page {

    public static final String PAT = "^users/(.*?)/total/(.*?)$";

    @Inject
    public TotalTimeResource(ActeurFactory af) {
        add(af.matchPath(PAT));
        add(af.matchMethods(Method.GET, Method.HEAD));
        add(af.banParameters("type"));
        add(AuthenticationActeur.class);
        add(AuthorizedChecker.class);
        add(CreateCollectionPolicy.DONT_CREATE.toActeur());
        add(TimeCollectionFinder.class);
        add(TotalGetter.class);
    }

    @Override
    protected String getDescription() {
        return "Total times for query";
    }

    private static class TotalGetter extends Acteur {

        @Inject
        public TotalGetter(HttpEvent evt, DBCollection collection, BasicDBObject query) throws IOException {
            query.put(type, time);
            query.remove(detail);

            boolean detail = true;
            if (evt.urlParameter(Properties.detail) != null && "false".equals(evt.urlParameter(Properties.detail))) {
                detail = false;
            }
            boolean summary = true;
            if (evt.urlParameter(Properties.summary) != null && "false".equals(evt.urlParameter(Properties.summary))) {
                summary = false;
            }

            Intervals ivals = new Intervals();
            try (DBCursor cur = collection.find(query)) {
                for (; cur.hasNext();) {
                    DBObject ob = cur.next();
                    Long startTime = (Long) ob.get(start);
                    Long endTime = (Long) ob.get(end);
                    ivals.add(new Interval(startTime, endTime), "" + ob.get("_id"));
                }
            }
            ok(ivals.toJSON(detail, summary));
        }
    }
}
