package org.apereo.cas.support.wsfederation.authentication.handler.support;

import org.apereo.cas.authentication.Credential;
import org.apereo.cas.authentication.HandlerResult;
import org.apereo.cas.authentication.PreventedException;
import org.apereo.cas.authentication.principal.Principal;
import org.apereo.cas.support.wsfederation.authentication.principal.WsFederationCredential;
import org.apereo.cas.authentication.handler.support.AbstractPreAndPostProcessingAuthenticationHandler;

import javax.security.auth.login.FailedLoginException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Map;

/**
 * This handler authenticates Security token/credentials.
 *
 * @author John Gasper
 * @since 4.2.0
 */
public class WsFederationAuthenticationHandler extends AbstractPreAndPostProcessingAuthenticationHandler {

    /**
     * Determines if this handler can support the credentials provided.
     *
     * @param credentials the credentials to test
     * @return true if supported, otherwise false
     */
    @Override
    public boolean supports(final Credential credentials) {
        return credentials != null && WsFederationCredential.class.isAssignableFrom(credentials.getClass());
    }

    @Override
    protected HandlerResult doAuthentication(final Credential credential) throws GeneralSecurityException, PreventedException {
        final WsFederationCredential wsFederationCredentials = (WsFederationCredential) credential;
        if (wsFederationCredentials != null) {
            final Map attributes = wsFederationCredentials.getAttributes();
            final Principal principal = this.principalFactory.createPrincipal(wsFederationCredentials.getId(), attributes);

            return this.createHandlerResult(wsFederationCredentials, principal, new ArrayList<>());
        }
        throw new FailedLoginException();
    }

}
