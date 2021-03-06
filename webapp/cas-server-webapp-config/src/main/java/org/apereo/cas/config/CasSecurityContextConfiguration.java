package org.apereo.cas.config;

import org.apache.commons.lang.StringUtils;
import org.apereo.cas.CasProtocolConstants;
import org.apereo.cas.authentication.AuthenticationHandler;
import org.apereo.cas.authentication.principal.PrincipalResolver;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.util.AsciiArtUtils;
import org.apereo.cas.util.ResourceUtils;
import org.pac4j.cas.authorization.DefaultCasAuthorizationGenerator;
import org.pac4j.cas.client.direct.DirectCasClient;
import org.pac4j.cas.config.CasConfiguration;
import org.pac4j.core.authorization.authorizer.IsAuthenticatedAuthorizer;
import org.pac4j.core.authorization.authorizer.RequireAnyRoleAuthorizer;
import org.pac4j.core.authorization.generator.SpringSecurityPropertiesAuthorizationGenerator;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.engine.DefaultSecurityLogic;
import org.pac4j.core.exception.HttpAction;
import org.pac4j.http.client.direct.IpClient;
import org.pac4j.http.credentials.authenticator.IpRegexpAuthenticator;
import org.pac4j.springframework.web.SecurityInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.endpoint.mvc.EndpointHandlerMappingCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import org.springframework.web.servlet.mvc.WebContentInterceptor;
import org.springframework.web.servlet.view.RedirectView;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * This is {@link CasSecurityContextConfiguration} that attempts to create Spring-managed beans
 * backed by external configuration.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
@Configuration("casSecurityContextConfiguration")
@EnableConfigurationProperties(CasConfigurationProperties.class)
public class CasSecurityContextConfiguration extends WebMvcConfigurerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(CasSecurityContextConfiguration.class);

    private static final String CAS_CLIENT_NAME = "CasClient";

    @Autowired
    private CasConfigurationProperties casProperties;

    @Autowired
    @Qualifier("authenticationHandlersResolvers")
    private Map authenticationHandlersResolvers;

    @Autowired
    @Qualifier("personDirectoryPrincipalResolver")
    private PrincipalResolver personDirectoryPrincipalResolver;

    @Autowired
    @Qualifier("acceptUsersAuthenticationHandler")
    private AuthenticationHandler acceptUsersAuthenticationHandler;

    @Bean
    public WebContentInterceptor webContentInterceptor() {
        final WebContentInterceptor interceptor = new WebContentInterceptor();
        interceptor.setCacheSeconds(0);
        interceptor.setAlwaysUseFullPath(true);
        return interceptor;
    }

    @RefreshScope
    @Bean
    public SecurityInterceptor requiresAuthenticationStatusInterceptor() {
        return new SecurityInterceptor(new
                Config(new IpClient(new IpRegexpAuthenticator(casProperties.getAdminPagesSecurity().getIp()))),
                "IpClient");
    }

    @RefreshScope
    @Bean
    public Config config() {
        try {
            if (StringUtils.isNotBlank(casProperties.getAdminPagesSecurity().getLoginUrl())
                    && StringUtils.isNotBlank(casProperties.getAdminPagesSecurity().getService())) {

                final CasConfiguration casConfig = new CasConfiguration(casProperties.getAdminPagesSecurity().getLoginUrl());
                final DirectCasClient client = new DirectCasClient(casConfig);
                client.setName(CAS_CLIENT_NAME);

                final Config cfg = new Config(casProperties.getAdminPagesSecurity().getService(), client);
                if (this.casProperties.getAdminPagesSecurity().getUsers() == null) {
                    LOGGER.warn("List of authorized users for admin pages security is not defined. "
                            + "Allowing access for all authenticated users");
                    client.setAuthorizationGenerator(new DefaultCasAuthorizationGenerator<>());
                    cfg.setAuthorizer(new IsAuthenticatedAuthorizer());
                } else {
                    final Resource file = ResourceUtils.prepareClasspathResourceIfNeeded(this.casProperties.getAdminPagesSecurity().getUsers());
                    if (file != null && file.exists()) {
                        final Properties properties = new Properties();
                        properties.load(file.getInputStream());
                        client.setAuthorizationGenerator(new SpringSecurityPropertiesAuthorizationGenerator(properties));
                        cfg.setAuthorizer(new RequireAnyRoleAuthorizer(
                                org.springframework.util.StringUtils.commaDelimitedListToSet(
                                        casProperties.getAdminPagesSecurity().getAdminRoles())));
                    }
                }
                return cfg;
            }
        } catch (final Exception e) {
            LOGGER.warn(e.getMessage(), e);
        }
        return new Config();
    }

    @RefreshScope
    @Bean
    public SecurityInterceptor requiresAuthenticationStatusAdminEndpointsInterceptor() {
        final Config cfg = config();
        if (cfg.getClients() == null) {
            return requiresAuthenticationStatusInterceptor();
        }
        final CasAdminPagesSecurityInterceptor interceptor = new CasAdminPagesSecurityInterceptor(cfg,
                CAS_CLIENT_NAME, "securityHeaders,csrfToken,".concat(getAuthorizerName()));
        return interceptor;
    }

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(statusInterceptor()).addPathPatterns("/status/**");
        registry.addInterceptor(webContentInterceptor()).addPathPatterns("/*");
    }

    @Bean
    public HandlerInterceptorAdapter statusInterceptor() {
        return new CasAdminStatusInterceptor();
    }

    @RefreshScope
    @Bean
    public EndpointHandlerMappingCustomizer mappingCustomizer() {
        return mapping -> mapping.setInterceptors(new Object[]{statusInterceptor()});
    }

    private String getAuthorizerName() {
        if (this.casProperties.getAdminPagesSecurity().getUsers() == null) {
            return IsAuthenticatedAuthorizer.class.getSimpleName();
        }
        return RequireAnyRoleAuthorizer.class.getSimpleName();
    }

    /**
     * The Cas admin status interceptor.
     */
    public class CasAdminStatusInterceptor extends HandlerInterceptorAdapter {
        @Override
        public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response,
                                 final Object handler) throws Exception {
            final String requestPath = request.getRequestURI();
            final Pattern pattern = Pattern.compile("/status(/)*$");

            if (pattern.matcher(requestPath).find()) {
                return requiresAuthenticationStatusInterceptor().preHandle(request, response, handler);
            }
            return requiresAuthenticationStatusAdminEndpointsInterceptor().preHandle(request, response, handler);
        }

        @Override
        public void postHandle(final HttpServletRequest request, final HttpServletResponse response, 
                               final Object handler, final ModelAndView modelAndView) throws Exception {
            if (StringUtils.isNotBlank(request.getQueryString()) 
                    && request.getQueryString().contains(CasProtocolConstants.PARAMETER_TICKET)) {
                final RedirectView v = new RedirectView(request.getRequestURL().toString());
                v.setExposeModelAttributes(false);
                v.setExposePathVariables(false);
                modelAndView.setView(v);
            }
        }
    }

    /**
     * The Cas admin pages security interceptor.
     */
    public static class CasAdminPagesSecurityInterceptor extends SecurityInterceptor {
        
        public CasAdminPagesSecurityInterceptor(final Config config, final String clients, final String authorizers) {
            super(config, clients, authorizers);

            final DefaultSecurityLogic secLogic = new DefaultSecurityLogic() {
                @Override
                protected HttpAction unauthorized(final WebContext context, final List currentClients) {
                    return HttpAction.forbidden("Access Denied", context);
                }
                @Override
                protected boolean loadProfilesFromSession(final WebContext context, final List currentClients) {
                    return true;
                }
            };
            secLogic.setSaveProfileInSession(true);
            setSecurityLogic(secLogic);
        }
    }
    
    @PostConstruct
    public void init() {
        if (StringUtils.isNotBlank(casProperties.getAuthn().getAccept().getUsers())) {
            final String header =
                    "\nCAS is configured to accept a static list of credentials for authentication.\n"
                            + "While this is generally useful for demo purposes, it is STRONGLY recommended\n"
                            + "that you DISABLE this authentication method (by SETTING 'cas.authn.accept.users'\n"
                            + "to a blank value) and switch to a mode that is more suitable for production. \n";
            AsciiArtUtils.printAsciiArt(LOGGER, "STOP!", header);
            this.authenticationHandlersResolvers.put(acceptUsersAuthenticationHandler,
                    personDirectoryPrincipalResolver);
        }
    }
}
