package org.szegedi.spring.web.jsflow;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;

/**
 * Interface that can be used as an "around" interceptor for each state
 * execution. If set in the {@link FlowController}, its "around" method is
 * invoked after {@link FlowExecutionInterceptor#beforeFlowExecution(
 * javax.servlet.http.HttpServletRequest, String, Context, Scriptable)} and
 * before {@link FlowExecutionInterceptor#afterFlowExecution(
 * javax.servlet.http.HttpServletRequest, Context, Scriptable, Exception)} as
 * well as before a continuation is restarted (after receiving each noninitial
 * HTTP request).
 * @author Attila Szegedi
 * @version $Id: $
 */
public interface StateExecutionInterceptor
{
    /**
     * Invoked to surround the execution of a flow state.
     * @param executor the executor for the state. It is an implementation of
     * the Rhino Script interface, but implementations shouldn't assume it is a
     * real script, rather just something that can be given a context and a
     * scope and that will subsequently execute a state. Implementations of the
     * interceptor will typically invoke <tt>executor.exec(cx, scope)</tt> as
     * part of their operation to delegate to the actual flow state execution.
     * @param cx the Rhino context for the invocation
     * @param scope the top level variable scope for the invocation
     */
    public void aroundStateExecution(Script executor, Context cx,
            Scriptable scope);
}
