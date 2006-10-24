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
     * initial HTTP request
     * @param request the HTTP request
     * @return the path of the script.
     */
    public String getScriptPath(HttpServletRequest request);
}
