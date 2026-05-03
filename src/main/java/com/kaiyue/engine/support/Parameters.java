package com.kaiyue.engine.support;

import com.kaiyue.connector.HttpExchangeRequest;
import com.kaiyue.utils.HttpUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数解析器
 */
public class Parameters {

    /**
     * 请求对象
     */
    final HttpExchangeRequest exchangeRequest;
    /**
     * 字符集
     */
    Charset charset;
    /**
     * 参数
     */
    Map<String, String[]> parameters;
    /**
     * 构造方法
     *
     * @param exchangeRequest 请求对象
     * @param charset         字符集
     */
    public Parameters(HttpExchangeRequest exchangeRequest, String charset) {
        this.exchangeRequest = exchangeRequest;
        this.charset = Charset.forName(charset);
    }

    /**
     * 设置字符集
     *
     * @param charset 字符集
     */
    public void setCharset(String charset) {
        this.charset = Charset.forName(charset);
    }

    /**
     * 获取参数
     *
     * @param name 参数名
     * @return 参数值
     */
    public String getParameter(String name) {
        String[] values = getParameterValues(name);
        if (values == null) {
            return null;
        }
        return values[0];
    }
    /**
     * 获取参数名
     *
     * @return 参数名
     */
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(getParameterMap().keySet());
    }

    /**
     * 获取参数值
     * @param name
     * @return
     */
    public String[] getParameterValues(String name) {
        return getParameterMap().get(name);
    }

    /**
     * 获取参数
     *
     * @return 参数
     */
    public Map<String, String[]> getParameterMap() {
        if (this.parameters == null) {
            this.parameters = initParameters();
        }
        return this.parameters;
    }

    /**
     * 初始化参数
     *
     * @return 参数
     */
    Map<String, String[]> initParameters() {
        Map<String, List<String>> params = new HashMap<>();
        String query = this.exchangeRequest.getRequestURI().getRawQuery();
        if (query != null) {
            params = HttpUtils.parseQuery(query, charset);
        }
        if ("POST".equals(this.exchangeRequest.getRequestMethod())) {
            String value = HttpUtils.getHeader(this.exchangeRequest.getRequestHeaders(), "Content-Type");
            if (value != null && value.startsWith("application/x-www-form-urlencoded")) {
                String requestBody;
                try {
                    requestBody = new String(this.exchangeRequest.getRequestBody(), charset);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                Map<String, List<String>> postParams = HttpUtils.parseQuery(requestBody, charset);
                // merge:
                for (String key : postParams.keySet()) {
                    List<String> postValues = postParams.get(key);
                    List<String> queryValues = params.get(key);
                    if (queryValues == null) {
                        params.put(key, postValues);
                    } else {
                        queryValues.addAll(postValues);
                    }
                }
            }
        }
        if (params.isEmpty()) {
            return Map.of();
        }
        // convert:
        Map<String, String[]> paramsMap = new HashMap<>();
        for (String key : params.keySet()) {
            List<String> values = params.get(key);
            paramsMap.put(key, values.toArray(String[]::new));
        }
        return paramsMap;
    }
}
