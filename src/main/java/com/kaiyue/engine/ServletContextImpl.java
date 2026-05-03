package com.kaiyue.engine;

import com.kaiyue.engine.mapping.FilterMapping;
import com.kaiyue.engine.mapping.ServletMapping;
import com.kaiyue.engine.session.SessionManager;
import com.kaiyue.utils.AnnoUtils;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebServlet;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

public class ServletContextImpl implements ServletContext {
    // Servlet注册表: 这是注册配置
    Map<String, ServletRegistrationImpl> servletRegistrations = new HashMap<>();
    // Servlet名称到Servlet的映射:
    Map<String, Servlet> nameToServlets = new HashMap<>();
    // Servlet映射:
    List<ServletMapping> servletMappings = new ArrayList<>();
    // Filter注册表：这是过滤器的注册配置
    Map<String, FilterRegistrationImpl> filterRegistrations = new HashMap<>();
    // Filter名称到Filter的映射:
    Map<String, Filter> nameToFilters = new HashMap<>();
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    // Filter映射:
    List<FilterMapping> filterMappings = new ArrayList<>();

    SessionManager sessionManager = new SessionManager(this,1800);

    public void initFilters(List<Class<?>> filterClasses) throws ServletException {
        for (Class<?> c : filterClasses) {
            // 获取@WebFilter注解:
            WebFilter wf = c.getAnnotation(WebFilter.class);
            // 创建FilterRegistration.Dynamic:
            Class<? extends Filter> clazz = (Class<? extends Filter>) c;
            // 添加Filter:
            FilterRegistration.Dynamic registration = this.addFilter(AnnoUtils.getFilterName(clazz), clazz);
            // 添加URL映射:
            registration.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, AnnoUtils.getFilterUrlPatterns(clazz));
            // 设置初始化参数:
            registration.setInitParameters(AnnoUtils.getFilterInitParams(clazz));
        }
        for (String name : this.filterRegistrations.keySet()) {
            // 依次处理每个FilterRegistration.Dynamic:
            var registration = this.filterRegistrations.get(name);
            // 调用Filter.init()方法:
            registration.filter.init(registration.getFilterConfig());
            this.nameToFilters.put(name, registration.filter);
            // 将Filter定义的每个URL映射编译为正则表达式:
            for (String urlPattern : registration.getUrlPatternMappings()) {
                this.filterMappings.add(new FilterMapping(urlPattern, registration.filter));
            }
        }
    }
    /**
     * 初始化方法 - 两阶段初始化过程
     */
    public void initialize(List<Class> servletClasses) throws ServletException {
        if (servletClasses == null || servletClasses.isEmpty()) {
            return;
        }
        // 第一阶段：注册所有 Servlet 并配置
        for (Class servletClass : servletClasses) {
            // 验证类是否实现 Servlet 接口
            if (!Servlet.class.isAssignableFrom(servletClass)) {
                throw new ServletException("Class " + servletClass.getName() + " does not implement Servlet interface");
            }
            // 验证 @WebServlet 注解存在
            WebServlet ws = (WebServlet) servletClass.getAnnotation(WebServlet.class);
            if (ws == null) {
                throw new ServletException("Class " + servletClass.getName() + " missing @WebServlet annotation");
            }
            Class<? extends Servlet> clazz = (Class<? extends Servlet>) servletClass;
            // 创建 ServletRegistration
            String servletName = AnnoUtils.getServletName(clazz);
            ServletRegistration.Dynamic registration = this.addServlet(servletName, clazz);
            // 配置 URL 映射
            String[] urlPatterns = AnnoUtils.getServletUrlPatterns(clazz);
            if (urlPatterns == null || urlPatterns.length == 0) {
                throw new ServletException("Servlet '" + servletName + "' must have at least one URL pattern");
            }
            registration.addMapping(urlPatterns);
            // 配置初始化参数
            registration.setInitParameters(AnnoUtils.getServletInitParams(clazz));
        }
        // 第二阶段：初始化所有 Servlet 并构建路由映射表
        for (Map.Entry<String, ServletRegistrationImpl> entry : this.servletRegistrations.entrySet()) {
            String name = entry.getKey();
            ServletRegistrationImpl registration = entry.getValue();
            try {
                // 调用 Servlet 的 init 方法
                registration.servlet.init(registration.getServletConfig());
                // 添加到名称映射表
                this.nameToServlets.put(name, registration.servlet);
                // 为每个 URL 模式创建映射（转换为正则表达式）
                for (String urlPattern : registration.getMappings()) {
                    String regex = convertUrlPatternToRegex(urlPattern);
                    ServletMapping mapping = new ServletMapping(regex, registration.servlet);
                    this.servletMappings.add(mapping);
                }
                // 标记为已初始化，禁止后续修改配置
                registration.initialized = true;
            } catch (ServletException e) {
                throw new ServletException("Failed to initialize servlet: " + name, e);
            }
        }
    }

    /**
     * 将 Servlet URL 模式转换为正则表达式
     * 支持的模式：
     * - 精确匹配: /api/users -> ^/api/users$
     * - 路径前缀匹配: /api/* -> ^/api(/.*)?$
     * - 扩展名匹配: *.do -> ^.*\.do$
     * - 根路径: / -> ^/$
     */
    private String convertUrlPatternToRegex(String urlPattern) {
        if (urlPattern == null || urlPattern.isEmpty()) {
            throw new IllegalArgumentException("URL pattern cannot be null or empty");
        }

        // 处理根路径
        if ("/".equals(urlPattern)) {
            return "^/$";
        }

        // 处理扩展名匹配: *.do, *.html 等
        if (urlPattern.startsWith("*.")) {
            String extension = urlPattern.substring(2);
            return "^.*\\." + Pattern.quote(extension) + "$";
        }

        // 处理路径前缀匹配: /api/*, /user/* 等
        if (urlPattern.endsWith("/*")) {
            String prefix = urlPattern.substring(0, urlPattern.length() - 2);
            return "^" + Pattern.quote(prefix) + "(/.*)?$";
        }

        // 处理精确匹配
        if (!urlPattern.contains("*")) {
            return "^" + Pattern.quote(urlPattern) + "$";
        }

        // 其他包含通配符的情况，将 * 替换为 .*
        String regex = Pattern.quote(urlPattern);
        regex = regex.replace(Pattern.quote("*"), ".*");
        return "^" + regex + "$";
    }
    /**
     * servlet应用程序入口
     * @param request
     * @param response
     * @throws IOException
     * @throws ServletException
     */
    public void process(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        // 请求路径:
        String path = request.getRequestURI();
        // 搜索Servlet:
        Servlet servlet = null;
        for (ServletMapping mapping : this.servletMappings) {
            if (mapping.matches(path)) {
                // 路径匹配:
                servlet = mapping.servlet;
                break;
            }
        }
        if (servlet == null) {
            // 未匹配到任何Servlet显示404 Not Found:
            PrintWriter pw = response.getWriter();
            pw.write("<h1>404 Not Found</h1><p>No mapping for URL: " + path + "</p>");
            pw.close();
            return;
        }
        // 查找Filter
        List<Filter> enabledFilters = new ArrayList<>();
        for (FilterMapping mapping : this.filterMappings) {
            if (mapping.matches(path)) {
                // 添加到列表:
                enabledFilters.add(mapping.filter);
            }
        }
        Filter[] filters = enabledFilters.toArray(new Filter[0]);
        // 构造FilterChain示例
        FilterChain chain = new FilterChainImpl(filters, servlet);
        // 调用Filter处理请求:
        chain.doFilter(request, response);
//        // 由Servlet继续处理请求: // 已经包含在FilterChain里面执行
//        servlet.service(request, response);
    }
    @Override
    public String getContextPath() {
        return "";
    }

    @Override
    public ServletContext getContext(String uripath) {
        return null;
    }

    @Override
    public int getMajorVersion() {
        return 0;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public int getEffectiveMajorVersion() {
        return 0;
    }

    @Override
    public int getEffectiveMinorVersion() {
        return 0;
    }

    @Override
    public String getMimeType(String file) {
        return "";
    }

    @Override
    public Set<String> getResourcePaths(String path) {
        return Set.of();
    }

    @Override
    public URL getResource(String path) throws MalformedURLException {
        return null;
    }

    @Override
    public InputStream getResourceAsStream(String path) {
        return null;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        return null;
    }

    @Override
    public RequestDispatcher getNamedDispatcher(String name) {
        return null;
    }

    @Override
    public Servlet getServlet(String name) throws ServletException {
        return null;
    }

    @Override
    public Enumeration<Servlet> getServlets() {
        return null;
    }

    @Override
    public Enumeration<String> getServletNames() {
        return null;
    }

    @Override
    public void log(String msg) {

    }

    @Override
    public void log(Exception exception, String msg) {

    }

    @Override
    public void log(String message, Throwable throwable) {

    }

    @Override
    public String getRealPath(String path) {
        return "";
    }

    @Override
    public String getServerInfo() {
        return "";
    }

    @Override
    public String getInitParameter(String name) {
        return "";
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return null;
    }

    @Override
    public boolean setInitParameter(String name, String value) {
        return false;
    }

    @Override
    public Object getAttribute(String name) {
        return null;
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return null;
    }

    @Override
    public void setAttribute(String name, Object object) {

    }

    @Override
    public void removeAttribute(String name) {

    }

    @Override
    public String getServletContextName() {
        return "";
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, String className) {
        return null;
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet) {
        return null;
    }

    public ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {
        if (servletName == null || servletName.isEmpty()) {
            throw new IllegalArgumentException("Servlet name cannot be null or empty");
        }
        if (servletClass == null) {
            throw new IllegalArgumentException("Servlet class cannot be null");
        }

        // 检查是否已经注册过同名的 Servlet
        if (servletRegistrations.containsKey(servletName)) {
            return servletRegistrations.get(servletName);
        }

        // 创建 ServletRegistrationImpl
        ServletRegistrationImpl registration = null;
        try {
            registration = new ServletRegistrationImpl(this, servletName, servletClass.getDeclaredConstructor().newInstance());
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate servlet: " + servletClass.getName(), e);
        }
        servletRegistrations.put(servletName, registration);
        return registration;
    }

    @Override
    public ServletRegistration.Dynamic addJspFile(String servletName, String jspFile) {
        return null;
    }

    @Override
    public <T extends Servlet> T createServlet(Class<T> clazz) throws ServletException {
        return null;
    }

    @Override
    public ServletRegistration getServletRegistration(String servletName) {
        return null;
    }

    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations() {
        return Map.of();
    }

    // 根据Class Name添加Filter:
    @Override
    public FilterRegistration.Dynamic addFilter(String name, String className) {
        try {
            return addFilter(name, (Class<? extends Filter>) Class.forName(className));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    // 根据Class添加Filter:
    @Override
    public FilterRegistration.Dynamic addFilter(String name, Class<? extends Filter> clazz) {
        try {
            return addFilter(name, clazz.newInstance());
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    // 根据Filter实例添加Filter:
    @Override
    public FilterRegistration.Dynamic addFilter(String name, Filter filter) {
        var registration = new FilterRegistrationImpl(this, name, filter);
        this.filterRegistrations.put(name, registration);
        return registration;
    }

    @Override
    public <T extends Filter> T createFilter(Class<T> clazz) throws ServletException {
        return null;
    }

    @Override
    public FilterRegistration getFilterRegistration(String filterName) {
        return null;
    }

    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
        return Map.of();
    }

    @Override
    public SessionCookieConfig getSessionCookieConfig() {
        return null;
    }

    @Override
    public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {

    }

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        return Set.of();
    }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        return Set.of();
    }

    @Override
    public void addListener(String className) {

    }

    @Override
    public <T extends EventListener> void addListener(T t) {

    }

    @Override
    public void addListener(Class<? extends EventListener> listenerClass) {

    }

    @Override
    public <T extends EventListener> T createListener(Class<T> clazz) throws ServletException {
        return null;
    }

    @Override
    public JspConfigDescriptor getJspConfigDescriptor() {
        return null;
    }

    @Override
    public ClassLoader getClassLoader() {
        return null;
    }

    @Override
    public void declareRoles(String... roleNames) {

    }

    @Override
    public String getVirtualServerName() {
        return "";
    }

    @Override
    public int getSessionTimeout() {
        return 0;
    }

    @Override
    public void setSessionTimeout(int sessionTimeout) {

    }

    @Override
    public String getRequestCharacterEncoding() {
        return "";
    }

    @Override
    public void setRequestCharacterEncoding(String encoding) {

    }

    @Override
    public String getResponseCharacterEncoding() {
        return "";
    }

    @Override
    public void setResponseCharacterEncoding(String encoding) {

    }
}
