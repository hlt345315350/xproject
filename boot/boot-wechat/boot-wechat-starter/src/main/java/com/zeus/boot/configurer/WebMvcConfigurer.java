package com.zeus.boot.configurer;


import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.support.config.FastJsonConfig;
import com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter4;
import com.zeus.boot.commons.message.ResponseMessage;
import com.zeus.boot.commons.message.ResultCode;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

@Configuration
@ComponentScan(value = {"com.zeus.boot.controller"})
public class WebMvcConfigurer extends WebMvcConfigurationSupport {

    // logger
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    //当前激活的配置文件
    @Value("${spring.profiles.active}")
    private String env;

    @Override
    protected void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 配置fastjson
        FastJsonHttpMessageConverter4 converter = new FastJsonHttpMessageConverter4();
        FastJsonConfig jsonConfig = new FastJsonConfig();
        // 配置空数据String null -> "" Number null -> 0
        jsonConfig.setSerializerFeatures(SerializerFeature.WriteMapNullValue,
                SerializerFeature.WriteNullStringAsEmpty,
                SerializerFeature.WriteNullNumberAsZero);
        converter.setFastJsonConfig(jsonConfig);
        converter.setDefaultCharset(Charset.forName("UTF-8"));
        converters.add(converter);
    }

    // 配置拦截器
    @Override
    protected void addInterceptors(InterceptorRegistry registry) {
        //开发环境忽略签名认证
        if (!StringUtils.contains(env, "dev")) {
            registry.addInterceptor(new HandlerInterceptorAdapter(){
                @Override
                public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                    // todo 以后处理认证问题
                    return super.preHandle(request, response, handler);
                }
            });
        }
    }

    // 解决跨域
    @Override
    protected void addCorsMappings(CorsRegistry registry) {
        super.addCorsMappings(registry);
    }

    // 配置异常处理
    @Override
    protected void configureHandlerExceptionResolvers(List<HandlerExceptionResolver> exceptionResolvers) {
       exceptionResolvers.add((HttpServletRequest request,
                               HttpServletResponse response,
                               Object handler,
                               Exception e) -> {
           ResponseMessage<Object> error = null;
           if (handler instanceof HandlerMethod) {
               HandlerMethod handlerMethod = (HandlerMethod) handler;
               error = ResponseMessage.error(ResultCode.INTERNAL_SERVER_ERROR, "接口 [" + request.getRequestURI() + "] 内部错误");
               String message = String.format("接口 [%s] 出现异常，方法：%s.%s，异常摘要：%s",
                       request.getRequestURI(),
                       handlerMethod.getBean().getClass().getName(),
                       handlerMethod.getMethod().getName(),
                       e.getMessage());
               logger.error(message, e);
           } else {
               if (e instanceof NoHandlerFoundException) {
                   error = ResponseMessage.error(ResultCode.NOT_FOUND, "接口 [" + request.getRequestURI() + "] 不存在");
               } else {
                   error = ResponseMessage.error(ResultCode.INTERNAL_SERVER_ERROR, e.getMessage());
                   logger.error(e.getMessage(), e);
               }
           }
           responseResult(response, error);
           return new ModelAndView();
       });
    }
    private void responseResult(HttpServletResponse response, ResponseMessage result) {
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-type", "application/json;charset=UTF-8");
        response.setStatus(200);
        try {
            response.getWriter().write(result.toString());
        } catch (IOException ex) {
            logger.error(ex.getMessage());
        }
    }

    // 配置静态资源
    @Override
    protected void addResourceHandlers(ResourceHandlerRegistry registry) {
        logger.info("addResourceHandlers");
        registry.addResourceHandler("/static/**")
                .addResourceLocations("/WEB-INF/static/");

        registry.addResourceHandler("swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");

        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");

    }

    // 添加自定义HandlerMethodArgumentResolver
    @Override
    protected void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        // spring boot 自定义分页
        argumentResolvers.add(new PageableHandlerMethodArgumentResolver());
    }

}
