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
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
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
    private ResourceLoader resourceLoader;
    private String prefix = "";
    private String[] globalLibraries; 
    private long noStaleCheckPeriod = 10000;
    private final Map scripts = new HashMap();
    private Map functionsToStubs = Collections.EMPTY_MAP;
    private Map stubsToFunctions = Collections.EMPTY_MAP;
    private final Object lock = new Object();

    private final ScriptableObject library = new NativeObject();
    
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
     * Specifies the name of an application-specific global library script. 
     * This script will execute only once, during the initialization of the
     * script storage, in the global library scope. Therefore, it mostly makes
     * sense to only place function definitions into it.
     * @param globalLibrary the name of the application-specific global library
     * script.
     */
    public void setGlobalLibrary(String globalLibrary)
    {
        this.globalLibraries = new String[] { globalLibrary };
    }
    
    /**
     * Specifies the names of application-specific global library scripts. 
     * These scripts will execute only once, in the order specified in the 
     * array, during the initialization of the script storage, in the global 
     * library scope. Therefore, it mostly makes sense to only place function 
     * definitions into them.
     * @param globalLibraries an array of names of the application-specific 
     * global library scripts.
     */
    public void setGlobalLibraries(String[] globalLibraries)
    {
        this.globalLibraries = (String[])globalLibraries.clone();
    }
    
    public void afterPropertiesSet() throws Exception
    {
        Context.call(new ContextAction()
        {
            public Object run(Context cx)
            {
                try
                {
                    cx.setOptimizationLevel(-1);
                    // Run the built-in library script
                    Script libraryScript = loadScript(new ClassPathResource(
                            "library.js", ScriptStorage.class), "~library.js");
                    cx.initStandardObjects(library);
                    libraryScript.exec(cx, library);
                    ScriptableObject.defineClass(library, HostObject.class);
                    
                    // Run application-defined global libraries in the library
                    // scope
                    if(globalLibraries != null)
                    {
                        for (int i = 0; i < globalLibraries.length; i++)
                        {
                            getScript(globalLibraries[i]).exec(cx, library);
                        }
                    }
                    
                    // bit of a hack to initialize all lazy objects that 
                    // ScriptableOutputStream would initialize, so we can then
                    // safely seal it
                    new ScriptableOutputStream(new ByteArrayOutputStream(), 
                            library);
                    // Finally seal the library scope
                    library.sealObject();
                }
                catch(RuntimeException e)
                {
                    throw e;
                }
                catch(Error e)
                {
                    throw e;
                }
                catch(Throwable t)
                {
                    throw new UndeclaredThrowableException(t);
                }
                return null;
            }
        });
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

            protected Object resolveFunctionStub(Object stub) throws IOException
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
    
    Script getScript(String path) throws IOException
    {
        TimestampedScript ts;
        synchronized(scripts)
        {
            ts = (TimestampedScript)scripts.get(path);
            if(ts == null)
            {
                ts = new TimestampedScript(path);
                scripts.put(path, ts);
            }
        }
        return ts.getScript();
    }

    private class TimestampedScript
    {
        private final String path;
        private long lastModified;
        private long lastChecked;
        private Script script;
        
        TimestampedScript(String path)
        {
            this.path = path;
        }
        
        synchronized Script getScript() throws IOException
        {
            long now = System.currentTimeMillis();
            if(script != null && now - lastChecked < noStaleCheckPeriod)
            {
                return script;
            }
            Resource resource = resourceLoader.getResource(prefix + path);
            URL url;
            try
            {
                url = resource.getURL();
            }
            catch(IOException e)
            {
                url = null;
            }
            long newLastModified;
            URLConnection conn;
            if(url != null)
            {
                if("file".equals(url.getProtocol()))
                {
                    newLastModified = resource.getFile().lastModified();
                    conn = null;
                }
                else
                {
                    conn = url.openConnection();
                    newLastModified = conn.getLastModified();
                }
            }
            else
            {
                newLastModified = 0;
                conn = null;
            }
            lastChecked = now;
            if(script == null || newLastModified != lastModified)
            {
                lastModified = newLastModified;
                InputStream in = conn == null ? resource.getInputStream() : 
                    conn.getInputStream();
                try
                {
                    script = loadScript(in, resource.getDescription(), path);
                }
                finally
                {
                    in.close();
                }
            }
            else if(conn != null)
            {
                conn.getInputStream().close();
            }
            return script;
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
            return loadScript(in, resource.getDescription(), path);
        }
        finally
        {
            in.close();
        }
    }
    
    private Script loadScript(final InputStream in, String description, 
            String path)
    throws IOException
    {
        final Reader r = new InputStreamReader(in);
        try
        {
            Script script = Context.getCurrentContext().compileReader(r, 
                    description, 1, null);
            new FunctionStubFactory(path).createStubs(script);
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
