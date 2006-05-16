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

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.continuations.Continuation;
import org.springframework.web.servlet.ModelAndView;

/**
 * An internal host object used to implement built-in functions for scripts. You
 * should never use it directly. It is public only as an implementation detail.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class HostObject extends ScriptableObject
{
    public static final String CLASS_NAME = "HostObject";

    private static final long serialVersionUID = 1L;
    private ScriptStorage scriptStorage;
    private Continuation continuation;
    private Scriptable model;
    private ModelAndView modelAndView;
    private int includeLevel;
    
    public HostObject()
    {
        scriptStorage = null;
    }
    
    void setScriptStorage(ScriptStorage scriptStorage)
    {
        this.scriptStorage = scriptStorage;
    }
    
    public String getClassName()
    {
        return CLASS_NAME;
    }
    
    public void jsFunction_inspect(Scriptable obj)
    {
        System.out.println(obj);
    }
    
    public void jsFunction_respond(String viewName, Scriptable model)
    {
        if(viewName == "undefined")
        {
            viewName = null;
        }
        this.model = model;
        modelAndView = viewName == null ? null : new ModelAndView(viewName, 
                new ScriptableMap(model));
    }

    public void jsFunction_wait(Continuation continuation)
    {
        if(includeLevel > 0)
        {
            throw new IllegalStateException("Cannot wait from include");
        }
        this.continuation = continuation;
    }

    public boolean jsGet_hasContinuation()
    {
        return continuation != null;
    }

    ModelAndView getModelAndView(Object continuationId)
    {
        if(model != null)
        {
            model.put(FlowController.STATEID_KEY, model, continuationId);
        }
        return modelAndView;
    }
    
    Continuation getContinuation()
    {
        return continuation;
    }
    
    public void jsFunction_include(Scriptable scope, String scriptName)
    throws Exception
    {
        Context cx = Context.getCurrentContext();
        Script script = scriptStorage.getScript(scriptName);
        ++includeLevel;
        try
        {
            script.exec(cx, scope);
        }
        finally
        {
            --includeLevel;
        }
    }
}
