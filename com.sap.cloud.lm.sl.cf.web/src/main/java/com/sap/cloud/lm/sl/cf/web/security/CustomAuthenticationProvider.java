package com.sap.cloud.lm.sl.cf.web.security;

import org.cloudfoundry.client.lib.CloudFoundryException;
import org.cloudfoundry.client.lib.CloudFoundryOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.TokenStore;

import com.sap.cloud.lm.sl.cf.client.TokenProvider;
import com.sap.cloud.lm.sl.cf.client.util.TokenFactory;
import com.sap.cloud.lm.sl.cf.core.auditlogging.AuditLoggingProvider;
import com.sap.cloud.lm.sl.cf.core.cf.ClientFactory;
import com.sap.cloud.lm.sl.cf.core.util.Configuration;
import com.sap.cloud.lm.sl.cf.core.util.SecurityUtil;
import com.sap.cloud.lm.sl.cf.web.message.Messages;
import com.sap.cloud.lm.sl.common.util.Pair;

public class CustomAuthenticationProvider implements AuthenticationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomAuthenticationProvider.class);

    @Autowired
    @Qualifier("tokenStore")
    TokenStore tokenStore;

    @Autowired
    @Qualifier("cloudFoundryClientFactory")
    ClientFactory cloudFoundryClientFactory;
    
    @Autowired
    Configuration configuration;
    
    @Autowired
    TokenFactory tokenFactory;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        if (!configuration.isBasicAuthEnabled())
            throw new InsufficientAuthenticationException("Basic authentication is not enabled, use OAuth2");

        try {
            UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) authentication;
            String userName = (String) auth.getPrincipal();
            String password = (String) auth.getCredentials();

            Pair<CloudFoundryOperations, TokenProvider> client = cloudFoundryClientFactory.createClient(userName, password);
            TokenProvider tokenProvider = client._2;

            // Get a valid token from the client
            // If this works, consider the request authenticated
            OAuth2AccessToken token = (tokenProvider != null) ? tokenProvider.getToken() : null;
            if (token == null) {
                if (configuration.areDummyTokensEnabled()) {
                    token = tokenFactory.createDummyToken(userName, SecurityUtil.CLIENT_ID);
                } else {
                    String message = "Null access token returned by cloud controller";
                    AuditLoggingProvider.getFacade().logSecurityIncident(message);
                    throw new AuthenticationServiceException(message);
                }
            }

            // Check if an authentication for this token already exists in the token store
            OAuth2Authentication auth2 = tokenStore.readAuthentication(token);
            if (auth2 == null) {
                // Create an authentication for the token and store it in the token store
                auth2 = SecurityUtil.createAuthentication(SecurityUtil.CLIENT_ID, token.getScope(), SecurityUtil.getTokenUserInfo(token));
                try {
                    tokenStore.storeAccessToken(token, auth2);
                } catch (DataIntegrityViolationException e) {
                    LOGGER.debug(com.sap.cloud.lm.sl.cf.core.message.Messages.ERROR_STORING_TOKEN_DUE_TO_INTEGRITY_VIOLATION, e);
                    // Ignoring the exception as the token and authentication are already persisted
                    // by another client.
                }
            }

            return auth2;
        } catch (CloudFoundryException e) {
            String message = Messages.CANNOT_AUTHENTICATE_WITH_CLOUD_CONTROLLER;
            AuditLoggingProvider.getFacade().logSecurityIncident(message);
            throw new BadCredentialsException(message, e);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}
