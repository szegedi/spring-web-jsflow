package org.szegedi.spring.web.jsflow;

import javax.servlet.http.HttpServletRequest;

/**
 * Interface for objects that select a script for a particular initial HTTP 
 * request.
 * @author Attila Szegedi
 * @version $Id: $
 */
public interface ScriptSelectionStrategy
{
    /**
     * Returns the pathname of the script that should run for a particular
     * initial HTTP request.
     * @param request the HTTP request
     * @return the path of the script. null can be returned to indicate that
     * this strategy is unable to select a script (i.e. because some data is
     * missing in the request). The controller will respond to this by sending
     * back a HTTP 400 "Bad Request" status.
     */
    public String getScriptPath(HttpServletRequest request);
}
