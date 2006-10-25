package org.szegedi.spring.web.jsflow;

import javax.servlet.http.HttpServletRequest;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

/**
 * An interface for objects that can initialize a flow state.
 * @author Attila Szegedi
 * @version $Id: $
 */
public interface FlowStateInitializer
{
    /**
     * Initializes the flow state before the script starts running. Typically,
     * this method will populate the scope based on data from the HTTP request.
     * @param request the HTTP request that initiated the flow. 
     * @param scriptPath the pathname of the script that will be executed
     * @param cx the Rhino Context object that is used to run the initial stage
     * of the flow
     * @param scope the global variable scope for the flow - it will typically
     * be manipulated by this method.
     * @throws Exception
     */
    public void initializeFlowState(HttpServletRequest request, 
            String scriptPath, Context cx, Scriptable scope) throws Exception;
}
