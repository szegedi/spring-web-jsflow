package org.szegedi.spring.web.jsflow;

import org.springframework.core.io.Resource;

/**
 * Classes implementing this interface can be plugged into the 
 * {@link ScriptStorage} to provide creation of Rhino security domain objects
 * for scripts.
 * @author Attila Szegedi
 * @version $Id: $
 */
public interface SecurityDomainFactory
{
    /**
     * Create a Rhino security domain object for the script specified as the
     * resource.
     * @param scriptResource the resource identifying the script
     * @return a security domain object.
     */
    public Object createSecurityDomain(Resource scriptResource);
}
