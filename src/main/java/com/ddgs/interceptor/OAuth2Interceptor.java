package com.ddgs.interceptor;

import com.ddgs.ErrorCode;
import com.ddgs.Result;
import com.ddgs.service.URLService;
import com.ddgs.tools.JSONUtil;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.oltu.oauth2.common.OAuth;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * @author: codeyung  E-mail:yjc199308@gmail.com
 * @date: 2017/11/23.下午1:44
 */
public class OAuth2Interceptor implements HandlerInterceptor {

    @Value("${check.accessToken.url}")
    private String CHECK_ACCESS_CODE_URL;

    @Autowired
    URLService urlService;

    private static final Logger logger = LoggerFactory.getLogger(OAuth2Interceptor.class);

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        Result result = new Result();
        result.setError(ErrorCode.INVALID_ACCESS_TOKEN);
        try {
            String accessToken = request.getParameter("access_token");//请求体中或请求头中获取参数

            if (StringUtils.isEmpty(accessToken)) {
                // 如果不存在/过期了，返回未验证错误，需重新验证
                oAuthFaileResponse(response, result);
                return false;
            }

            //验证Access Token
            if (!checkAccessToken(accessToken)) {
                // 如果不存在/过期了，返回未验证错误，需重新验证
                oAuthFaileResponse(response, result);
                return false;
            }

            //URL 权限验证
            if (!checkURL(request, accessToken)) {
                // 权限不符 访问了没有权限的 URL
                result.setError(ErrorCode.APP_PERMISSIONS_FAIL);
                oAuthFaileResponse(response, result);
                return false;
            }
        } catch (Exception e) {
            System.out.println("错误");
            oAuthFaileResponse(response, result);
            return false;
        }
        return true;


    }

    @Override
    public void postHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, ModelAndView modelAndView) throws Exception {

    }

    @Override
    public void afterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, Exception e) throws Exception {

    }

    /**
     * oAuth认证失败时的输出
     *
     * @param res
     * @throws IOException
     */
    private void oAuthFaileResponse(HttpServletResponse res, Result result) throws IOException {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add("Content-Type", "application/json; charset=utf-8");
        res.setCharacterEncoding("UTF-8");
        res.addHeader(OAuth.HeaderType.WWW_AUTHENTICATE, OAuth.HeaderType.WWW_AUTHENTICATE);
        PrintWriter writer = res.getWriter();
        writer.write(JSONUtil.toJson(result));
        writer.flush();
        writer.close();
    }


    /**
     * 验证accessToken
     *
     * @param accessToken
     * @return
     * @throws IOException
     */
    private boolean checkAccessToken(String accessToken) throws IOException {
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(5000).setConnectionRequestTimeout(1000)
                .setSocketTimeout(5000).build();
        CloseableHttpClient httpclient = HttpClients.custom().setDefaultRequestConfig(requestConfig)
                .setRetryHandler(new DefaultHttpRequestRetryHandler(0, false)).build();

        List<NameValuePair> paras = new ArrayList<>();
        paras.add(new BasicNameValuePair("access_token", accessToken));
        HttpPost httpPost = new HttpPost(CHECK_ACCESS_CODE_URL);
        httpPost.setConfig(requestConfig);
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(paras, "utf-8");
        httpPost.setEntity(entity);
        CloseableHttpResponse httpResponse = httpclient.execute(httpPost);

        int status = httpResponse.getStatusLine().getStatusCode();

        if (status == 200) {
            String resultStr = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");

            JSONObject jSONObject = new JSONObject(resultStr);
            return jSONObject.getBoolean("success");
        }
        return false;

    }

    /**
     * 验证权限
     *
     * @param accessToken
     * @param request
     * @return
     */
    private boolean checkURL(HttpServletRequest request, String accessToken) {
        String url = request.getMethod() + ":" + request.getRequestURI();
        return urlService.checkURL(accessToken, url);
    }


}
