package com.kaiyue;

import com.kaiyue.connector.HttpConnectoer;
import com.kaiyue.connector.SimpleHttpServer;
import com.kaiyue.engine.filter.HelloFilter;
import com.kaiyue.engine.listener.HelloHttpSessionAttributeListener;
import com.kaiyue.engine.servlet.HelloServlet;
import com.kaiyue.engine.servlet.IndexServlet;
import com.kaiyue.engine.servlet.LoginServlet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.EventListener;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ShaunSheepServer 综合测试")
public class ServerTest {

    static final int PORT = 18080;
    static final String BASE = "http://localhost:" + PORT;
    static SimpleHttpServer server;
    static HttpClient client;

    @BeforeAll
    static void startServer() throws IOException, ServletException {
        List<Class> servletClasses = List.of(IndexServlet.class, HelloServlet.class, LoginServlet.class);
        List<Class<?>> filterClasses = List.of(HelloFilter.class);
        List<Class<? extends EventListener>> listenerClasses = List.of(HelloHttpSessionAttributeListener.class);
        HttpConnectoer connector = new HttpConnectoer(servletClasses, filterClasses, listenerClasses);
        connector.servletContext.invokeServletContextInitialized();
        server = new SimpleHttpServer("0.0.0.0", PORT, connector);

        client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    HttpRequest.Builder req(String path) {
        return HttpRequest.newBuilder().uri(URI.create(BASE + path));
    }

    HttpResponse<String> get(String path) throws Exception {
        return client.send(req(path).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(req(path)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Nested
    @DisplayName("1. Servlet 路由测试")
    class ServletRoutingTest {

        @Test
        @DisplayName("/hello 默认参数返回 Hello, World!")
        void testHelloDefault() throws Exception {
            HttpResponse<String> resp = get("/hello");
            assertEquals(200, resp.statusCode());
            assertTrue(resp.body().contains("Hello, World!"));
        }

        @Test
        @DisplayName("/hello?name=Claude 返回 Hello, Claude!")
        void testHelloWithName() throws Exception {
            HttpResponse<String> resp = get("/hello?name=Claude");
            assertEquals(200, resp.statusCode());
            assertTrue(resp.body().contains("Hello, Claude!"));
        }

        @Test
        @DisplayName("/hello?name=中文 支持中文参数")
        void testHelloChinese() throws Exception {
            HttpResponse<String> resp = get("/hello?name=世界");
            assertEquals(200, resp.statusCode());
            assertTrue(resp.body().contains("Hello, 世界!"));
        }
    }

    @Nested
    @DisplayName("2. Filter 拦截测试")
    class FilterTest {

        @Test
        @DisplayName("name=123 被 HelloFilter 拦截返回错误提示")
        void testFilterBlocks123() throws Exception {
            HttpResponse<String> resp = get("/hello?name=123");
            assertEquals(200, resp.statusCode());
            assertTrue(resp.body().contains("Invalid name parameter"));
        }

        @Test
        @DisplayName("name=normal 正常通过 Filter")
        void testFilterPassesNormal() throws Exception {
            HttpResponse<String> resp = get("/hello?name=normal");
            assertEquals(200, resp.statusCode());
            assertTrue(resp.body().contains("Filtered by: HelloFilter"));
            assertTrue(resp.body().contains("Hello, normal!"));
        }
    }

    @Nested
    @DisplayName("3. 404 处理测试")
    class NotFoundTest {

        @Test
        @DisplayName("不存在的路径返回 404")
        void test404() throws Exception {
            HttpResponse<String> resp = get("/nonexist");
            assertEquals(200, resp.statusCode());
            assertTrue(resp.body().contains("404 Not Found"));
        }
    }

    @Nested
    @DisplayName("4. Session 登录流程测试")
    class SessionTest {

        @Test
        @DisplayName("未登录访问 / 重定向到 /login")
        void testIndexRedirectToLogin() throws Exception {
            HttpResponse<String> resp = get("/");
            assertEquals(302, resp.statusCode());
            String location = resp.headers().firstValue("Location").orElse("");
            assertEquals("/login", location);
        }

        @Test
        @DisplayName("GET 方式登录成功重定向到 /")
        void testLoginSuccessByGet() throws Exception {
            HttpResponse<String> resp = get("/login?username=admin&password=admin");
            assertEquals(302, resp.statusCode());
            String location = resp.headers().firstValue("Location").orElse("");
            assertEquals("/", location);
            String cookie = resp.headers().firstValue("Set-Cookie").orElse("");
            assertTrue(cookie.contains("JSESSIONID"), "登录成功应设置 JSESSIONID Cookie");
        }

        @Test
        @DisplayName("GET 方式登录失败重定向到 /error")
        void testLoginFailByGet() throws Exception {
            HttpResponse<String> resp = get("/login?username=wrong&password=wrong");
            assertEquals(302, resp.statusCode());
            String location = resp.headers().firstValue("Location").orElse("");
            assertEquals("/error", location);
        }

        @Test
        @DisplayName("POST /login (LoginServlet 不支持 POST)")
        void testLoginByPost() throws Exception {
            HttpResponse<String> resp = post("/login", "username=admin&password=admin");
            assertTrue(resp.statusCode() == 405 || resp.statusCode() == 302);
        }
    }

    @Nested
    @DisplayName("5. 完整 Session 会话流程测试")
    class FullSessionFlowTest {

        @Test
        @DisplayName("登录后访问 / 显示欢迎页面")
        void testFullLoginFlow() throws Exception {
            CookieManager cookieManager = new CookieManager();
            HttpClient sessionClient = HttpClient.newBuilder()
                    .cookieHandler(cookieManager)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

            HttpRequest loginReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/login?username=admin&password=admin"))
                    .GET().build();
            HttpResponse<String> loginResp = sessionClient.send(loginReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(302, loginResp.statusCode(), "登录应返回 302");
            assertEquals("/", loginResp.headers().firstValue("Location").orElse(""));

            CookieStore store = cookieManager.getCookieStore();
            boolean hasSessionId = store.getCookies().stream()
                    .anyMatch(c -> "JSESSIONID".equals(c.getName()));
            assertTrue(hasSessionId, "Cookie 中应包含 JSESSIONID");

            HttpRequest indexReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/"))
                    .GET().build();
            HttpResponse<String> indexResp = sessionClient.send(indexReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, indexResp.statusCode(), "登录后访问首页应返回 200");
            assertTrue(indexResp.body().contains("Welcome"), "登录后应显示 Welcome 页面");
        }

        @Test
        @DisplayName("登录后跟随重定向能看到欢迎页")
        void testSessionCookie() throws Exception {
            CookieManager cm = new CookieManager();
            HttpClient redirectClient = HttpClient.newBuilder()
                    .cookieHandler(cm)
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

            HttpRequest loginReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/login?username=admin&password=admin"))
                    .GET().build();
            HttpResponse<String> resp = redirectClient.send(loginReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode());
            assertTrue(resp.body().contains("Welcome") || resp.body().contains("Hello"),
                    "登录后跟随重定向应看到欢迎页或 Hello 页");
        }
    }

    @Nested
    @DisplayName("6. 响应头测试")
    class ResponseHeaderTest {

        @Test
        @DisplayName("HelloServlet 返回 Content-Type: text/html")
        void testContentType() throws Exception {
            HttpResponse<String> resp = get("/hello");
            String contentType = resp.headers().firstValue("Content-Type").orElse("");
            assertTrue(contentType.contains("text/html"), "响应应包含 Content-Type: text/html");
        }

        @Test
        @DisplayName("LoginServlet 重定向响应包含 Location 头")
        void testRedirectHasLocation() throws Exception {
            HttpResponse<String> resp = get("/login");
            assertEquals(302, resp.statusCode());
            assertTrue(resp.headers().firstValue("Location").isPresent(), "302 响应应包含 Location 头");
        }
    }

    @Nested
    @DisplayName("7. 多请求测试")
    class ConcurrencyTest {

        @Test
        @DisplayName("10 个请求都能正常响应")
        void testConcurrentRequests() throws Exception {
            var futures = new java.util.ArrayList<java.util.concurrent.CompletableFuture<HttpResponse<String>>>();
            for (int i = 0; i < 10; i++) {
                final int idx = i;
                futures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return get("/hello?name=User" + idx);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
            }
            java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
            for (var f : futures) {
                HttpResponse<String> resp = f.join();
                assertEquals(200, resp.statusCode());
                assertTrue(resp.body().contains("Hello, User"));
            }
        }
    }
}
