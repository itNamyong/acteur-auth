package com.mastfrog.acteur.auth;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.auth.UserFactory.LoginState;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Takes care of talking to an OAuth provider
 *
 * @author Tim Boudreau
 */
public abstract class OAuthPlugin<CredentialType> {

    protected final String code;
    protected final String name;
    private final String logoUrl;
    protected final OAuthPlugins plugins;

    public OAuthPlugin(String name, String code, String logoUrl, OAuthPlugins plugins) {
        this.code = code;
        this.name = name;
        this.logoUrl = logoUrl;
        this.plugins = plugins;
        plugins.register(this);
    }

    /**
     * The human-readable display name of the service
     * @return The name
     */
    public String name() {
        return name;
    }

    /**
     * Get the code for this oauth provider.  The code is something you
     * invent - it is used as a cookie name and to reference slugs in the
     * database - to keep cookie size down, keep this to two characters or so.
     * @return 
     */
    public String code() {
        return code;
    }

    /**
     * Get a url for a logo for this service
     * @return 
     */
    public String getLogoUrl() {
        return logoUrl;
    }
    
    /**
     * Get the maximum time a cookie that keeps the user logged in should
     * live.
     * @return The duration
     */
    public Duration getSlugMaxAge() {
        return plugins.slugMaxAge();
    }

    /**
     * Extract a "state" parameter from an HTTP request generated by
     * an OAuth authorization page redirecting back to this server.
     * The state string is a random string which was created and stored when
     * the user was redirected, and simply serves to distinguish legitimate
     * callbacks (since the number of retained state strings is small and they
     * are long and difficult to guess)..
     * 
     * @param evt An http request
     * @return The state string or null
     */
    public abstract String stateForEvent(HttpEvent evt);

    /**
     * Get the redirect URL needed to transfer control to a remote OAuth
     * service.  The URL should include a state string.
     * 
     * @param state The state, which is stored locally (i.e. in a frequently
     * cleaned-out tabel in the database or similar).
     * 
     * @return A url
     */
    public abstract String getRedirectURL(LoginState state);

    /**
     * Get an implementation-specific credential from an HTTP request;
     * this may involve connecting to a remote auth service and to
     * exchange a callback code for a credential.
     * 
     * @param evt An http request
     * @return The credential or null
     */
    public abstract CredentialType credentialForEvent(HttpEvent evt);

    public abstract boolean revalidateCredential(String userName, String accessToken);
    
    protected String credentialToString(CredentialType credential) {
        return credential.toString();
    }
    
    final <T> void saveToken(UserFactory<T> uf, T user, CredentialType credential) {
        uf.putAccessToken(user, credentialToString(credential), code());
    }

    /**
     * Take a credential and fetch enough info about the user in the
     * remote service to be able to create a local user.
     * 
     * @param credential An implementation-specific credential taken from
     * a callback request
     * @return User info, or null if none can be obtained
     */
    public abstract RemoteUserInfo getRemoteUserInfo(CredentialType credential) throws IOException, JsonParseException, JsonMappingException;
    
    public <T> String getUserPictureURL(UserFactory<T> uf, T user) {
        Map<String,Object> m = uf.getData(user, this);
        return m == null ? null : getUserPictureURL(m);
    }
    
    protected String getUserPictureURL(Map<String,Object> data) {
        return null;
    }

    @Override
    public String toString() {
        return code + " (" + name + ")";
    }

    public interface RemoteUserInfo extends Map<String,Object> {

        public String userName();

        public String displayName();

        public Object get(String key);
    }

}
