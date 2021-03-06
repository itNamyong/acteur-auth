package com.timboudreau.trackerapi;

import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.timboudreau.trackerapi.support.CreateCollectionPolicy;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.annotations.Defaults;
import com.mastfrog.giulius.annotations.Namespace;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.ImplicitBindings;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.auth.OAuthPlugins;
import com.mastfrog.acteur.mongo.CursorWriter.MapFilter;
import com.mastfrog.acteur.mongo.userstore.TTUser;
import com.mastfrog.acteur.server.PathFactory;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.CacheControl;
import com.mastfrog.acteur.util.CacheControlTypes;
import com.mastfrog.acteur.headers.Headers;
import com.mastfrog.acteur.headers.Method;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.timboudreau.questions.AddSurveyResource;
import com.timboudreau.questions.GetSurveyResource;
import com.timboudreau.questions.GetSurveysResource;
import com.timboudreau.questions.Subscribe;
import com.timboudreau.questions.UpdateSurveyResource;
import com.timboudreau.trackerapi.ModifyEventsResource.Body;
import io.netty.handler.codec.http.HttpResponse;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.bson.types.ObjectId;
import org.joda.time.Interval;

/**
 * The Timetracker main class
 *
 * @author Tim Boudreau
 */
// Classes which are injected:
@ImplicitBindings({TTUser.class, DBCollection.class, CreateCollectionPolicy.class,
    DBCursor.class, Interval.class, Body.class, String.class, DBObject.class,
    ObjectId.class, AdjustTimeResource.AdjustParameters.class, MapFilter.class})
@Defaults(namespace =
        @Namespace(Timetracker.TIMETRACKER),
        value = {"periodicLiveWrites=true", "port=7739"})
@Namespace(Timetracker.TIMETRACKER)
public class Timetracker extends Application {

    public static final String TIMETRACKER = "timetracker";
    public static final String URL_PATTERN_TIME = "^users/(.*?)/time/(.*?)$";

    public static final String REALM_NAME = "Surv";

    public static void main(String[] args) throws IOException, InterruptedException {
        
        Map<Character,String> shortcuts = new HashMap<>();
        shortcuts.put('p', "port");
        
        // Set up our defaults - can be overridden in 
        // /etc/timetracker.json, ~/timetracker.json and ./timetracker.json
        Settings settings = SettingsBuilder.forNamespace(TIMETRACKER)
                .addDefaultLocations()
                .addLocation(new File("/etc"))
                .add(PathFactory.BASE_PATH_SETTINGS_KEY, "time")
//                .add("neverKeepAlive", "true")
                .parseCommandLineArguments(shortcuts, args)
                .build();
        
        // Set up the Guice injector
        Dependencies deps = Dependencies.builder()
                .add(settings, TIMETRACKER).
                add(settings, Namespace.DEFAULT).add(
                new ServerModule<>(Timetracker.class),
                new TimetrackerAppModule(settings)).build();

        // Insantiate the server, start it and wait for it to exit
        Server server = deps.getInstance(Server.class);
        server.start(settings.getInt("port", 7739)).await();
    }

    @Inject
    Timetracker(DB db, OAuthPlugins plugins) {
        // These are our request handlers:
        super(SignUpResource.class,
                Subscribe.class,
                WhoAmIResource.class,
//                TestLogin.class,
                plugins.testLoginPageType(),
                SetsResource.class,
                CORSResource.class,
                GetSurveysResource.class,
                GetSurveyResource.class,
                AddSurveyResource.class,
                UpdateSurveyResource.class,
                AddTimeResource.class,
                DeauthorizeResource.class,
                AuthorizeResource.class,
                SharesWithMeResources.class,
                SetPasswordResource.class,
                DistinctResource.class,
                GetTimeResource.class,
                DeleteTimeResource.class,
                TotalTimeResource.class,
                ModifyEventsResource.class,
                //                OAuth2CallbackPage.class,
                //                GoogleLoginPage.class,
                plugins.bouncePageType(),
                plugins.landingPageType(),
                plugins.listOAuthProvidersPageType(),
                AdjustTimeResource.class,
                SkewResource.class,
                ListUsersResource.class,
                RecordTimeConnectionIsOpenResource.class,
                EditUserResource.class,
                Application.helpPageType());
        db.getCollection("users");
    }

    @Override
    protected HttpResponse decorateResponse(Event<?> event, Page page, Acteur action, HttpResponse response) {
        response.headers().add("Server", getName());
        // Do no-cache cache control headers for everything
        if (((HttpEvent)event).method() != Method.OPTIONS) {
            CacheControl cc = new CacheControl(CacheControlTypes.Private).add(
                    CacheControlTypes.no_cache).add(CacheControlTypes.no_store);
            response.headers().add(Headers.CACHE_CONTROL.name(), Headers.CACHE_CONTROL.toCharSequence(cc));
        }
        // We do JSON for everything, so save setting the content type on every page
        int code = response.getStatus().code();
        if (code >= 200 && code < 300) {
            if (response.headers().get(Headers.CONTENT_TYPE.name()) == null) {
                Headers.write(Headers.CONTENT_TYPE, MediaType.JSON_UTF_8, response);
            }
        }
        return super.decorateResponse(event, page, action, response);
    }

    public static String quickJson(String key, Object value) {
        StringBuilder sb = new StringBuilder("{").append('"').append(key).append('"').append(':');
        if (value instanceof String) {
            sb.append('"');
        }
        sb.append(value);
        if (value instanceof String) {
            sb.append('"');
        }
        sb.append("}\n");
        return sb.toString();
    }
}
