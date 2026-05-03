package com.kaiyue;

import com.kaiyue.connector.HttpConnectoer;
import com.kaiyue.connector.SimpleHttpServer;
import com.kaiyue.engine.filter.HelloFilter;
import com.kaiyue.engine.listener.HelloHttpSessionAttributeListener;
import com.kaiyue.engine.servlet.HelloServlet;
import com.kaiyue.engine.servlet.IndexServlet;
import com.kaiyue.engine.servlet.LoginServlet;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;

public class TestRunner {

    static final int PORT = 18080;
    static final String BASE = "http://localhost:" + PORT;
    static SimpleHttpServer server;
    static HttpClient client;

    static int passed = 0;
    static int failed = 0;
    static final List<String> failures = new ArrayList<>();

    interface TestCase {
        void run() throws Exception;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=" .repeat(60));
        System.out.println("  ShaunSheepServer 测试套件");
        System.out.println("=" .repeat(60));

        startServer();
        runTests();
        stopServer();

        System.out.println("=" .repeat(60));
        System.out.printf("  结果: %d 通过, %d 失败, 共 %d 测试%n", passed, failed, passed + failed);
        if (!failures.isEmpty()) {
            System.out.println("  失败的测试:");
            for (String f : failures) {
                System.out.println("    - " + f);
            }
        }
        System.out.println("=" .repeat(60));
        System.exit(failed > 0 ? 1 : 0);
    }

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
        System.out.println("[OK] 服务器已启动: " + BASE);
    }

    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
        System.out.println("[OK] 服务器已关闭");
    }

    static HttpRequest.Builder req(String path) {
        return HttpRequest.newBuilder().uri(URI.create(BASE + path));
    }

    static HttpResponse<String> get(String path) throws Exception {
        return client.send(req(path).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(req(path)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    static void test(String name, TestCase tc) {
        try {
            tc.run();
            passed++;
            System.out.println("  [PASS] " + name);
        } catch (AssertionError | Exception e) {
            failed++;
            failures.add(name);
            System.out.println("  [FAIL] " + name + " - " + e.getMessage());
        }
    }

    static void assertEquals(int expected, int actual, String msg) {
        if (expected != actual) {
            throw new AssertionError(msg + " - 期望 " + expected + " 但得到 " + actual);
        }
    }

    static void assertEquals(String expected, String actual, String msg) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(msg + " - 期望 \"" + expected + "\" 但得到 \"" + actual + "\"");
        }
    }

    static void assertTrue(boolean condition, String msg) {
        if (!condition) {
            throw new AssertionError(msg);
        }
    }

    static void assertContains(String body, String substring, String msg) {
        if (!body.contains(substring)) {
            throw new AssertionError(msg + " - 未找到: \"" + substring + "\" 在: " + body);
        }
    }

    static void runTests() {
        System.out.println("\n--- 1. Servlet 路由测试 ---");

        test("GET /hello 返回 200", () -> {
            HttpResponse<String> resp = get("/hello");
            assertEquals(200, resp.statusCode(), "/hello 状态码");
            assertContains(resp.body(), "Hello, World!", "/hello 默认参数");
        });

        test("GET /hello?name=Claude 返回自定义名称", () -> {
            HttpResponse<String> resp = get("/hello?name=Claude");
            assertEquals(200, resp.statusCode(), "/hello?name=Claude 状态码");
            assertContains(resp.body(), "Hello, Claude!", "/hello?name=Claude 内容");
        });

        test("GET /hello?name=中文 支持中文参数", () -> {
            HttpResponse<String> resp = get("/hello?name=世界");
            assertEquals(200, resp.statusCode(), "中文参数状态码");
            assertContains(resp.body(), "Hello, 世界!", "中文参数内容");
        });

        System.out.println("\n--- 2. Filter 拦截测试 ---");

        test("GET /hello?name=123 被拦截返回错误提示", () -> {
            HttpResponse<String> resp = get("/hello?name=123");
            assertEquals(200, resp.statusCode(), "name=123 状态码");
            assertContains(resp.body(), "Invalid name parameter", "name=123 应被拦截");
        });

        test("GET /hello?name=normal 通过 Filter", () -> {
            HttpResponse<String> resp = get("/hello?name=normal");
            assertEquals(200, resp.statusCode(), "name=normal 状态码");
            assertContains(resp.body(), "Filtered by: HelloFilter", "Filter 应添加 attribute");
            assertContains(resp.body(), "Hello, normal!", "正常参数内容");
        });

        System.out.println("\n--- 3. 404 处理测试 ---");

        test("GET /nonexist 返回 404 页面", () -> {
            HttpResponse<String> resp = get("/nonexist");
            assertEquals(200, resp.statusCode(), "404页面状态码应为200");
            assertContains(resp.body(), "404 Not Found", "404 页面内容");
        });

        System.out.println("\n--- 4. 登录流程测试 ---");

        test("GET / 未登录重定向到 /login", () -> {
            HttpResponse<String> resp = get("/");
            assertEquals(302, resp.statusCode(), "/ 应重定向");
            assertEquals("/login", resp.headers().firstValue("Location").orElse(""), "/ 应重定向到 /login");
        });

        test("GET /login?username=admin&password=admin 登录成功", () -> {
            HttpResponse<String> resp = get("/login?username=admin&password=admin");
            assertEquals(302, resp.statusCode(), "登录成功应重定向");
            assertEquals("/", resp.headers().firstValue("Location").orElse(""), "登录成功应重定向到 /");
            assertTrue(resp.headers().firstValue("Set-Cookie").orElse("").contains("JSESSIONID"),
                    "登录成功应设置 JSESSIONID Cookie");
        });

        test("GET /login?username=wrong&password=wrong 登录失败", () -> {
            HttpResponse<String> resp = get("/login?username=wrong&password=wrong");
            assertEquals(302, resp.statusCode(), "登录失败应重定向");
            assertEquals("/error", resp.headers().firstValue("Location").orElse(""), "登录失败应重定向到 /error");
        });

        test("POST /login (LoginServlet 不支持 POST, 预期 405)", () -> {
            HttpResponse<String> resp = post("/login", "username=admin&password=admin");
            assertTrue(resp.statusCode() == 405 || resp.statusCode() == 302,
                    "POST /login 应返回 405(不支持POST) 或 302(已实现doPost)");
        });

        System.out.println("\n--- 5. 完整 Session 会话测试 ---");

        test("登录后访问首页显示欢迎页面", () -> {
            CookieManager cm = new CookieManager();
            HttpClient sessionClient = HttpClient.newBuilder()
                    .cookieHandler(cm)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();

            HttpRequest loginReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/login?username=admin&password=admin"))
                    .GET().build();
            HttpResponse<String> loginResp = sessionClient.send(loginReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(302, loginResp.statusCode(), "登录应返回302");
            assertEquals("/", loginResp.headers().firstValue("Location").orElse(""), "登录应重定向到 /");

            boolean hasSessionId = cm.getCookieStore().getCookies().stream()
                    .anyMatch(c -> "JSESSIONID".equals(c.getName()));
            assertTrue(hasSessionId, "Cookie 中应包含 JSESSIONID");

            HttpRequest indexReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/"))
                    .GET().build();
            HttpResponse<String> indexResp = sessionClient.send(indexReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, indexResp.statusCode(), "登录后访问首页应返回200");
            assertContains(indexResp.body(), "Welcome", "登录后应显示 Welcome 页面");
        });

        test("登录后跟随重定向能看到页面", () -> {
            CookieManager cm = new CookieManager();
            HttpClient redirectClient = HttpClient.newBuilder()
                    .cookieHandler(cm)
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();
            HttpRequest loginReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/login?username=admin&password=admin"))
                    .GET().build();
            HttpResponse<String> resp = redirectClient.send(loginReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode(), "跟随重定向应返回200");
            assertTrue(resp.body().contains("Welcome") || resp.body().contains("Hello"),
                    "登录后应看到欢迎页或 Hello 页");
        });

        System.out.println("\n--- 6. 响应头测试 ---");

        test("Content-Type 响应头", () -> {
            HttpResponse<String> resp = get("/hello");
            String ct = resp.headers().firstValue("Content-Type").orElse("");
            assertTrue(ct.contains("text/html"), "Content-Type 应包含 text/html");
        });

        test("重定向响应包含 Location 头", () -> {
            HttpResponse<String> resp = get("/login");
            assertEquals(302, resp.statusCode(), "/login 应重定向");
            assertTrue(resp.headers().firstValue("Location").isPresent(), "302 应包含 Location 头");
        });

        System.out.println("\n--- 7. 多请求测试 ---");

        test("10 个请求都能正常响应", () -> {
            for (int i = 0; i < 10; i++) {
                HttpResponse<String> resp = get("/hello?name=User" + i);
                assertEquals(200, resp.statusCode(), "第" + i + "个请求状态码");
                assertContains(resp.body(), "Hello, User" + i, "第" + i + "个请求内容");
            }
        });
    }
}
