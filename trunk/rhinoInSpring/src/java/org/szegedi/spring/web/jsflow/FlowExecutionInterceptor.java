package org.szegedi.spring.web.jsflow;

import javax.servlet.http.HttpServletRequest;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

/**
 * An interface for objects that can intercept execution of a flow, and perform
 * operation before and after it.
 * @author Attila Szegedi
 * @version $Id: $
 */
public interface FlowExecutionInterceptor
{
    /**
     * Executed before the flow's script starts running. Typically, this method
     * will populate the scope based on data from the HTTP request.
     * @param request the HTTP request that initiated the flow. 
     * @param scriptPath the pathname of the script that will be executed
     * @param cx the Rhino Context object that is used to run the initial stage
     * of the flow
     * @param scope the global variable scope for the flow - it will typically
     * be manipulated by this method.
     * @throws Exception
     */
    public void beforeFlowExecution(HttpServletRequest request, 
            String scriptPath, Context cx, Scriptable scope) throws Exception;

    /**
     * Executed after the flow's script ended runnnig.
     * @param cx the Rhino Context object that is used to run the terminating 
     * stage of the flow
     * @param scope the global variable scope for the flow
     * @param cause the cause of flow execution termination. It is null if the
     * flow execution terminated normally. The interceptor must not rethrow it,
     * it will be retrown by the {@link FlowController} after this method 
     * executed.
     * @throws Exception
     */
    public void afterFlowExecution(Context cx, Scriptable scope, Exception e)
    throws Exception;
}
