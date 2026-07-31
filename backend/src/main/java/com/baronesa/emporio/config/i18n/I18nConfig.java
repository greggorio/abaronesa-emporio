package com.baronesa.emporio.config.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Configuration
public class I18nConfig implements WebMvcConfigurer {

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:i18n/messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setCacheSeconds(3600);
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setDefaultLocale(new Locale("pt", "BR"));
        return messageSource;
    }

    @Bean
    public LocaleResolver localeResolver() {
        return new QueryParamLocaleResolver(new Locale("pt", "BR"));
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }

    private static final class QueryParamLocaleResolver implements LocaleResolver {
        private static final String OVERRIDE_ATTRIBUTE = QueryParamLocaleResolver.class.getName() + ".OVERRIDE_LOCALE";
        private final Locale fallback;

        private QueryParamLocaleResolver(Locale fallback) {
            this.fallback = fallback;
        }

        @Override
        public Locale resolveLocale(jakarta.servlet.http.HttpServletRequest request) {
            Object override = request.getAttribute(OVERRIDE_ATTRIBUTE);
            if (override instanceof Locale locale) {
                return locale;
            }

            String param = request.getParameter("lang");
            if (StringUtils.hasText(param)) {
                Locale requested = Locale.forLanguageTag(param);
                if (StringUtils.hasText(requested.getLanguage())) {
                    return requested;
                }
            }

            Locale requestLocale = request.getLocale();
            return requestLocale != null ? requestLocale : fallback;
        }

        @Override
        public void setLocale(jakarta.servlet.http.HttpServletRequest request,
                              jakarta.servlet.http.HttpServletResponse response,
                              Locale locale) {
            if (locale == null) {
                request.removeAttribute(OVERRIDE_ATTRIBUTE);
            } else {
                request.setAttribute(OVERRIDE_ATTRIBUTE, locale);
            }
        }
    }
}
