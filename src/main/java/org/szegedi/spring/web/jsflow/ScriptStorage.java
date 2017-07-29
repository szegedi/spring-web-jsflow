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

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InvalidObjectException;
import java.io.Reader;
import java.io.Serializable;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.debug.DebuggableScript;
import org.mozilla.javascript.serialize.ScriptableOutputStream;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.szegedi.spring.core.io.ResourceRepresentation;
import org.szegedi.spring.web.jsflow.support.PersistenceSupport;

/**
 * A loader and in-memory storage for compiled flowscripts. It is sufficient 
 * (and recommended) to have exactly one script storage per application context.
 * The storage will be used by all the flow controllers in the application 
 * context. The script storage is resource loader aware, and will use the 
 * resource loader it was made aware of for loading script source code.
 * @author Attila Szegedi
 * @version $Id$
 */
public class ScriptStorage implements ResourceLoaderAware, InitializingBean
{
    private static final String[] lazilyNames = { "RegExp", "Packages", "java",
        "getClass", "JavaAdapter", "JavaImporter", "XML", "XMLList", 
        "Namespace", "QName"        
    };
        
    private ResourceLoader resourceLoader;
    private String prefix = "";
    private LibraryCustomizer libraryCustomizer;
    private List libraryScripts;
    private SecurityDomainFactory securityDomainFactory;
    private ContextFactory contextFactory;
    private long noStaleCheckPeriod = 10000;
    private final Map scripts = new HashMap();
    private Map functionsToStubs = Collections.EMPTY_MAP;
    private Map stubsToFunctions = Collections.EMPTY_MAP;
    private final Object lock = new Object();
    private String scriptCharacterEncoding = System.getProperty("file.encoding");
    private final ScriptableObject library = new NativeObject();
    
    /**
     * Sets the character encoding used to load scripts' source code. Defaults
     * to the value of the system property <code>file.encoding</code>.
     * @param scriptCharacterEncoding
     * @since 1.2 
     */
    public void setScriptCharacterEncoding(String scriptCharacterEncoding)
    {
        this.scriptCharacterEncoding = scriptCharacterEncoding;
    }
    
    /**
     * Sets the resource loader for this storage. The resource loader will be 
     * used to load script source code. As the class implements 
     * {@link ResourceLoaderAware}, this will usually be invoked by the Spring 
     * framework to set the application context as the resource loader.
     */
    public void setResourceLoader(ResourceLoader resourceLoader)
    {
        this.resourceLoader = resourceLoader;
    }
    
    /**
     * Sets the prefix prepended to the script name to form the full path and
     * name of the resource that stores the script source code, i.e. "scripts/".
     * @param prefix the resource path prefix used for locating script source
     * code resources. Defaults to "".
     */
    public void setPrefix(String prefix)
    {
        this.prefix = prefix;
    }

    /**
     * Sets the period in milliseconds during which a script resource will not
     * be checked for staleness. Defaults to 10000, that is, if upon requesting
     * a script its file's timestamp was checked in the last 10 seconds, then 
     * it won't be checked again. If it was checked earlier than 10 seconds
     * though, then its timestamp will be checked and if it changed, the script
     * will be reloaded. Setting it to nonzero improves performance, while
     * setting it to a very large value effectively disables automatic script 
     * reloading.
     * @param noStaleCheckPeriod the period in milliseconds during which one
     * script file's timestamp is not rechecked.
     */
    public void setNoStaleCheckPeriod(long noStaleCheckPeriod)
    {
        if(noStaleCheckPeriod < 0)
        {
            throw new IllegalArgumentException("noStaleCheckPeriod < 0");
        }
        this.noStaleCheckPeriod = noStaleCheckPeriod;
    }
    
    /**
     * Sets a list of library scripts. These scripts will be executed in the
     * context of the global "library" scope that is the prototype of all 
     * conversation scopes.
     * @param libraryScripts the list of additional library scripts. Each 
     * element can be a string (will resolve to a path as per 
     * {@link #setResourceLoader(ResourceLoader)} and {@link #setPrefix(String)}),
     * a {@link Resource}, or a {@link Script}.
     * @since 1.2 
     */
    public void setLibraryScripts(List libraryScripts)
    {
        this.libraryScripts = libraryScripts;
    }

    /**
     * Sets a library customizer. A library customizer is an optional object
     * that is given the chance to customize the global "library" scope that
     * is the prototype of all conversation scopes. If you simply wish to 
     * execute further scripts that define globally available functions, you'd
     * rather want to use {@link #setLibraryScripts(List)}, as that will also
     * cause the functions defined in those scripts to be properly stubbed for
     * serialization and other clustered replication mechanisms. The customizer
     * is invoked after all library scripts have already been run.
     * @param libraryCustomizer
     */
    public void setLibraryCustomizer(LibraryCustomizer libraryCustomizer)
    {
        this.libraryCustomizer = libraryCustomizer;
    }
    
    /**
     * Sets a security domain factory for this script storage. It can be used
     * to associate Rhino security domain objects with scripts.
     * @param securityDomainFactory
     */
    public void setSecurityDomainFactory(
            SecurityDomainFactory securityDomainFactory)
    {
        this.securityDomainFactory = securityDomainFactory;
    }
    
    /**
     * Sets the context factory used to run the library script. If none is set
     * the global context factory {@link ContextFactory#getGlobal()} is used.
     * If you are using a custom context factory in either 
     * {@link FlowController} or {@link OpenContextInViewInterceptor}, you 
     * might want to explicitly specify that factory here as well, although in
     * majority of cases this is not necessary.
     * @param contextFactory
     */
    public void setContextFactory(ContextFactory contextFactory)
    {
        this.contextFactory = contextFactory;
    }
    
    public void afterPropertiesSet() throws Exception
    {
        ContextAction ca = new ContextAction() {
            public Object run(Context cx) {
                try {
                    cx.setOptimizationLevel(-1);
                    // Run the built-in library script
                    Script libraryScript = loadScript(new ClassPathResource(
                            "library.js", ScriptStorage.class), "~library.js");
                    cx.initStandardObjects(library);
                    libraryScript.exec(cx, library);
                    ScriptableObject.defineClass(library, HostObject.class);
                    // Force instantiation of lazily loaded objects
                    for (int i = 0; i < lazilyNames.length; i++) {
                        ScriptableObject.getProperty(library, lazilyNames[i]);
                    }
                    
                    if(libraryScripts != null) {
                        int i = 0;
                        for (Iterator iter = libraryScripts.iterator(); iter.hasNext(); ++i) {
                            Object scriptSpec = iter.next();
                            Script s;
                            String path;
                            if(scriptSpec instanceof String) {
                                path = (String)scriptSpec; 
                                s = getScript(path);
                            }
                            else if(scriptSpec instanceof Resource) {
                                Resource r = (Resource)scriptSpec;
                                path = r.getDescription();
                                s = loadScript(r, path);
                            }
                            else if(scriptSpec instanceof Script) {
                                s = (Script)scriptSpec;
                                path = "~libraryScript[" + i + "].js";
                                createFunctionStubs(path, s);
                            }
                            else {
                                throw new IllegalArgumentException(
                                        "libraryScripts[" + i + "] is " + 
                                        scriptSpec.getClass().getName());
                            }
                            s.exec(cx, library);
                        }
                    }

                    if(libraryCustomizer != null) {
                        libraryCustomizer.customizeLibrary(cx, library);
                    }
                    
                    // bit of a hack to initialize all lazy objects that 
                    // ScriptableOutputStream would initialize, so we can then
                    // safely seal it
                    new ScriptableOutputStream(new ByteArrayOutputStream(), 
                            library);
                    // Finally seal the library scope
                    library.sealObject();
                }
                catch(RuntimeException e) {
                    throw e;
                }
                catch(Error e) {
                    throw e;
                }
                catch(Throwable t) {
                    throw new UndeclaredThrowableException(t);
                }
                return null;
            }
        };
        if(contextFactory == null)
        {
            Context.call(ca);
        }
        else
        {
            contextFactory.call(ca);
        }
    }
    
    /**
     * Returns an object implementing support functionality for persistent flow
     * state storage. Not usable by client applications, this is meant to be 
     * used by the persistent flow state storage
     * @return the persistence support object.
     */
    public PersistenceSupport getPersistenceSupport()
    {
        return new PersistenceSupport()
        {
            protected ScriptableObject getLibrary()
            {
                return library;
            }
            
            protected Object getFunctionStub(Object function)
            {
                if(function instanceof DebuggableScript)
                {
                    Object stub = functionsToStubs.get(function);
                    if(stub == null)
                    {
                        synchronized(lock)
                        {
                            return functionsToStubs.get(function);
                        }
                    }
                    return stub;
                }
                return null;
            }

            protected Object resolveFunctionStub(Object stub) throws Exception
            {
                if(stub instanceof FunctionStub)
                {
                    Object function = stubsToFunctions.get(stub);
                    if(function == null)
                    {
                        synchronized(lock)
                        {
                            function = stubsToFunctions.get(stub);
                        }
                        if(function == null)
                        {
                            // trigger script loading
                            getScript(((FunctionStub)stub).scriptName);
                            // try again
                            function = stubsToFunctions.get(stub);
                            if(function == null)
                            {
                                synchronized(lock)
                                {
                                    function = stubsToFunctions.get(stub);
                                }
                                if(function == null)
                                {
                                    throw new InvalidObjectException(stub + " not found");
                                }
                            }
                        }
                    }
                    return function;
                }
                return null;
            }
        };
    }
    
    Script getScript(String path) throws Exception
    {
        ScriptResource script;
        synchronized(scripts)
        {
            script = (ScriptResource)scripts.get(path);
            if(script == null)
            {
                script = new ScriptResource(path);
                scripts.put(path, script);
            }
        }
        return (Script)script.getRepresentation(noStaleCheckPeriod);
    }

    private class ScriptResource extends ResourceRepresentation
    {
        private final String path;
        
        ScriptResource(String path)
        {
            super(resourceLoader.getResource(prefix + path));
            this.path = path;
        }
        
        protected Object loadRepresentation(InputStream in) throws IOException
        {
            return loadScript(in, getResource(), path);
        }
    }
    
    ScriptableObject createNewTopLevelScope(Context cx)
    {
        ScriptableObject scope = (ScriptableObject)cx.newObject(library);
        scope.setPrototype(library);
        scope.setParentScope(null);
        return scope;
    }
    
    private Script loadScript(Resource resource, String path)
    throws IOException
    {
        InputStream in = resource.getInputStream();
        try
        {
            return loadScript(in, resource, path);
        }
        finally
        {
            in.close();
        }
    }
    
    private Script loadScript(final InputStream in, Resource resource, 
            String path)
    throws IOException
    {
        final Reader r = new InputStreamReader(in, scriptCharacterEncoding);
        try
        {
            Object securityDomain = securityDomainFactory == null ? null : 
                securityDomainFactory.createSecurityDomain(resource);
            Script script = Context.getCurrentContext().compileReader(r, 
                    resource.getDescription(), 1, securityDomain);
            createFunctionStubs(path, script);
            return script;
        }
        catch(FileNotFoundException e)
        {
            return null;
        }
        catch(UndeclaredThrowableException e)
        {
            if(e.getCause() instanceof IOException)
            {
                throw (IOException)e.getCause();
            }
            throw e;
        }
    }

    private void createFunctionStubs(String path, Script script)
    {
        new FunctionStubFactory(path).createStubs(script);
    }
    
    private class FunctionStubFactory
    {
        private final String scriptName;
        private final Map newStubsToFunctions = new HashMap();
        private final Map newFunctionsToStubs = new IdentityHashMap();
        
        public FunctionStubFactory(String scriptName)
        {
            this.scriptName = scriptName;
        }
        
        void createStubs(Script script)
        {
            createStubs("", Context.getDebuggableView(script));
            synchronized(lock)
            {
                newStubsToFunctions.putAll(stubsToFunctions);
                newFunctionsToStubs.putAll(functionsToStubs);
                stubsToFunctions = newStubsToFunctions;
                functionsToStubs = newFunctionsToStubs;
            }
        }
        
        private void createStubs(String prefix, DebuggableScript fnOrScript)
        {
            FunctionStub stub = new FunctionStub(scriptName, prefix);
            newStubsToFunctions.put(stub, fnOrScript);
            newFunctionsToStubs.put(fnOrScript, stub);
            int l = fnOrScript.getFunctionCount();
            for(int i = 0; i < l; ++i)
            {
                createStubs(prefix + i + ".", fnOrScript.getFunction(i));
            }
        }
    }

    
    private static class FunctionStub implements Serializable
    {
        private static final long serialVersionUID = -6421810931770215109L;
        private final String scriptName;
        private final String functionName;
        
        FunctionStub(String scriptName, String functionName)
        {
            this.scriptName = scriptName;
            this.functionName = functionName;
        }
        
        public int hashCode()
        {
            return scriptName.hashCode() ^ functionName.hashCode();
        }
        
        public boolean equals(Object o)
        {
            if(o instanceof FunctionStub)
            {
                FunctionStub key = (FunctionStub)o;
                return key.functionName.equals(functionName) && 
                    key.scriptName.equals(scriptName); 
            }
            return false;
        }

        public String toString()
        {
            return "stub:" + scriptName + "#" + functionName;
        }
    }
}
