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
import org.mozilla.javascript.ContinuationPending;
import org.mozilla.javascript.NativeContinuation;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.ModelAndViewDefiningException;
import org.springframework.web.servlet.mvc.AbstractController;
import org.szegedi.spring.beans.factory.BeanFactoryUtilsEx;
import org.szegedi.spring.web.jsflow.support.AbstractFlowStateStorage;
import org.szegedi.spring.web.jsflow.support.ContextFactoryHolder;

/**
 * A Spring MVC {@link org.springframework.web.servlet.mvc.Controller} that uses
 * Rhino ECMAScript engine to implement flows. A controller requires a
 * {@link FlowStateStorage}, a {@link ScriptStorage}, and a
 * {@link ScriptSelectionStrategy} to operate properly. It can be either wired
 * (manually or autowired) by a bean factory to them, or it can discover them by
 * itself in the application context. As a last resort, if it can not find these
 * objects, it will create its own instances of them (it will use a
 * {@link HttpSessionFlowStateStorage} and a
 * {@link UrlScriptSelectionStrategy}). A single instance of controller can
 * encapsulate a single webflow represented by a single script, or it can handle
 * several flows represented by several scripts, depending on the script
 * selection strategy used. The operation of the controller can be cusomized by
 * installing various interceptors into it.
 *
 * @author Attila Szegedi
 * @version $Id$
 */
public class FlowController extends AbstractController implements InitializingBean {
    static final String STATEID_KEY = "stateId";
    private static final String HOST_PROPERTY = "__host__";
    private static final String SCRIPT_DIR_PROPERTY = "__scriptDirectory__";
    private static final String REQUEST_PROPERTY = "request";
    private static final String RESPONSE_PROPERTY = "response";
    private static final String SERVLETCONTEXT_PROPERTY = "servletContext";
    private static final String APPLICATIONCONTEXT_PROPERTY = "applicationContext";
    private static final int UNMODIFIABLE = ScriptableObject.READONLY | ScriptableObject.PERMANENT;
    private static final int HIDDEN = ScriptableObject.DONTENUM;

    private ScriptStorage scriptStorage;
    private ScriptSelectionStrategy scriptSelectionStrategy;
    private FlowStateStorage flowStateStorage;
    private FlowExecutionInterceptor flowExecutionInterceptor;
    private StateExecutionInterceptor stateExecutionInterceptor;
    private final ContextFactoryHolder contextFactoryHolder = new ContextFactoryHolder();

    /**
     * Sets the flow state storage used to store flow states between a HTTP
     * response and the next HTTP request. If not set, the controller will
     * attempt to look up an instance of it by type in the application context
     * during initialization. If none is found, the controller will create an
     * internal default instance of {@link HttpSessionFlowStateStorage}.
     *
     * @param flowStateStorage
     */
    public void setFlowStateStorage(final FlowStateStorage flowStateStorage) {
        this.flowStateStorage = flowStateStorage;
    }

    /**
     * Sets the script storage used to load scripts. If not set, the controller
     * will attempt to look up an instance of it by type in the application
     * context during initialization. If none is found, it will create an
     * internal default instance.
     *
     * @param scriptStorage
     */
    public void setScriptStorage(final ScriptStorage scriptStorage) {
        this.scriptStorage = scriptStorage;
    }

    /**
     * Sets the script selector used to select scripts for initial HTTP
     * requests. If not set, an instance of {@link UrlScriptSelectionStrategy}
     * with {@link UrlScriptSelectionStrategy#setUseServletPath(boolean)} set to
     * true will be used.
     *
     * @param scriptSelector
     */
    public void setScriptSelectionStrategy(final ScriptSelectionStrategy scriptSelector) {
        this.scriptSelectionStrategy = scriptSelector;
    }

    /**
     * Sets the Rhino context factory to use. It will only be used when the
     * flowscripts are executed outside of a
     * {@link OpenContextInViewInterceptor} (otherwise the interceptor's is
     * used). If it's not set either here or in the interceptor, the global
     * context factory returned by {@link ContextFactory#getGlobal()} will
     * be used.
     *
     * @param contextFactory
     */
    public void setContextFactory(final ContextFactory contextFactory) {
        contextFactoryHolder.setContextFactory(contextFactory);
    }

    /**
     * @deprecated Use
     *             {@link #setScriptSelectionStrategy(ScriptSelectionStrategy)}
     *             with a {@link UrlScriptSelectionStrategy} instead
     */
    @Deprecated
    public void setResourcePath(final String resourcePath) {
        getUrlScriptSelectionStrategy().setResourcePath(resourcePath);
    }

    /**
     * @deprecated Use
     *             {@link #setScriptSelectionStrategy(ScriptSelectionStrategy)}
     *             with a {@link UrlScriptSelectionStrategy} instead
     */
    @Deprecated
    public void setUsePathInfo(final boolean usePathInfo) {
        getUrlScriptSelectionStrategy().setUsePathInfo(usePathInfo);
    }

    /**
     * @deprecated Use
     *             {@link #setScriptSelectionStrategy(ScriptSelectionStrategy)}
     *             with a {@link UrlScriptSelectionStrategy} instead
     */
    @Deprecated
    public void setUseServletPath(final boolean useServletPath) {
        getUrlScriptSelectionStrategy().setUseServletPath(useServletPath);
    }

    /**
     * This method exists as a helper to the three deprecated methods above.
     * When they get removed, we'll remove it too.
     */
    private UrlScriptSelectionStrategy getUrlScriptSelectionStrategy() {
        if (scriptSelectionStrategy == null) {
            scriptSelectionStrategy = new UrlScriptSelectionStrategy();
        }
        try {
            return (UrlScriptSelectionStrategy) scriptSelectionStrategy;
        } catch (final ClassCastException e) {
            throw new IllegalStateException("You can't use the deprecated "
                    + "script selection methods after explicitly " + "setting a custom script selection strategy");
        }
    }

    /**
     * Sets the flow execution interceptor used to custom initialize an instance
     * of a flow before it first executes, as well as perform any cleanup after
     * an instance of a flow terminates. If not set, the controller will attempt
     * to look up an instance of it by type in the application context during
     * initialization. If none is found, no custom flow initialization will be
     * performed.
     *
     * @param flowExecutionInterceptor
     */
    public void setFlowExecutionInterceptor(final FlowExecutionInterceptor flowExecutionInterceptor) {
        this.flowExecutionInterceptor = flowExecutionInterceptor;
    }

    /**
     * Sets the flow state interceptor used to provide "around" advice around
     * each state execution of each flow. If not set, the controller will
     * attempt to look up an instance of it by type in the application context
     * during initialization. If none is found, no custom state interception
     * will be performed.
     *
     * @param stateExecutionInterceptor
     */
    public void setStateExecutionInterceptor(final StateExecutionInterceptor stateExecutionInterceptor) {
        this.stateExecutionInterceptor = stateExecutionInterceptor;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // Try to autodiscover a script storage, flow state storage, flow
        // execution interceptor, and flow state interceptor in the context if
        // they're not explicitly set. Create default instances of script
        // storage and flow state storage if none found.
        final ApplicationContext ctx = getApplicationContext();
        if (scriptStorage == null) {
            scriptStorage = createDefaultScriptStorage(ctx);
        }
        if (flowStateStorage == null) {
            flowStateStorage = BeanFactoryUtilsEx.beanOfTypeIncludingAncestors(ctx, FlowStateStorage.class);
            if (flowStateStorage == null) {
                flowStateStorage = new HttpSessionFlowStateStorage();
                final HttpSessionFlowStateStorage hflowStateStorage = (HttpSessionFlowStateStorage) flowStateStorage;
                hflowStateStorage.setApplicationContext(getApplicationContext());
                hflowStateStorage.setScriptStorage(scriptStorage);
                hflowStateStorage.afterPropertiesSet();
            }
        }
        if (flowExecutionInterceptor == null) {
            flowExecutionInterceptor = BeanFactoryUtilsEx.beanOfTypeIncludingAncestors(ctx,
                    FlowExecutionInterceptor.class);
        }
        if (scriptSelectionStrategy == null) {
            final UrlScriptSelectionStrategy dss = new UrlScriptSelectionStrategy();
            dss.setUseServletPath(true);
            scriptSelectionStrategy = dss;
        }
        // Since we can't guarantee initialization order, make sure that we're
        // using the same script storage.
        if (flowStateStorage instanceof AbstractFlowStateStorage) {
            final AbstractFlowStateStorage pfss = (AbstractFlowStateStorage) flowStateStorage;
            final ScriptStorage otherScriptStorage = pfss.getScriptStorage();
            if (otherScriptStorage == null) {
                pfss.setScriptStorage(scriptStorage);
            } else if (otherScriptStorage != scriptStorage) {
                throw new BeanInitializationException("Persistent state storage uses a different script storage");
            }
        }
    }

    public static ScriptStorage createDefaultScriptStorage(final ApplicationContext ctx) throws Exception {
        ScriptStorage scriptStorage = BeanFactoryUtilsEx.beanOfTypeIncludingAncestors(ctx, ScriptStorage.class);
        if (scriptStorage == null) {
            scriptStorage = new ScriptStorage();
            scriptStorage.setResourceLoader(ctx);
            scriptStorage.afterPropertiesSet();
            if (ctx instanceof ConfigurableApplicationContext) {
                final ConfigurableListableBeanFactory bf = ((ConfigurableApplicationContext) ctx).getBeanFactory();
                bf.registerSingleton("scriptStorage", scriptStorage);
            }
        }
        return scriptStorage;
    }

    /**
     * <p>
     * First, the controller determines if there is a request parameter named
     * "stateId" and if it contains a valid state ID. If so, it continues the
     * execution of the associated flowscript at the point where it was waiting.
     * If state ID is not present or not valid, the controller will look up a
     * script to execute, by concatenating resourcePath with neither, one, or
     * both of the servlet path and path info portions of the request URI,
     * depending on the values of the usePathInfo and useServletPath properties.
     * In case the servlet was invoked as a result of a
     * {@link javax.servlet.RequestDispatcher#include(javax.servlet.ServletRequest, javax.servlet.ServletResponse)}
     * call, the controller will correctly use the request attributes
     * <tt>javax.servlet.include.servlet_path</tt> and
     * <tt>javax.servlet.include.path_info</tt> instead of the request's proper
     * servlet path and path info. All flowscripts have access to the following
     * built-in objects:
     * </p>
     * <p>
     * <table border="1" summary="Built-in Objects">
     * <tr>
     * <th>Name</th>
     * <th>Object</th>
     * </tr>
     * <tr>
     * <td><tt>request</tt></td>
     * <td>the HttpServletRequest object</td>
     * </tr>
     * <tr>
     * <td><tt>response</tt></td>
     * <td>the HttpServletResponse object</td>
     * </tr>
     * <tr>
     * <td><tt>servletContext</tt></td>
     * <td>the ServletContext object</td>
     * </tr>
     * <tr>
     * <td><tt>applicationContext</tt></td>
     * <td>the ApplicationContext object</td>
     * </tr>
     * </table>
     * They also have access to following built-in functions:
     * <table border="1" summary="Built-in Functions">
     * <tr>
     * <th>Function</th>
     * <th>Purpose</th>
     * </tr>
     * <tr>
     * <td><tt>include(<i>path</i>)</tt></td>
     * <td>includes a script referenced with the specified path as if it was
     * executed at the point of inclusion. You can use it to conveniently
     * include reusable function modules. For absolute pathnames (relative to
     * the root of the namespace of the resource loader configured in the
     * associated script storage), start the path with <tt>/</tt>. Otherwise,
     * the paths are interpreted as relative to the including script. In
     * relative paths, you can use any number of <tt>../</tt> components at the
     * start of the path to refer to parent directories. Relative paths were
     * introduced in 1.1.1 release. For compatibility with older releases that
     * only supported absolute paths, if a relative path can not be resolved to
     * an existing script, the system will also try to resolve it as an
     * old-format absolute path.</td>
     * </tr>
     * <tr>
     * <td><tt>respond(<i>viewName</i>, <i>model</i>)</tt></td>
     * <td>respond to the actual HTTP request with the specified view name and
     * the specified model. The view is resolved using the Spring's view
     * resolution mechanism. The model can be any Rhino Scriptable instance
     * (including <tt>this</tt> to pass all script variables to the model), the
     * controller will take care of fitting a Map interface to it to conform to
     * Spring model requirements. The actual sending of the response will not
     * happen until the script either terminates, or calls the <tt>wait()</tt>
     * function. Another invocation of the function before the actual sending of
     * the reponse happens will overwrite a previous one. The script can pass
     * <tt>null</tt> for the view name to indicate it handled the response
     * completely on its own and no view needs to be invoked.</td>
     * </tr>
     * <tr>
     * <td><tt>wait()</tt></td>
     * <td>send the response and then waits for the next HTTP request. The
     * function will return when another HTTP request for that flow is made.
     * Upon returning from this function, the variables <tt>request</tt> and
     * <tt>response</tt> will have new values, therefore scripts should not
     * store references to them across waits. When <tt>wait()</tt> is invoked,
     * an additional variable named "stateId" is placed into the model. The view
     * should pass this id to the client, who should specify it in a request
     * parameter also named "stateId" in the next HTTP request to continue the
     * flow.</td>
     * </tr>
     * <tr>
     * <td><tt>respondAndWait(<i>viewName</i>, <i>model</i>)</tt></td>
     * <td>conveniently combines the <tt>respond()</tt> and <tt>wait()</tt>
     * functions into a single function.</td>
     * </tr>
     * <tr>
     * <td><tt>isGoingToWait()</tt></td>
     * <td>returns true if the script is about to go waiting as a result of a
     * <tt>wait()</tt> call. All open finally blocks are executed in Rhino
     * whenever <tt>wait()</tt> is called. This function can be used in finally
     * blocks to distinguish between control flow exiting the block for the last
     * time (really "finally") and control flow exiting the block because of
     * wait. I.e. the typical use is:
     *
     * <pre>
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
     * </pre>
     *
     * </td>
     * </tr>
     * </table>
     */
    @Override
    protected ModelAndView handleRequestInternal(final HttpServletRequest request, final HttpServletResponse response)
            throws Exception {
        final NativeContinuation continuation = getState(request);
        final Context cx = Context.getCurrentContext();
        if (cx == null) {
            // No context - we're not running within
            // OpenContextInViewInterceptor.
            // Open our own context only for the duration of the controller.
            final ContextAction cxa = new ContextAction() {
                @Override
                public Object run(final Context cx) {
                    try {
                        return handleRequestInContext(request, response, continuation, cx);
                    } catch (final ModelAndViewDefiningException e) {
                        return e.getModelAndView();
                    } catch (final RuntimeException e) {
                        throw e;
                    } catch (final Exception e) {
                        throw new UndeclaredThrowableException(e);
                    }
                }
            };
            return (ModelAndView)contextFactoryHolder.getContextFactory().call(cxa);
        }
        // Have a context associated with the thread - we're probably
        // running within OpenContextInViewInterceptor. Just use it.
        return handleRequestInContext(request, response, continuation, cx);
    }

    private NativeContinuation getState(final HttpServletRequest request) {
        final String strId = request.getParameter(STATEID_KEY);
        if (strId == null) {
            return null;
        }
        return flowStateStorage.getState(request, strId);
    }

    private ModelAndView handleRequestInContext(final HttpServletRequest request, final HttpServletResponse response,
            final NativeContinuation continuation, final Context cx) throws Exception {
        final ScriptableObject scope;
        if (continuation == null) {
            scope = scriptStorage.createNewTopLevelScope(cx);
        } else {
            scope = (ScriptableObject) ScriptableObject.getTopLevelScope(continuation);
        }
        final HostObject hostObject = (HostObject) cx.newObject(scope, "HostObject");
        hostObject.setScriptStorage(scriptStorage);
        ScriptableObject.defineProperty(scope, HOST_PROPERTY, hostObject, HIDDEN | UNMODIFIABLE);
        ScriptableObject.defineProperty(scope, REQUEST_PROPERTY, request, UNMODIFIABLE);
        ScriptableObject.defineProperty(scope, RESPONSE_PROPERTY, response, UNMODIFIABLE);
        ScriptableObject.defineProperty(scope, SERVLETCONTEXT_PROPERTY, getServletContext(), UNMODIFIABLE);
        ScriptableObject.defineProperty(scope, APPLICATIONCONTEXT_PROPERTY, getApplicationContext(), UNMODIFIABLE);
        cx.setOptimizationLevel(-1);
        NativeContinuation newContinuation = null;
        if (continuation == null) {
            final String scriptPath = scriptSelectionStrategy.getScriptPath(request);
            if (scriptPath == null) {
                // Couldn't select script
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return null;
            }
            final String scriptDirectory = HostObject.getDirectoryForScript(scriptPath);
            hostObject.setCurrentScriptDirectory(scriptDirectory);
            ScriptableObject.defineProperty(scope, SCRIPT_DIR_PROPERTY, scriptDirectory, UNMODIFIABLE);
            final Script script;
            try {
                script = scriptStorage.getScript(scriptPath);
            } catch (final FileNotFoundException e) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return null;
            }
            if (script == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return null;
            }
            if (flowExecutionInterceptor != null) {
                flowExecutionInterceptor.beforeFlowExecution(request, scriptPath, cx, scope);
            }
            try {
                if (stateExecutionInterceptor != null) {
                    stateExecutionInterceptor.aroundStateExecution(new Script() {
                        @Override
                        public Object exec(final Context cx, final Scriptable scope) {
                            return cx.executeScriptWithContinuations(script, scope);
                        }
                    }, cx, scope);
                } else {
                    cx.executeScriptWithContinuations(script, scope);
                }
            } catch (final ContinuationPending e) {
                newContinuation = (NativeContinuation) e.getContinuation();
            } catch (final Exception e) {
                afterFlowExecution(request, cx, scope, e);
                throw e;
            }
        } else {
            hostObject.setCurrentScriptDirectory(
                    String.valueOf(ScriptableObject.getProperty(scope, SCRIPT_DIR_PROPERTY)));
            try {
                if (stateExecutionInterceptor != null) {
                    stateExecutionInterceptor.aroundStateExecution(new Script() {
                        @Override
                        public Object exec(final Context cx, final Scriptable scope) {
                            return cx.resumeContinuation(continuation, scope, null);
                        }
                    }, cx, scope);
                } else {
                    cx.resumeContinuation(continuation, scope, null);
                }
            } catch (final ContinuationPending e) {
                newContinuation = (NativeContinuation) e.getContinuation();
            } catch (final Exception e) {
                afterFlowExecution(request, cx, scope, e);
                throw e;
            }
        }
        deleteProperty(scope, APPLICATIONCONTEXT_PROPERTY);
        deleteProperty(scope, SERVLETCONTEXT_PROPERTY);
        deleteProperty(scope, RESPONSE_PROPERTY);
        deleteProperty(scope, REQUEST_PROPERTY);
        deleteProperty(scope, HOST_PROPERTY);
        Object id;
        if (newContinuation != null) {
            id = flowStateStorage.storeState(request, newContinuation);
        } else {
            id = null;
            afterFlowExecution(request, cx, scope, null);
        }
        return hostObject.getModelAndView(id);
    }

    private void afterFlowExecution(final HttpServletRequest request, final Context cx, final ScriptableObject scope,
            final Exception cause) throws Exception {
        if (flowExecutionInterceptor != null) {
            flowExecutionInterceptor.afterFlowExecution(request, cx, scope, cause);
        }
    }

    private static void deleteProperty(final ScriptableObject object, final String property) {
        object.setAttributes(property, 0);
        ScriptableObject.deleteProperty(object, property);
    }
}
