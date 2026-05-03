# ShaunSheepServer

一个基于 JDK 内置 `com.sun.net.httpserver` 实现的**轻量级 Servlet 4.0 容器**，零外部 HTTP 依赖，支持双模式启动（嵌入式 / WAR 包动态加载），是迷你版 Tomcat。

---

## 整体架构

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          ShaunSheepServer                                │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │                     连接器层 (connector)                          │    │
│  │                                                                  │    │
│  │  SimpleHttpServer          HttpConnectoer                        │    │
│  │  ├─ 启动入口                 ├─ 桥接 HttpExchange 与 Servlet      │    │
│  │  ├─ 解析 --host/--port/--war ├─ 设置 ContextClassLoader           │    │
│  │  ├─ contextInitialized/     ├─ 包装 Request/Response             │    │
│  │  │   Destroyed 触发          └─ 委托 ServletContextImpl.process() │    │
│  │  └─ AutoCloseable 关闭                                      │    │
│  │                                                                  │    │
│  │  HttpExchangeAdapter      HttpExchangeRequest/Response            │    │
│  │  (适配器, 实现双接口)       (接口定义, 统一读写逻辑)              │    │
│  │                                                                  │    │
│  │  HttpServletRequestImpl    HttpServletResponseImpl                │    │
│  │  ├─ 完整 Servlet 请求实现    ├─ 完整 Servlet 响应实现              │    │
│  │  ├─ Cookie/Parameter/Session ├─ Cookie/Redirect/Error 页面        │    │
│  │  ├─ ServletRequestListener   ├─ 惰性初始化的 getWriter()           │    │
│  │  │   事件触发               │   (首次写入时自动 sendResponse)      │    │
│  │  └─ getServletContext() 修复  └─ 自动 flush 机制                   │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│                           │                                               │
│                           ▼                                               │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │                     引擎核心层 (engine)                           │    │
│  │                                                                  │    │
│  │  ┌────────────────────────────────────────────────────────────┐  │    │
│  │  │  ServletContextImpl                                        │  │    │
│  │  │  ├─ Servlet 两阶段注册+路由分发(正则匹配)                  │  │    │
│  │  │  ├─ Filter 链管理                                         │  │    │
│  │  │  ├─ 6 类 Listener 事件分发                                │  │    │
│  │  │  ├─ Session 管理(60s过期清理)                              │  │    │
│  │  │  └─ getAttribute() 修复: 委托给 Attributes                 │  │    │
│  │  └────────────────────────────────────────────────────────────┘  │    │
│  │                                                                  │    │
│  │  FilterChainImpl         ServletRegistrationImpl                  │    │
│  │  FilterRegistrationImpl  SessionManager(守护线程清理)             │    │
│  │  HttpSessionImpl(含事件触发)  Resource                            │    │
│  │                                                                  │    │
│  │  ┌─ mapping/ ───────────────────────────────────────────────┐  │    │
│  │  │  ServletMapping: 正则 Pattern → Servlet 实例             │  │    │
│  │  │  FilterMapping:   正则 Pattern → Filter 实例             │  │    │
│  │  └──────────────────────────────────────────────────────────┘  │    │
│  │                                                                  │    │
│  │  ┌─ support/ ───────────────────────────────────────────────┐  │    │
│  │  │  LazyMap(懒加载HashMap)  Attributes(属性存储)             │  │    │
│  │  │  Parameters(参数解析)    InitParameters(初始化参数)        │  │    │
│  │  └──────────────────────────────────────────────────────────┘  │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│                           │                                               │
│     ┌─────────────────────┼─────────────────────┐                        │
│     ▼                     ▼                     ▼                        │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────────┐                   │
│  │ servlet/ │  │ filter/      │  │ listener/        │                   │
│  │Index     │  │HelloFilter   │  │HelloSessionAttr  │                   │
│  │Hello     │  │(拦截/放行)    │  │Listener          │                   │
│  │Login     │  │              │  │(属性变更日志)     │                   │
│  └──────────┘  └──────────────┘  └──────────────────┘                   │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                       类加载层 (classloader)                             │
│                                                                          │
│  WarUtils: 解压 WAR→临时目录, 注册关闭钩子自动清理                       │
│  WebAppClassLoader: URLClassLoader, 加载 WEB-INF/classes + lib/*.jar    │
│  ClassScanner: 扫描 @WebServlet, @WebFilter, @WebListener               │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

## ClassLoader 双亲委派模型

```
                    ┌──────────────────────────────────────┐
                    │ java.lang.String                     │
    BootClassLoader │ java.util.List                       │
                    │ ...                                  │
         ▲          └──────────────────────────────────────┘
         │          ┌──────────────────────────────────────┐
         │          │ javax.servlet.Servlet                │
PlatformClassLoader │ javax.sql.DataSource                 │
         ▲          │ ...                                  │
         │          └──────────────────────────────────────┘
         │          ┌──────────────────────────────────────┐
         │          │ com.kaiyue.*                         │
  AppClassLoader    │ org.slf4j.Logger                     │
         ▲          │ ...                                  │
         │          └──────────────────────────────────────┘
         │          ┌──────────────────────────────────────┐
         │     ┌────┤ WEB-INF/classes/*.class              │
  WebAppClassLoader │ WEB-INF/lib/*.jar                    │
              └────┤ ...                                   │
                   └──────────────────────────────────────┘
```

每次请求处理时，`HttpConnectoer.handle()` 会将线程 ContextClassLoader 设置为 `WebAppClassLoader`，确保 WAR 包中的类能通过 SPI 等机制正确加载：

```java
ClassLoader old = Thread.currentThread().getContextClassLoader();
try {
    Thread.currentThread().setContextClassLoader(this.classLoader);
    servletContext.process(request, response);
} finally {
    Thread.currentThread().setContextClassLoader(old);
}
```

## 请求处理流程

```
HTTP Request
    │
    ▼
SimpleHttpServer.handle(HttpExchange)          ← JDK HttpServer 回调
    │
    ▼
HttpConnectoer.handle(HttpExchange)
    ├─ 包装 HttpExchangeAdapter
    ├─ 创建 HttpServletRequestImpl / HttpServletResponseImpl
    ├─ 设置 ContextClassLoader = WebAppClassLoader
    └─ 委托 ServletContextImpl.process()
    │
    ▼
ServletContextImpl.process(request, response)
    ├─ 触发 ServletRequestListener.requestInitialized()
    ├─ 正则匹配 URL 路径 → 查找 ServletMapping
    │   (修复: 通配符 * 匹配 .* 而非 .+, 支持 /api/* → /api/abc)
    ├─ 收集匹配的 FilterMapping
    ├─ 构建 FilterChainImpl
    ├─ chain.doFilter(request, response)
    │      ├─ 按序执行 Filter.doFilter()
    │      └─ 最终调用 HttpServlet.service()
    │             ├─ doGet() / doPost() / ...
    │             └─ 使用 response.getWriter() 输出
    │                    └─ 惰性 OutputStream: 首次 write() 时自
    │                       动调用 sendResponseHeaders() 发送响应头
    └─ finally: 触发 ServletRequestListener.requestDestroyed()
    │
    ▼
HttpConnectoer.handle() finally
    ├─ 恢复线程 ContextClassLoader
    └─ exchange.close()
```

## 完整的 6 类监听器事件触发

| 监听器类型 | 触发时机 | 调用方法 |
|-----------|---------|---------|
| **ServletContextListener** | 启动时 / 关闭时 | `contextInitialized()` / `contextDestroyed()` |
| **ServletContextAttributeListener** | set/remove ServletContext 属性 | `attributeAdded/Removed/Replaced()` |
| **ServletRequestListener** | process() 入口/出口 | `requestInitialized()` / `requestDestroyed()` |
| **ServletRequestAttributeListener** | set/remove 请求属性 | `attributeAdded/Removed/Replaced()` |
| **HttpSessionListener** | 创建/销毁 Session | `sessionCreated()` / `sessionDestroyed()` |
| **HttpSessionAttributeListener** | set/remove Session 属性 | `attributeAdded/Removed/Replaced()` |

## Bug 修复清单

| # | 文件 | 问题 | 修复方案 |
|---|------|------|---------|
| 1 | [ServletContextImpl.java](file:///d:/IDEA工作目录/good/ShaunSheepServer/src/main/java/com/kaiyue/engine/ServletContextImpl.java#L527) | `getAttribute()` 始终返回 `null`，无法读取通过 `setAttribute()` 设置的值 | 委托给 `this.attributes.getAttribute(name)` |
| 2 | [HttpServletRequestImpl.java](file:///d:/IDEA工作目录/good/ShaunSheepServer/src/main/java/com/kaiyue/connector/HttpServletRequestImpl.java#L580) | `getServletContext()` 返回 `null` | 改为返回 `this.servletContext` |
| 3 | [HtmlUtils.java](file:///d:/IDEA工作目录/good/ShaunSheepServer/src/main/java/com/kaiyue/utils/HtmlUtils.java#L6) | 先替换 `<` 再替换 `&`，导致 `&amp;` 被双重编码 | 先替换 `&`，再替换其他字符 |
| 4 | [UrlUtils.java](file:///d:/IDEA工作目录/good/ShaunSheepServer/src/main/java/com/kaiyue/utils/UrlUtils.java#L24) | 通配符 `*` 被编译为 `.+`（至少匹配一个字符） | 改为 `.*`（匹配零个或多个字符） |
| 5 | [engine/WebAppClassLoader.java](file:///d:/IDEA工作目录/good/ShaunSheepServer/src/main/java/com/kaiyue/engine/classLoader/WebAppClassLoader.java#L112) | JAR URL 缺少 `jar:` 前缀，导致 `URLClassLoader` 无法正确加载 JAR | 改为 `jar:file:///path.jar!/` 格式 |

## 优化清单

| # | 优化内容 | 文件 |
|---|---------|------|
| 1 | 移除 `System.out.println` 调试代码 | [LoginServlet.java](file:///d:/IDEA工作目录/good/ShaunSheepServer/src/main/java/com/kaiyue/engine/servlet/LoginServlet.java) |
| 2 | 移除 "测试成功" 调试输出 | [HelloHttpSessionAttributeListener.java](file:///d:/IDEA工作目录/good/ShaunSheepServer/src/main/java/com/kaiyue/engine/listener/HelloHttpSessionAttributeListener.java) |
| 3 | 清理 `// ... existing code ...` 垃圾注释 | [HttpServletResponseImpl.java](file:///d:/IDEA工作目录/good/ShaunSheepServer/src/main/java/com/kaiyue/connector/HttpServletResponseImpl.java) |
| 4 | 修复 `arrtribute` 拼写错误 | [HelloServlet.java](file:///d:/IDEA工作目录/good/ShaunSheepServer/src/main/java/com/kaiyue/engine/servlet/HelloServlet.java) |
| 5 | 关键文件添加完整中文 Javadoc 注释 | HttpConnectoer, ClassScanner, SimpleHttpServer, WarUtils 等 |
| 6 | 修复 `FilterRegistrationImpl` 错误信息方法名不匹配 | [FilterRegistrationImpl.java](file:///d:/IDEA工作目录/good/ShaunSheepServer/src/main/java/com/kaiyue/engine/FilterRegistrationImpl.java) |

## 内置示例组件

### Servlet

| Servlet | URL 映射 | 功能 |
|---------|----------|------|
| [IndexServlet](file:///d:/IDEA工作目录/good/ShaunSheepServer/src/main/java/com/kaiyue/engine/servlet/IndexServlet.java) | `/` | 检查 Session 中是否有 `username`，未登录重定向到 `/login` |
| [HelloServlet](file:///d:/IDEA工作目录/good/ShaunSheepServer/src/main/java/com/kaiyue/engine/servlet/HelloServlet.java) | `/hello` | 读取 `name` 参数输出 `"Hello, {name}!"`，展示 Filter 设置的 attribute |
| [LoginServlet](file:///d:/IDEA工作目录/good/ShaunSheepServer/src/main/java/com/kaiyue/engine/servlet/LoginServlet.java) | `/login` | 写死校验 admin/admin，登录成功写入 Session |

### Filter

| Filter | URL 映射 | 功能 |
|--------|----------|------|
| [HelloFilter](file:///d:/IDEA工作目录/good/ShaunSheepServer/src/main/java/com/kaiyue/engine/filter/HelloFilter.java) | `/hello`, `/greeting` | 日志记录；`name=123` 时拦截返回错误 |

### Listener

| Listener | 类型 | 功能 |
|----------|------|------|
| [HelloHttpSessionAttributeListener](file:///d:/IDEA工作目录/good/ShaunSheepServer/src/main/java/com/kaiyue/engine/listener/HelloHttpSessionAttributeListener.java) | `HttpSessionAttributeListener` | Session 属性添加/移除/替换时日志记录 |

## 快速开始

### 前置要求

- Java 17+
- Maven 3.x

### 构建

```bash
mvn clean package -DskipTests
```

### 嵌入式模式（默认）

```bash
java -jar target/ShaunSheepServer-1.0-SNAPSHOT.jar
# 或指定端口和地址
java -jar target/ShaunSheepServer-1.0-SNAPSHOT.jar --host 127.0.0.1 --port 9090
```

### WAR 包模式

```bash
java -jar target/ShaunSheepServer-1.0-SNAPSHOT.jar --war hello-webapp.war
```

### 命令行参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--host` | `0.0.0.0` | 监听地址 |
| `--port` | `8080` | 监听端口 |
| `--war` | (无) | WAR 包路径，指定后从 WAR 动态加载组件 |

## WAR 包结构要求

```
hello-webapp.war
├── WEB-INF/
│   ├── classes/                    ← .class 文件（需带 @WebServlet 等注解）
│   │   └── com/example/
│   │       ├── MyServlet.java      ← @WebServlet
│   │       ├── MyFilter.java       ← @WebFilter
│   │       └── MyListener.java     ← @WebListener
│   └── lib/                        ← 依赖的 jar 包
│       ├── my-lib-1.0.jar
│       └── ...
```

支持注解驱动的自动发现：`@WebServlet`、`@WebFilter`、`@WebListener`。

## API 测试

启动服务后：

```bash
# HelloServlet: 默认参数
curl "http://localhost:8080/hello"
# → <h1>Hello, World!</h1>

# HelloServlet: 自定义参数
curl "http://localhost:8080/hello?name=Claude"
# → <h1>Hello, Claude!</h1>

# HelloServlet: 中文参数
curl "http://localhost:8080/hello?name=世界"
# → <h1>Hello, 世界!</h1>

# HelloServlet: Filter 拦截（name=123）
curl "http://localhost:8080/hello?name=123"
# → Invalid name parameter

# IndexServlet: 未登录重定向
curl -v "http://localhost:8080/"
# → 302 → Location: /login

# 登录
curl -v "http://localhost:8080/login?username=admin&password=admin"
# → 302 → Location: / (Set-Cookie: JSESSIONID=...)

# 登录后访问首页（携带 Cookie）
curl -v --cookie "JSESSIONID=xxx" "http://localhost:8080/"
# → 200 Welcome, admin!

# 404
curl "http://localhost:8080/nonexist"
# → 404 Not Found
```

## 运行测试

```bash
# 编译测试
javac -cp "target\classes;%MAVEN_REPO%\javax\servlet\javax.servlet-api\4.0.1\javax.servlet-api-4.0.1.jar;%MAVEN_REPO%\org\slf4j\slf4j-api\2.0.9\slf4j-api-2.0.9.jar;%MAVEN_REPO%\ch\qos\logback\logback-classic\1.4.11\logback-classic-1.4.11.jar;%MAVEN_REPO%\ch\qos\logback\logback-core\1.4.11\logback-core-1.4.11.jar" -d target\test-classes src\test\java\com\kaiyue\TestRunner.java

# 运行测试
java -cp "target\test-classes;target\classes;%MAVEN_REPO%\javax\servlet\javax.servlet-api\4.0.1\javax.servlet-api-4.0.1.jar;..." com.kaiyue.TestRunner

# 测试结果示例
============================================================
  ShaunSheepServer 测试套件
============================================================
  [PASS] GET /hello 返回 200
  [PASS] GET /hello?name=Claude 返回自定义名称
  [PASS] GET /hello?name=中文 支持中文参数
  ...
  [PASS] 10 个请求都能正常响应
============================================================
  结果: 15 通过, 0 失败, 共 15 测试
============================================================
```

## 项目文件清单

```
src/main/java/com/kaiyue/
├── connector/                          # 连接器层（7 文件）
│   ├── SimpleHttpServer.java           #   服务器入口 + 启动器
│   ├── HttpConnectoer.java             #   核心连接器
│   ├── HttpExchangeAdapter.java        #   适配器
│   ├── HttpExchangeRequest.java        #   请求接口
│   ├── HttpExchangeResponse.java       #   响应接口
│   ├── HttpServletRequestImpl.java     #   Servlet 请求实现
│   └── HttpServletResponseImpl.java    #   Servlet 响应实现
├── classloader/                        # 类加载层（3 文件）
│   ├── WebAppClassLoader.java          #   自定义类加载器
│   ├── WarUtils.java                   #   WAR 解压工具
│   └── ClassScanner.java               #   注解扫描器
├── engine/                             # 引擎核心层
│   ├── ServletContextImpl.java         #   核心引擎
│   ├── FilterChainImpl.java            #   Filter 链
│   ├── FilterRegistrationImpl.java     #   Filter 注册
│   ├── ServletRegistrationImpl.java    #   Servlet 注册
│   ├── SessionManager.java             #   Session 管理
│   ├── classLoader/                    #   资源扫描
│   │   ├── WebAppClassLoader.java
│   │   └── Resource.java
│   ├── filter/
│   │   └── HelloFilter.java            #   示例 Filter
│   ├── listener/
│   │   └── HelloHttpSessionAttributeListener.java
│   ├── mapping/
│   │   ├── ServletMapping.java
│   │   └── FilterMapping.java
│   ├── servlet/
│   │   ├── IndexServlet.java
│   │   ├── HelloServlet.java
│   │   └── LoginServlet.java
│   ├── session/
│   │   └── HttpSessionImpl.java
│   └── support/
│       ├── Attributes.java
│       ├── LazyMap.java
│       ├── Parameters.java
│       └── InitParameters.java
└── utils/                              # 工具类层（7 文件）
    ├── AnnoUtils.java
    ├── ClassPathUtils.java
    ├── DateUtils.java
    ├── HtmlUtils.java
    ├── HttpUtils.java
    ├── UrlUtils.java
    └── WarExtractor.java

src/test/java/com/kaiyue/
├── ServerTest.java                     # JUnit 5 测试类
└── TestRunner.java                     # 独立运行测试
```

## 依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| Java | 17+ | 运行环境 |
| javax.servlet-api | 4.0.1 | Servlet 规范 API |
| SLF4J | 2.0.9 | 日志门面 |
| Logback | 1.4.11 | 日志实现 |
| JUnit 5 (test) | 5.10.0 | 单元测试 |

**零外部 HTTP 依赖**：全部使用 JDK 内置的 `com.sun.net.httpserver`。

## 核心技术要点

1. **双亲委派模型**：`WebAppClassLoader` 继承 `URLClassLoader`，父加载器为 `AppClassLoader`，确保 WAR 包类可访问服务器 API
2. **线程上下文类加载器**：每次请求设置 `Thread.currentThread().setContextClassLoader(webAppClassLoader)`，保证 SPI 机制（如 `Class.forName()`）正常工作
3. **惰性响应输出**：`HttpServletResponseImpl.getWriter()` 返回 `PrintWriter`，包装了一个延迟初始化的 `OutputStream`，仅在首次 `write()` 时自动调用 `sendResponseHeaders()`
4. **正则路径匹配**：URL 模式（精确 / 前缀 / 后缀）编译为正则表达式，通配符 `*` 匹配零或多个字符（`.*`）
5. **两阶段初始化**：Servlet 分"注册"和"初始化"两个阶段，注册阶段收集配置，初始化阶段实例化并构建路由表
6. **完整 6 类监听器**：覆盖 Servlet 规范中全部 6 类监听器的生命周期事件触发
