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
package co.elastic.apm.servlet;

import co.elastic.apm.MockReporter;
import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.context.Url;
import co.elastic.apm.matcher.WildcardMatcher;
import co.elastic.apm.util.PotentiallyMultiValuedMap;
import co.elastic.apm.web.WebConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.assertj.core.api.Java6Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApmFilterTest {

    private ApmFilter apmFilter;
    private MockReporter reporter;
    private ConfigurationRegistry config;
    private WebConfiguration webConfiguration;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        config = SpyConfiguration.createSpyConfig();
        ElasticApmTracer tracer = ElasticApmTracer.builder()
            .configurationRegistry(config)
            .reporter(reporter)
            .build();
        apmFilter = new ApmFilter(tracer);
        webConfiguration = config.getConfig(WebConfiguration.class);
    }

    @Test
    void testEndsTransaction() throws IOException, ServletException {
        apmFilter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(reporter.getTransactions()).hasSize(1);
    }

    @Test
    void testDisabled() throws IOException, ServletException {
        when(config.getConfig(CoreConfiguration.class).isActive()).thenReturn(false);
        apmFilter = spy(apmFilter);
        apmFilter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(reporter.getTransactions()).hasSize(0);
        verify(apmFilter, never()).captureTransaction(any(), any(), any());
    }

    @Test
    void testURLTransaction() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/foo/bar");
        request.setQueryString("foo=bar");
        apmFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        Url url = reporter.getFirstTransaction().getContext().getRequest().getUrl();
        assertThat(url.getProtocol()).isEqualTo("http");
        assertThat(url.getSearch()).isEqualTo("foo=bar");
        assertThat(url.getPort().toString()).isEqualTo("80");
        assertThat(url.getHostname()).isEqualTo("localhost");
        assertThat(url.getPathname()).isEqualTo("/foo/bar");
        assertThat(url.getFull().toString()).isEqualTo("http://localhost/foo/bar?foo=bar");
    }

    @Test
    void testTrackPostParams() throws IOException, ServletException {
        when(webConfiguration.getCaptureBody()).thenReturn(WebConfiguration.EventType.ALL);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/foo/bar");
        request.addParameter("foo", "bar");
        request.addParameter("baz", "qux", "quux");
        request.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=uft-8");

        apmFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getBody()).isInstanceOf(PotentiallyMultiValuedMap.class);
        PotentiallyMultiValuedMap<String, String> params = (PotentiallyMultiValuedMap<String, String>) reporter.getFirstTransaction()
            .getContext().getRequest().getBody();
        assertThat(params.get("foo")).isEqualTo("bar");
        assertThat(params.get("baz")).isEqualTo(Arrays.asList("qux", "quux"));
    }

    @Test
    void captureException() throws IOException, ServletException {
        final Servlet servlet = mock(Servlet.class);
        doThrow(new ServletException("Bazinga")).when(servlet).service(any(), any());
        assertThatThrownBy(() -> apmFilter.doFilter(
            new MockHttpServletRequest("GET", "/test"),
            new MockHttpServletResponse(), new MockFilterChain(servlet)))
            .isInstanceOf(ServletException.class);
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getMethod()).isEqualTo("GET");
        assertThat(reporter.getErrors()).hasSize(1);
        assertThat(reporter.getFirstError().getContext().getRequest().getMethod()).isEqualTo("GET");
        assertThat(reporter.getFirstError().getException().getMessage()).isEqualTo("Bazinga");
    }

    @Test
    void testIgnoreUrlStartWith() throws IOException, ServletException {
        when(webConfiguration.getIgnoreUrls()).thenReturn(Collections.singletonList(WildcardMatcher.valueOf("/resources*")));
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/resources/test.js");
        apmFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(reporter.getTransactions()).hasSize(0);
    }

    @Test
    void testIgnoreUrlStartWithNoMatch() throws IOException, ServletException {
        when(webConfiguration.getIgnoreUrls()).thenReturn(Collections.singletonList(WildcardMatcher.valueOf("/resources*")));
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/");
        apmFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(reporter.getTransactions()).hasSize(1);
    }

    @Test
    void testIgnoreUrlEndWith() throws IOException, ServletException {
        when(webConfiguration.getIgnoreUrls()).thenReturn(Collections.singletonList(WildcardMatcher.valueOf("*.js")));
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/resources");
        request.setPathInfo("test.js");
        apmFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(reporter.getTransactions()).hasSize(0);
    }

    @Test
    void testIgnoreUserAgentStartWith() throws IOException, ServletException {
        when(webConfiguration.getIgnoreUserAgents()).thenReturn(Collections.singletonList(WildcardMatcher.valueOf("curl/*")));
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("user-agent", "curl/7.54.0");
        apmFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(reporter.getTransactions()).hasSize(0);
    }

    @Test
    void testIgnoreUserAgentInfix() throws IOException, ServletException {
        when(webConfiguration.getIgnoreUserAgents()).thenReturn(Collections.singletonList(WildcardMatcher.valueOf("*pingdom*")));
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("user-agent", "Pingdom.com_bot_version_1.4_(http://www.pingdom.com)");
        apmFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(reporter.getTransactions()).hasSize(0);
    }
}
