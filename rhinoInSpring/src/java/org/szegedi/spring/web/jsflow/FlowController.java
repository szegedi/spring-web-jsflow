/*
   Copyright 2006 Attila Szegedi

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package org.szegedi.spring.web.jsflow;

import java.io.FileNotFoundException;
import java.lang.reflect.UndeclaredThrowableException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.continuations.Continuation;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.ModelAndViewDefiningException;
import org.springframework.web.servlet.mvc.AbstractController;
import org.szegedi.spring.beans.factory.BeanFactoryUtilsEx;
import org.szegedi.spring.web.jsflow.support.AbstractFlowStateStorage;

/**
 * A Spring MVC {@link org.springframework.web.servlet.mvc.Controller} that uses
 * Rhino ECMAScript engine to implement flows. A controller requires a 
 * {@link FlowStateStorage} and a {@link ScriptStorage} to operate properly. It 
 * can be either wired (manually or autowired) by a bean factory to them, or it 
 * can discover them by itself in the application context. As a last resort, if 
 * it can not find these objects, it will create its own instances of them (it
 * will use a 
 * {@link org.szegedi.spring.web.jsflow.HttpSessionFlowStateStorage}. A single 
 * instance of controller can encapsulate a single webflow represented by a 
 * single script, or it can handle several flows represented by several scripts.
 * @author Attila Szegedi
 * @version $Id$
 */
public class FlowController extends AbstractController
implements InitializingBean
{
    static final String STATEID_KEY = "stateId";
    public static final String HOST_PROPERTY = "__host__";
    private static final String REQUEST_PROPERTY = "request";
    private static final String RESPONSE_PROPERTY = "response";
    private static final String SERVLETCONTEXT_PROPERTY = "servletContext";
    private static final String APPLICATIONCONTEXT_PROPERTY = "applicationContext";
    private static final int UNMODIFIABLE = ScriptableObject.READONLY | 
        ScriptableObject.PERMANENT; 
    private static final int HIDDEN = ScriptableObject.DONTENUM;
    
    private ScriptStorage scriptStorage;
    private ScriptSelectionStrategy scriptSelectionStrategy;
    private FlowStateStorage flowStateStorage;
    private FlowExecutionInterceptor flowExecutionInterceptor;
    private StateExecutionInterceptor stateExecutionInterceptor;
    private ContextFactory contextFactory;
    
    /**
     * Sets the flow state storage used to store flow states between a HTTP
     * response and the next HTTP request. If not set, the controller will 
     * attempt to look up an instance of it by type in the application context 
     * during initialization.
     * @param flowStateStorage
     */
    public void setFlowStateStorage(FlowStateStorage flowStateStorage)
    {
        this.flowStateStorage = flowStateStorage;
    }
    
    /**
     * Sets the script storage used to load scripts. If not set, the 
     * controller will attempt to look up an instance of it by type in the 
     * application context during initialization.
     * @param scriptStorage
     */
    public void setScriptStorage(ScriptStorage scriptStorage)
    {
        this.scriptStorage = scriptStorage;
    }
    
    /**
     * Sets the script selector used to select scripts for initial HTTP 
     * requests. If not set, an instance of 
     * {@link UrlScriptSelectionStrategy} with
     * {@link UrlScriptSelectionStrategy#setUseServletPath(boolean)} set to 
     * true will be used.
     * @param scriptSelector
     */
    public void setScriptSelectionStrategy(ScriptSelectionStrategy scriptSelector)
    {
        this.scriptSelectionStrategy = scriptSelector;
    }
    
    /**
     * Sets the Rhino context factory to use. It will only be used when the
     * flowscripts are executed outside of a {@link OpenContextInViewInterceptor}
     * (otherwise the interceptor's is used). If not set, the global context
     * factory returned by {@link ContextFactory#getGlobal()} will be used.
     * @param contextFactory
     */
    public void setContextFactory(ContextFactory contextFactory)
    {
        this.contextFactory = contextFactory;
    }
    
    /**
     * @deprecated Use {@link #setScriptSelectionStrategy(ScriptSelectionStrategy)} with
     * a {@link UrlScriptSelectionStrategy} instead
     */
    public void setResourcePath(String resourcePath)
    {
        getUrlScriptSelectionStrategy().setResourcePath(resourcePath);
    }
    
    /**
     * @deprecated Use {@link #setScriptSelectionStrategy(ScriptSelectionStrategy)} with
     * a {@link UrlScriptSelectionStrategy} instead
     */
    public void setUsePathInfo(boolean usePathInfo)
    {
        getUrlScriptSelectionStrategy().setUsePathInfo(usePathInfo);
    }

    /**
     * @deprecated Use {@link #setScriptSelectionStrategy(ScriptSelectionStrategy)} with
     * a {@link UrlScriptSelectionStrategy} instead
     */
    public void setUseServletPath(boolean useServletPath)
    {
        getUrlScriptSelectionStrategy().setUseServletPath(useServletPath);
    }
    
    /**
     * This method exists as a helper to the three deprecated methods above.
     * When they get removed, we'll remove it too. 
     */
    private UrlScriptSelectionStrategy getUrlScriptSelectionStrategy()
    {
        if(scriptSelectionStrategy == null)
        {
            scriptSelectionStrategy = new UrlScriptSelectionStrategy();
        }
        try
        {
            return (UrlScriptSelectionStrategy)scriptSelectionStrategy;
        }
        catch(ClassCastException e)
        {
            throw new IllegalStateException("You can't use the deprecated " + 
                            "script selection methods after explicitly " +
                            "setting a custom script selection strategy");
        }
    }
    
    /**
     * Sets the flow state initializer used to initialize an instance of a
     * flow. If not set, the controller will attempt to look up an instance of
     * it by type in the application context during initialization. If none is
     * found, no custom flow initialization will be performed.
     * @param flowExecutionInterceptor
     */
    public void setFlowExecutionInterceptor(
            FlowExecutionInterceptor flowExecutionInterceptor)
    {
        this.flowExecutionInterceptor = flowExecutionInterceptor;
    }
    
    public void setStateExecutionInterceptor(
            StateExecutionInterceptor stateExecutionInterceptor)
    {
        this.stateExecutionInterceptor = stateExecutionInterceptor;
    }
    
    public void afterPropertiesSet() throws Exception
    {
        // Try to autodiscover a script cache, flow state storage, and flow 
        // state initializer in the context if they're not explicitly set.
        ApplicationContext ctx = getApplicationContext();
        if(scriptStorage == null)
        {
            scriptStorage = (ScriptStorage)BeanFactoryUtilsEx.beanOfTypeIncludingAncestors(ctx, ScriptStorage.class);
            if(scriptStorage == null)
            {
                scriptStorage = new ScriptStorage();
                scriptStorage.setResourceLoader(ctx);
            }
        }
        if(flowStateStorage == null)
        {
            flowStateStorage = (FlowStateStorage)BeanFactoryUtilsEx.beanOfTypeIncludingAncestors(ctx, 
                    FlowStateStorage.class);
            if(flowStateStorage == null)
            {
                flowStateStorage = new HttpSessionFlowStateStorage();
                ((HttpSessionFlowStateStorage)flowStateStorage).setApplicationContext(
                        getApplicationContext());
                ((HttpSessionFlowStateStorage)flowStateStorage).afterPropertiesSet();
            }
        }
        if(flowExecutionInterceptor == null)
        {
            flowExecutionInterceptor = (FlowExecutionInterceptor)BeanFactoryUtilsEx.beanOfTypeIncludingAncestors(ctx, 
                    FlowExecutionInterceptor.class);
        }
        if(scriptSelectionStrategy == null)
        {
            UrlScriptSelectionStrategy dss = new UrlScriptSelectionStrategy();
            dss.setUseServletPath(true);
            scriptSelectionStrategy = dss;
        }
        // Since we can't guarantee initialization order, make sure that we're
        // using the same script storage.
        if(flowStateStorage instanceof AbstractFlowStateStorage)
        {
            AbstractFlowStateStorage pfss = 
                (AbstractFlowStateStorage)flowStateStorage;
            ScriptStorage otherScriptStorage = pfss.getScriptStorage();
            if(otherScriptStorage == null)
            {
                pfss.setScriptStorage(scriptStorage);
            }
            else if(otherScriptStorage != scriptStorage)
            {
                throw new BeanInitializationException(
                        "Persistent state storage uses a different script storage");
            }
        }
    }

    /**
     * First, the controller determines if there is a request parameter named 
     * "stateId" and if it contains a valid state ID. If so, it continues the 
     * execution of the associated flowscript at the point where it was waiting.
     * If state ID is not present or not valid, the controller will look up a
     * script to execute, by concatenating resourcePath with neither, one, or 
     * both of the servlet path and path info portions of the request URI, 
     * depending on the values of the usePathInfo and useServletPath properties.
     * In case the servlet was invoked as a result of a {@link 
     * javax.servlet.RequestDispatcher#include(javax.servlet.ServletRequest, 
     * javax.servlet.ServletResponse)} call, the controller will correctly use
     * the request attributes <tt>javax.servlet.include.servlet_path</tt> and
     * <tt>javax.servlet.include.path_info</tt> instead of the request's proper
     * servlet path and path info. 
     * All flowscripts have access to the following built-in objects:
     * </p><p>
     * <table border="1" valign="top">
     *   <tr><th>Name</th><th>Object</th></tr>
     *   <tr><td><tt>request</tt></td><td>the HttpServletRequest object</td></tr>
     *   <tr><td><tt>response</tt></td><td>the HttpServletResponse object</td></tr>
     *   <tr><td><tt>servletContext</tt></td><td>the ServletContext object</td></tr>
     *   <tr><td><tt>applicationContext</tt></td><td>the ApplicationContext object</td></tr>
     * </table>
     * </p><p>
     * They also have access to following built-in functions:
     * <table border="1" valign="top">
     *   <tr><th>Function</th><th>Purpose</th></tr>
     *   <tr><td><tt>include(<i>path</i>)</tt></td><td>includes a script 
     *     referenced with the specified absolute path as if it was executed at 
     *     the point of inclusion. You can use it to conveniently include 
     *     reusable function modules.</td></tr>
     *   <tr><td><tt>respond(<i>viewName</i>, <i>model</i>)</tt></td><td>respond
     *     to the actual HTTP request with the specified view name and the 
     *     specified model. The view is resolved using the Spring's view 
     *     resolution mechanism. The model can be any Rhino Scriptable instance 
     *     (including <tt>this</tt> to pass all script variables to the model), 
     *     the controller will take care of fitting a Map interface to it to 
     *     conform to Spring model requirements. The actual sending of the 
     *     response will not happen until the script either terminates, or calls
     *     the <tt>wait()</tt> function. Another invocation of the function 
     *     before the actual sending of the reponse happens will overwrite a 
     *     previous one. The script can pass <tt>null</tt> for the view name to 
     *     indicate it handled the response completely on its own and no view 
     *     needs to be invoked.</td></tr>
     *   <tr><td><tt>wait()</tt></td><td>send the response and then waits for 
     *     the next HTTP request. The function will return when another HTTP 
     *     request for that flow is made. Upon returning from this function, the 
     *     variables <tt>request</tt> and <tt>response</tt> will have new 
     *     values, therefore scripts should not store references to them across
     *     waits. When <tt>wait()</tt> is invoked, an additional variable named
     *     "stateId" is placed into the model. The view should pass this id to 
     *     the client, who should specify it in a request parameter also named 
     *     "stateId" in the next HTTP request to continue the flow.</td></tr>
     *   <tr><td><tt>respondAndWait(<i>viewName</i>, <i>model</i>)</tt></td><td>
     *     conveniently combines the <tt>respond()</tt> and <tt>wait()</tt> 
     *     functions into a single function.</td></tr>
     *   <tr><td><tt>isGoingToWait()</tt></td><td> returns true if the script 
     *   is about to go waiting as a result of a <tt>wait()</tt> call. All open
     *   finally blocks are executed in Rhino whenever <tt>wait()</tt> is 
     *   called. This function can be used in finally blocks to distinguish 
     *   between control flow exiting the block for the last time (really 
     *   "finally") and control flow exiting the block because of wait. I.e. the
     *   typical use is:
     *   <pre>
     * try
     * {
     *     ...
     *     respondAndWait("confirm.html", data);
     *     ...
     * }
     * finally
     * {
     *     if(!isGoingToWait())
     *     {
     *         // perform the "real finally" cleanup here
     *         ...
     *     }
     * }
     *   </pre></td></tr>
     * </table>
     */
    protected ModelAndView handleRequestInternal(
            final HttpServletRequest request, final HttpServletResponse response) 
    throws Exception
    {
        final Continuation continuation = getState(request);
        Context cx = Context.getCurrentContext();
        if(cx == null)
        {
            // No context - we're not running within OpenContextInViewInterceptor.
            // Open our own context only for the duration of the controller.
            ContextAction cxa = new ContextAction()
            {
                public Object run(Context cx)
                {
                    cx.setOptimizationLevel(-1);
                    try
                    {
                        return handleRequestInContext(request, response, 
                            continuation, cx);
                    }
                    catch(ModelAndViewDefiningException e)
                    {
                        return e.getModelAndView();
                    }
                    catch(RuntimeException e)
                    {
                        throw e;
                    }
                    catch(Exception e)
                    {
                        throw new UndeclaredThrowableException(e);
                    }
                }
            };
            if(contextFactory == null)
            {
                return (ModelAndView)Context.call(cxa);
            }
            else
            {
                return (ModelAndView)contextFactory.call(cxa);
            }
        }
        else
        {
            // Have a context associated with the thread - we're probably 
            // running within OpenContextInViewInterceptor. Just use it.
            return handleRequestInContext(request, response, continuation, cx);
        }
    }

    private Continuation getState(HttpServletRequest request)
    {
        String strId = request.getParameter(STATEID_KEY);
        if(strId == null)
        {
            return null;
        }
        return flowStateStorage.getState(request, strId);
    }
    
    private ModelAndView handleRequestInContext(
            final HttpServletRequest request, 
            final HttpServletResponse response, 
            final Continuation continuation, final Context cx) throws Exception
    {
        final ScriptableObject scope;
        if(continuation == null)
        {
            scope = scriptStorage.createNewTopLevelScope(cx);
        }
        else
        {
            scope = (ScriptableObject)ScriptableObject.getTopLevelScope(
                    continuation);
        }
        HostObject hostObject = (HostObject)cx.newObject(scope, "HostObject");
        hostObject.setScriptStorage(scriptStorage);
        ScriptableObject.defineProperty(scope, HOST_PROPERTY,  hostObject, 
                HIDDEN | UNMODIFIABLE);
        ScriptableObject.defineProperty(scope, REQUEST_PROPERTY, request, 
                UNMODIFIABLE);
        ScriptableObject.defineProperty(scope, RESPONSE_PROPERTY, response, 
                UNMODIFIABLE);
        ScriptableObject.defineProperty(scope, SERVLETCONTEXT_PROPERTY, 
                getServletContext(), UNMODIFIABLE);
        ScriptableObject.defineProperty(scope, APPLICATIONCONTEXT_PROPERTY, 
                getApplicationContext(), UNMODIFIABLE);
        cx.setOptimizationLevel(-1);
        if(continuation == null)
        {
            String scriptPath = scriptSelectionStrategy.getScriptPath(request);
            if(scriptPath == null)
            {
                // Couldn't select script
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return null;
            }
            final Script script;
            try
            {
                script = scriptStorage.getScript(scriptPath);
            }
            catch(FileNotFoundException e)
            {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return null;
            }
            if(script == null)
            {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return null;
            }
            if(flowExecutionInterceptor != null)
            {
                flowExecutionInterceptor.beforeFlowExecution(request, 
                        scriptPath, cx, scope);
            }
            try
            {
                if(stateExecutionInterceptor != null)
                {
                    stateExecutionInterceptor.aroundStateExecution(new Script()
                    {
                        public Object exec(Context cx, Scriptable scope)
                        {
                            return script.exec(cx, scope);
                        }
                    }, cx, scope);
                }
                else
                {
                    script.exec(cx, scope);
                }
            }
            catch(Exception e)
            {
                afterFlowExecution(request, cx, scope, e);
                throw e;
            }
        }
        else
        {
            try
            {
                if(stateExecutionInterceptor != null)
                {
                    stateExecutionInterceptor.aroundStateExecution(new Script()
                    {
                        public Object exec(Context cx, Scriptable scope)
                        {
                            return continuation.call(cx, scope, null, 
                                    new Object[] { null });
                        }
                    }, cx, scope);
                }
                else
                {
                    continuation.call(cx, scope, null, new Object[] { null });
                }
            }
            catch(Exception e)
            {
                afterFlowExecution(request, cx, scope, e);
                throw e;
            }
        }
        deleteProperty(scope, APPLICATIONCONTEXT_PROPERTY);
        deleteProperty(scope, SERVLETCONTEXT_PROPERTY);
        deleteProperty(scope, RESPONSE_PROPERTY);
        deleteProperty(scope, REQUEST_PROPERTY);
        deleteProperty(scope, HOST_PROPERTY);
        Continuation newContinuation = hostObject.getContinuation();
        Object id;
        if(newContinuation != null)
        {
            id = flowStateStorage.storeState(request, newContinuation);
        }
        else 
        {
            id = null;
            afterFlowExecution(request, cx, scope, null);
        }
        return hostObject.getModelAndView(id);
    }

    
    private void afterFlowExecution(HttpServletRequest request, Context cx, 
            ScriptableObject scope, Exception cause) throws Exception
    {
        if(flowExecutionInterceptor != null)
        {
            flowExecutionInterceptor.afterFlowExecution(request, cx, scope, 
                    cause);
        }
    }

    private static void deleteProperty(ScriptableObject object, String property)
    {
        object.setAttributes(property, 0);
        ScriptableObject.deleteProperty(object, property);
    }
}
