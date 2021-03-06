/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.impl.context;

import co.elastic.apm.objectpool.Recyclable;
import co.elastic.apm.util.PotentiallyMultiValuedMap;

import javax.annotation.Nullable;


/**
 * Request
 * <p>
 * If a log record was generated as a result of a http request, the http interface can be used to collect this information.
 */
public class Request implements Recyclable {

    private final PotentiallyMultiValuedMap<String, String> postParams = new PotentiallyMultiValuedMap<>();
    /**
     * Should include any headers sent by the requester. Map<String, String> </String,>will be taken by headers if supplied.
     */
    private final PotentiallyMultiValuedMap<String, String> headers = new PotentiallyMultiValuedMap<>();
    private final Socket socket = new Socket();
    /**
     * A complete Url, with scheme, host and path.
     * (Required)
     */
    private final Url url = new Url();
    /**
     * A parsed key-value object of cookies
     */
    private final PotentiallyMultiValuedMap<String, String> cookies = new PotentiallyMultiValuedMap<>();
    /**
     * Data should only contain the request body (not the query string). It can either be a dictionary (for standard HTTP requests) or a raw request body.
     */
    @Nullable
    private String rawBody;
    /**
     * HTTP version.
     */
    @Nullable
    private String httpVersion;
    /**
     * HTTP method.
     * (Required)
     */
    @Nullable
    private String method;

    /**
     * Data should only contain the request body (not the query string). It can either be a dictionary (for standard HTTP requests) or a raw request body.
     */
    @Nullable
    public Object getBody() {
        if (!postParams.isEmpty()) {
            return postParams;
        } else {
            return rawBody;
        }
    }

    @Nullable
    public String getRawBody() {
        return rawBody;
    }

    public void redactBody() {
        postParams.clear();
        rawBody = "[REDACTED]";
    }

    public Request addFormUrlEncodedParameter(String key, String value) {
        this.postParams.add(key, value);
        return this;
    }

    public Request addFormUrlEncodedParameters(String key, String[] values) {
        for (String value : values) {
            this.postParams.add(key, value);
        }
        return this;
    }

    public Request withRawBody(String rawBody) {
        this.rawBody = rawBody;
        return this;
    }

    public PotentiallyMultiValuedMap<String, String> getFormUrlEncodedParameters() {
        return postParams;
    }

    /**
     * Adds a request header.
     *
     * @param headerName  The name of the header.
     * @param headerValue The value of the header.
     * @return {@code this}, for fluent method chaining
     */
    public Request addHeader(String headerName, String headerValue) {
        headers.add(headerName, headerValue);
        return this;
    }

    /**
     * Should include any headers sent by the requester.
     */
    public PotentiallyMultiValuedMap<String, String> getHeaders() {
        return headers;
    }

    /**
     * HTTP version.
     */
    @Nullable
    public String getHttpVersion() {
        return httpVersion;
    }

    public Request withHttpVersion(@Nullable String httpVersion) {
        this.httpVersion = httpVersion;
        return this;
    }

    /**
     * HTTP method.
     * (Required)
     */
    @Nullable
    public String getMethod() {
        return method;
    }

    public Request withMethod(@Nullable String method) {
        this.method = method;
        return this;
    }

    public Socket getSocket() {
        return socket;
    }

    /**
     * A complete Url, with scheme, host and path.
     * (Required)
     */
    public Url getUrl() {
        return url;
    }


    public Request addCookie(String cookieName, String cookieValue) {
        cookies.add(cookieName, cookieValue);
        return this;
    }

    /**
     * A parsed key-value object of cookies
     */
    public PotentiallyMultiValuedMap<String, String> getCookies() {
        return cookies;
    }

    @Override
    public void resetState() {
        rawBody = null;
        postParams.clear();
        headers.clear();
        httpVersion = null;
        method = null;
        socket.resetState();
        url.resetState();
        cookies.clear();
    }

    public void copyFrom(Request other) {
        this.rawBody = other.rawBody;
        this.postParams.putAll(other.postParams);
        this.headers.putAll(other.headers);
        this.httpVersion = other.httpVersion;
        this.method = other.method;
        this.socket.copyFrom(other.socket);
        this.url.copyFrom(other.url);
        this.cookies.putAll(other.cookies);
    }

    public boolean hasContent() {
        return method != null ||
            headers.size() > 0 ||
            httpVersion != null ||
            cookies.size() > 0 ||
            rawBody != null ||
            postParams.size() > 0 ||
            socket.hasContent() ||
            url.hasContent();
    }
}
