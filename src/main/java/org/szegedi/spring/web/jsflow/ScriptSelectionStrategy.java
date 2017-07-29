package org.szegedi.spring.web.jsflow;

import javax.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.ModelAndViewDefiningException;

/**
 * Interface for objects that select the flowscript to execute for a flow
 * initiating HTTP requests.
 * @author Attila Szegedi
 * @version $Id: $
 */
public interface ScriptSelectionStrategy
{
    /**
     * Returns the pathname of the script that should run for a particular
     * initial HTTP request.
     * @param request the HTTP request that initiates a flow
     * @return the path of the script. null can be returned to indicate that
     * this strategy is unable to select a script (i.e. because some data is
     * missing in the request). The controller will respond to this by sending
     * back a HTTP 400 "Bad Request" status. Alternatively, the strategy can
     * throw an instance of Spring's {@link ModelAndViewDefiningException} to
     * indicate failure.
     */
    public String getScriptPath(HttpServletRequest request)
    throws ModelAndViewDefiningException;
}
