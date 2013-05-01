package com.mastfrog.acteur.auth;

/**
 *
 * @author Tim Boudreau
 */
final class Result<UserType> {

    public final UserType user;
    public final String username;
    public final String hashedPass;
    public final ResultType type;
    public final boolean cookie;

    public Result(ResultType type, String username, boolean cookie) {
        this(null, username, null, type, cookie);
    }

    public Result(ResultType type, boolean cookie) {
        this(null, null, null, type, cookie);
    }

    public Result(UserType user, String username, String hashedPass, ResultType type, boolean cookie) {
        this.user = user;
        this.username = username;
        this.hashedPass = hashedPass;
        this.type = type;
        this.cookie = cookie;
    }

    static Result combined(Result a, Result b) {
        // We want cookie to be true if a cookie was present
        boolean ck = a.isSuccess() && a.cookie;
        if (!ck) {
            ck = b.isSuccess() && b.cookie;
        }
        return new Result(a.user == null ? b.user : null, a.username == null ? b.username : null, a.hashedPass == null ? b.hashedPass : a.hashedPass, a.type, ck);
    }

    public boolean isSuccess() {
        return type.isSuccess();
    }

    @Override
    public String toString() {
        return "Result{" + "user=" + user + ", username=" + username + ", hashedPass=" + hashedPass + ", type=" + type + ", cookie=" + cookie + '}';
    }
}
