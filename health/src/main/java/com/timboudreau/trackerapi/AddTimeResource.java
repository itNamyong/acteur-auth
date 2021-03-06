package com.timboudreau.trackerapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.ActeurFactory;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.auth.AuthenticationActeur;
import com.mastfrog.acteur.mongo.userstore.TTUser;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.WriteConcern;
import static com.timboudreau.trackerapi.Properties.*;
import static com.timboudreau.trackerapi.RecordTimeConnectionIsOpenResource.LiveTime.buildQueryFromURLParameters;
import static com.timboudreau.trackerapi.RecordTimeConnectionIsOpenResource.XLI;
import static com.timboudreau.trackerapi.RecordTimeConnectionIsOpenResource.XTI;
import com.timboudreau.trackerapi.support.AuthorizedChecker;
import com.timboudreau.trackerapi.support.CreateCollectionPolicy;
import com.timboudreau.trackerapi.support.TimeCollectionFinder;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.util.Map;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Interval;

/**
 *
 * @author Tim Boudreau
 */
final class AddTimeResource extends Page {

    @Inject
    AddTimeResource(ActeurFactory af) {
        add(af.matchPath(Timetracker.URL_PATTERN_TIME));
        add(af.matchMethods(Method.PUT, Method.POST));
        add(af.requireParameters("start", "end"));
        add(af.banParameters("added", "type"));
        add(CheckParameters.class);
        add(CreateCollectionPolicy.CREATE.toActeur());
        add(AuthenticationActeur.class);
        add(AuthorizedChecker.class);
        add(TimeCollectionFinder.class);
        add(TimeAdder.class);
    }

    @Override
    protected String getDescription() {
        return "Add A Time Event";
    }

    private static class CheckParameters extends Acteur {

        @Inject
        CheckParameters(HttpEvent evt) {
            System.out.println("AddTimeResource checkParameters");
            try {
                DateTime startTime = new DateTime(evt.longUrlParameter(start).get());
                DateTime endTime = new DateTime(evt.longUrlParameter(end).get());
                DateTime now = DateTime.now();
                DateTime twentyYearsAgo = now.minus(Duration.standardDays(365 * 20));
                if (twentyYearsAgo.isAfter(startTime)) {
                    setState(new RespondWith(HttpResponseStatus.BAD_REQUEST,
                            "Too long ago - minimum is " + twentyYearsAgo));
                    return;
                }
                Interval interval = new Interval(startTime, endTime);
                next(interval);
            } catch (NumberFormatException e) {
                badRequest("Start or end is not a number: '" + evt.urlParameter(start) + "' and '" + evt.urlParameter(end));
            }
        }
    }
    static final int MAX_PROPERTIES = 10;

    private static class TimeAdder extends Acteur {

        @Inject
        TimeAdder(HttpEvent evt, DBCollection coll, ObjectMapper mapper, TTUser user, Interval interval) throws IOException {
            long startVal = interval.getStartMillis();
            long endVal = interval.getEndMillis();
            // XXX ability to add for a different user?
            if (endVal - startVal <= 0) {
                badRequest("Start is equal to or after end '"
                        + interval.getStart() + "' and '" + interval.getEnd() + "'");
                return;
            }
            BasicDBObject toWrite = new BasicDBObject(type, time)
                    .append(start, startVal)
                    .append(end, endVal)
                    .append(duration, endVal - startVal)
                    .append(added, DateTime.now().getMillis())
                    .append(by, user.idAsString())
                    .append(version, 0);

            if (toWrite.get(start) instanceof String) {
                throw new IOException("Bad bad bad: " + toWrite.get(start));
            }

            String err = buildQueryFromURLParameters(evt, toWrite, Properties.start, Properties.end, Properties.duration);
            if (err != null) {
                badRequest(err);
                return;
            }
            if (toWrite.get(start) instanceof String) {
                throw new IOException("Bad bad bad: " + toWrite.get(start));
            }
            coll.insert(toWrite, WriteConcern.SAFE);
            Map m = toWrite.toMap();
            ObjectId id = (ObjectId) m.get(_id);
            if (id != null) {
                add(XTI, id.toString());
                if (evt.urlParameter("localId") != null) {
                    add(XLI, evt.urlParameter("localId"));
                }
            }
            setState(new RespondWith(HttpResponseStatus.ACCEPTED, mapper.writeValueAsString(m)));
        }
    }
}
