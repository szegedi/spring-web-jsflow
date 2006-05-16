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

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.continuations.Continuation;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.ModelAndView;
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
    private static final String HOST_PROPERTY = "__host__";
    private static final String REQUEST_PROPERTY = "request";
    private static final String RESPONSE_PROPERTY = "response";
    private static final String SERVLETCONTEXT_PROPERTY = "servletContext";
    private static final String APPLICATIONCONTEXT_PROPERTY = "applicationContext";
    private static final int UNMODIFIABLE = ScriptableObject.READONLY | 
        ScriptableObject.PERMANENT; 
    private static final int HIDDEN = ScriptableObject.DONTENUM;
    
    private String resourcePath = "";
    private boolean usePathInfo;
    private boolean useServletPath;
    
    private ScriptStorage scriptStorage;
    private FlowStateStorage flowStateStorage;
    
    /**
     * Sets the resource path. If neither path info nor servlet path are used,
     * then this controller will always execute a single script, the resource 
     * path of its source file being specified in this property. If either 
     * servlet path or path info (or both) are used, then the controller will 
     * run multiple scripts selected by servlet path and/or path info, and the 
     * path specified here will be used as a common prefix for the resource 
     * paths of script source files. Defaults to empty string, which is a handy 
     * value in case no prefix is required and either servlet path or path info 
     * are used. Be aware that this prefix can be further prefixed by a prefix
     * specified using {@link ScriptStorage#setPrefix(String)}) ScriptStorage
     * class.
     * @param resourcePath
     */
    public void setResourcePath(String resourcePath)
    {
        if(resourcePath == null)
        {
            throw new IllegalArgumentException("resourcePath == null");
        }
        this.resourcePath = resourcePath;
    }
    
    /**
     * Whether to use the path info portion of the request URI when looking up 
     * the script to run. If true, the name of the script to run will be 
     * determined by concatenating resource path + (optionally servlet path) + 
     * path info. If false, the name of the script to run will be determined by
     * concatenating resource path + (optionally servlet path). 
     * Defaults to false.
     * @param usePathInfo
     */
    public void setUsePathInfo(boolean usePathInfo)
    {
        this.usePathInfo = usePathInfo;
    }

    /**
     * Whether to use the servlet path portion of the request URI when looking 
     * up the script to run. If true, the name of the script to run will be 
     * determined by concatenating resource path + servlet path + (optionally 
     * path info). If false, the name of the script to run will be determined by
     * concatenating resource path + (optionally path info). Defaults to false.
     * @param useServletPath
     */
    public void setUseServletPath(boolean useServletPath)
    {
        this.useServletPath = useServletPath;
    }
    
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
    
    public void afterPropertiesSet() throws Exception
    {
        // Try to autodiscover a script cache and a flow state storage in the
        // context
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
            return (ModelAndView)Context.call(new ContextAction()
            {
                public Object run(Context cx)
                {
                    cx.setOptimizationLevel(-1);
                    return handleRequestInContext(request, response, 
                            continuation, cx);
                }
            });
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
            final HttpServletResponse response, final Continuation continuation,
            Context cx)
    {
        ScriptableObject scope;
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
            try
            {
                Script script = scriptStorage.getScript(getScriptPath(request));
                if(script == null)
                {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return null;
                }
                script.exec(cx, scope);
            }
            catch(IOException e)
            {
                throw new UndeclaredThrowableException(e);
            }
        }
        else
        {
            continuation.call(cx, scope, null, new Object[] { null });
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
        }
        return hostObject.getModelAndView(id);
    }

    private String getScriptPath(HttpServletRequest request)
    {
        if(!(usePathInfo || useServletPath))
        {
            return resourcePath;
        }
        StringBuffer buf = new StringBuffer(resourcePath);
        if(useServletPath)
        {
            String servletPath  = (String) request.getAttribute(
                    "javax.servlet.include.servlet_path");
            if(servletPath == null)
            {
                servletPath = request.getServletPath();
            }
            if(servletPath != null)
            {
                buf.append(servletPath);
            }
        }
        if(usePathInfo)
        {
            String pathInfo = (String) request.getAttribute(
                    "javax.servlet.include.path_info");
            if(pathInfo == null)
            {
                pathInfo = request.getPathInfo();
            }
            if(pathInfo != null)
            {
                buf.append(pathInfo);
            }
        }
        return buf.toString();
    }
    
    private static void deleteProperty(ScriptableObject object, String property)
    {
        object.setAttributes(property, 0);
        ScriptableObject.deleteProperty(object, property);
    }
}
