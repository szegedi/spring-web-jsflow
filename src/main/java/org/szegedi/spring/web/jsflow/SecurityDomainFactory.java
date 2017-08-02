package org.szegedi.spring.web.jsflow;

import org.springframework.core.io.Resource;

/**
 * Classes implementing this interface can be plugged into the
 * {@link ScriptStorage} to provide creation of Rhino security domain objects
 * for scripts. I.e. to use it with a custom context factory that uses Rhino's
 * {@link org.mozilla.javascript.PolicySecurityController}, you could implement
 * this interface so that it returns {@link java.security.CodeSource} objects
 * based on the resource URL.
 * 
 * @author Attila Szegedi
 * @version $Id: $
 */
public interface SecurityDomainFactory {
    /**
     * Create a Rhino security domain object for the script specified as the
     * resource.
     * 
     * @param scriptResource
     *            the resource identifying the script
     * @return a security domain object.
     */
    public Object createSecurityDomain(Resource scriptResource);
}
