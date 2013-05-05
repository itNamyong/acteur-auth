package com.mastfrog.netty.http.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;
import com.mastfrog.acteur.util.BasicCredentials;
import com.mastfrog.acteur.util.HeaderValueType;
import com.mastfrog.acteur.util.Headers;
import com.mastfrog.acteur.util.Method;
import com.mastfrog.url.Protocol;
import com.mastfrog.url.URL;
import com.mastfrog.url.URLBuilder;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.Streams;
import com.mastfrog.util.thread.Receiver;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.joda.time.DateTime;

/**
 *
 * @author tim
 */
abstract class RequestBuilder implements HttpRequestBuilder {

    private URLBuilder url = URL.builder();
    final List<Entry<?>> entries = new LinkedList<>();
    private final Method method;
    private HttpVersion version = HttpVersion.HTTP_1_1;

    RequestBuilder(Method method) {
        this.method = method;
    }

    @Override
    public RequestBuilder setURL(URL url) {
        this.url = URL.builder(url);
        return this;
    }

    @Override
    public RequestBuilder addPathElement(String element) {
        url.addPathElement(element);
        return this;
    }

    @Override
    public RequestBuilder addQueryPair(String key, String value) {
        url.addQueryPair(key, value);
        return this;
    }

    @Override
    public RequestBuilder setProtocol(Protocol protocol) {
        url.setProtocol(protocol);
        return this;
    }

    @Override
    public RequestBuilder setAnchor(String anchor) {
        url.setAnchor(anchor);
        return this;
    }

    @Override
    public RequestBuilder setPassword(String password) {
        url.setPassword(password);
        return this;
    }

    @Override
    public RequestBuilder setPath(String path) {
        url.setPath(path);
        return this;
    }

    @Override
    public RequestBuilder setPort(int port) {
        url.setPort(port);
        return this;
    }

    @Override
    public RequestBuilder setUserName(String userName) {
        url.setUserName(userName);
        return this;
    }

    @Override
    public RequestBuilder setHost(String host) {
        url.setHost(host);
        return this;
    }

    public RequestBuilder basicAuthentication(String username, String password) {
        addHeader(Headers.AUTHORIZATION, new BasicCredentials(username, password));
        return this;
    }

    @Override
    public <T> RequestBuilder addHeader(HeaderValueType<T> type, T value) {
        entries.add(new Entry<>(type, value));
        return this;
    }

    URL getURL() {
        return url.create();
    }

    public HttpRequest build() {
        if (url == null) {
            throw new IllegalStateException("URL not set");
        }
        URL u = getURL();
        System.out.println("BUILD WITH " + entries.size() + " entries");
        String uri = u.getPathAndQuery();
        if (uri.isEmpty()) {
            uri = "/";
        }
        HttpMethod mth = HttpMethod.valueOf(method.name());
        DefaultHttpRequest h = body == null
                ? new DefaultHttpRequest(version, mth, uri)
                : new DefaultFullHttpRequest(version, mth, uri, body);
        h.headers().add(HttpHeaders.Names.HOST, u.getHost().toString());
        h.headers().add(HttpHeaders.Names.CONNECTION, "close");
        h.headers().add(HttpHeaders.Names.DATE, Headers.DATE.toString(DateTime.now()));
        System.out.println("HAVE A BODY? " + body);
        System.out.println("REQ: " + mth + " " + uri + " on " + u.getHost());
        System.out.println("HAVE " + entries.size() + " header entries");
        for (Entry<?> e : entries) {
            e.addTo(h.headers());
        }
        for (Map.Entry<String, String> e : h.headers().entries()) {
            System.out.println(e.getKey() + ": " + e.getValue());
        }
        return h;
    }
    
    public URL toURL() {
        return url.create();
    }

    private ByteBuf body;

    @Override
    public HttpRequestBuilder setBody(Object o, MediaType contentType) throws IOException {
        System.out.println("setBody " + o);
        if (o instanceof CharSequence) {
            CharSequence seq = (CharSequence) o;
            setBody(seq.toString().getBytes(CharsetUtil.UTF_8), contentType);
        } else if (o instanceof byte[]) {
            setBody(Unpooled.wrappedBuffer((byte[]) o), contentType);
        } else if (o instanceof ByteBuf) {
            body = (ByteBuf) o;
            addHeader(Headers.stringHeader(HttpHeaders.Names.EXPECT), HttpHeaders.Values.CONTINUE);
            addHeader(Headers.CONTENT_LENGTH, (long) body.readableBytes());
            addHeader(Headers.CONTENT_TYPE, contentType);
        } else if (o instanceof InputStream) {
            ByteBuf buf = newByteBuf();
            try (ByteBufOutputStream out = new ByteBufOutputStream(buf)) {
                try (InputStream in = (InputStream) o) {
                    Streams.copy(in, out, 1024);
                }
            }
            setBody(buf, contentType);
        } else if (o instanceof RenderedImage) {
            ByteBuf buf = newByteBuf();
            try (ByteBufOutputStream out = new ByteBufOutputStream(buf)) {
                String type = contentType.subtype();
                if ("jpeg".equals(type)) {
                    type = "jpg";
                }
                ImageIO.write((RenderedImage) o, type, out);
            }
            setBody(buf, contentType);
        } else {
            try {
                setBody(new ObjectMapper().writeValueAsBytes(o), contentType);
            } catch (Exception ex) {
                throw new IllegalArgumentException(ex);
            }
        }
        return this;
    }

    protected final List<Receiver<State<?>>> any = new LinkedList<>();

    protected ByteBuf newByteBuf() {
        return Unpooled.buffer();
    }

    @Override
    public HttpRequestBuilder onEvent(Receiver<State<?>> r) {
        any.add(r);
        return this;
    }

    protected final List<HandlerEntry<?>> handlers = new LinkedList<>();

    @Override
    public <T> HttpRequestBuilder on(Class<? extends State<T>> event, Receiver<T> r) {
        HandlerEntry<T> h = null;
        for (HandlerEntry<?> e : handlers) {
            if (e.state.equals(event)) {
                h = (HandlerEntry<T>) e;
                break;
            }
        }
        if (h == null) {
            h = new HandlerEntry<T>((Class<State<T>>) event);
            handlers.add(h);
        }
        h.add(r);
        return this;
    }

    @Override
    public HttpRequestBuilder setURL(String url) {
        setURL(URL.parse(url));
        return this;
    }

    private static final class Entry<T> {

        private final HeaderValueType<T> type;
        private final T value;

        public Entry(HeaderValueType<T> type, T value) {
            this.type = type;
            this.value = value;
        }

        void addTo(HttpHeaders h) {
            h.add(type.name(), type.toString(value));
        }
    }
}
