/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014-2022 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.rest;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import java.io.PrintWriter;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Filter to add SameSite=None attribute to session cookies (JSESSIONID). This is required for EAP 6.4 which does not
 * natively support SameSite cookie attribute.
 *
 * The HttpOnly and Secure flags are configured in web.xml via session-config.
 */
@WebFilter(filterName = "SameSiteCookieFilter", urlPatterns = { "/*" })
public class SameSiteCookieFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletResponse httpResponse = (HttpServletResponse) response;

        SameSiteResponseWrapper wrappedResponse = new SameSiteResponseWrapper(httpResponse);
        try {
            chain.doFilter(request, wrappedResponse);
        } finally {
            wrappedResponse.rewriteCookiesIfNecessary();
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void destroy() {
    }

    private static class SameSiteResponseWrapper extends HttpServletResponseWrapper {
        private final HttpServletResponse nativeResponse;
        private boolean headersRewritten = false;

        public SameSiteResponseWrapper(HttpServletResponse response) {
            super(response);
            this.nativeResponse = response;
        }

        public void rewriteCookiesIfNecessary() {
            if (headersRewritten) {
                return;
            }
            synchronized (this) {
                if (headersRewritten) {
                    return;
                }
                headersRewritten = true;
                if (!nativeResponse.isCommitted()) {
                    Collection<String> headers = nativeResponse.getHeaders("Set-Cookie");
                    if (headers != null && !headers.isEmpty()) {
                        List<String> updatedHeaders = new ArrayList<>();
                        for (String header : headers) {
                            updatedHeaders.add(addSameSiteAttribute(header));
                        }
                        boolean first = true;
                        for (String updatedHeader : updatedHeaders) {
                            if (first) {
                                nativeResponse.setHeader("Set-Cookie", updatedHeader);
                                first = false;
                            } else {
                                nativeResponse.addHeader("Set-Cookie", updatedHeader);
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void addCookie(Cookie cookie) {
            String headerValue = formatCookie(cookie);
            addHeader("Set-Cookie", headerValue);
        }

        private String formatCookie(Cookie cookie) {
            StringBuilder sb = new StringBuilder();
            sb.append(cookie.getName()).append("=").append(cookie.getValue() != null ? cookie.getValue() : "");
            if (cookie.getPath() != null && !cookie.getPath().isEmpty()) {
                sb.append("; Path=").append(cookie.getPath());
            }
            if (cookie.getDomain() != null && !cookie.getDomain().isEmpty()) {
                sb.append("; Domain=").append(cookie.getDomain());
            }
            if (cookie.getMaxAge() >= 0) {
                sb.append("; Max-Age=").append(cookie.getMaxAge());
            }
            if (cookie.getSecure()) {
                sb.append("; Secure");
            }
            if (cookie.isHttpOnly()) {
                sb.append("; HttpOnly");
            }
            return sb.toString();
        }

        @Override
        public void addHeader(String name, String value) {
            if ("Set-Cookie".equalsIgnoreCase(name)) {
                value = addSameSiteAttribute(value);
            }
            super.addHeader(name, value);
        }

        @Override
        public void setHeader(String name, String value) {
            if ("Set-Cookie".equalsIgnoreCase(name)) {
                value = addSameSiteAttribute(value);
            }
            super.setHeader(name, value);
        }

        @Override
        public void flushBuffer() throws IOException {
            rewriteCookiesIfNecessary();
            super.flushBuffer();
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            final ServletOutputStream originalOut = super.getOutputStream();
            return new ServletOutputStream() {
                @Override
                public void write(int b) throws IOException {
                    rewriteCookiesIfNecessary();
                    originalOut.write(b);
                }

                @Override
                public void write(byte[] b) throws IOException {
                    rewriteCookiesIfNecessary();
                    originalOut.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    rewriteCookiesIfNecessary();
                    originalOut.write(b, off, len);
                }

                @Override
                public void flush() throws IOException {
                    rewriteCookiesIfNecessary();
                    originalOut.flush();
                }

                @Override
                public void close() throws IOException {
                    rewriteCookiesIfNecessary();
                    originalOut.close();
                }

                @Override
                public boolean isReady() {
                    return originalOut.isReady();
                }

                @Override
                public void setWriteListener(javax.servlet.WriteListener writeListener) {
                    originalOut.setWriteListener(writeListener);
                }
            };
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            rewriteCookiesIfNecessary();
            final PrintWriter originalWriter = super.getWriter();
            return new PrintWriter(originalWriter) {
                @Override
                public void write(int c) {
                    rewriteCookiesIfNecessary();
                    super.write(c);
                }

                @Override
                public void write(char[] buf, int off, int len) {
                    rewriteCookiesIfNecessary();
                    super.write(buf, off, len);
                }

                @Override
                public void write(String s, int off, int len) {
                    rewriteCookiesIfNecessary();
                    super.write(s, off, len);
                }

                @Override
                public void flush() {
                    rewriteCookiesIfNecessary();
                    super.flush();
                }

                @Override
                public void close() {
                    rewriteCookiesIfNecessary();
                    super.close();
                }
            };
        }

        private String addSameSiteAttribute(String cookieValue) {
            if (cookieValue == null) {
                return null;
            }

            // Check if cookie is JSESSIONID and doesn't already have SameSite attribute
            if (cookieValue.startsWith("JSESSIONID=") && !cookieValue.contains("SameSite=")) {
                return cookieValue + "; SameSite=None";
            }

            return cookieValue;
        }
    }
}
