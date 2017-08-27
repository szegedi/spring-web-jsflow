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
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.springframework.web.servlet.ModelAndView;

/**
 * An internal host object used to implement built-in functions for scripts. You
 * should never use it directly. It is public only as an implementation detail.
 *
 * @author Attila Szegedi
 * @version $Id$
 */
public class HostObject extends ScriptableObject {
    public static final String CLASS_NAME = "HostObject";

    private static final long serialVersionUID = 1L;
    private ScriptStorage scriptStorage;
    private boolean isGoingToWait;
    private Scriptable model;
    private String viewName;
    private String currentScriptDirectory;

    public HostObject() {
        scriptStorage = null;
    }

    void setCurrentScriptDirectory(final String currentScriptDirectory) {
        this.currentScriptDirectory = currentScriptDirectory;
    }

    void setScriptStorage(final ScriptStorage scriptStorage) {
        this.scriptStorage = scriptStorage;
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    public void jsFunction_inspect(final Scriptable obj) {
        System.out.println(obj);
    }

    public void jsFunction_respond(final String viewName, final Scriptable model) {
        this.viewName = "undefined".equals(viewName) ? null : viewName;
        this.model = model;
    }

    public void jsFunction_wait() {
        isGoingToWait = true;
        throw Context.getCurrentContext().captureContinuation();
    }

    public boolean jsGet_isGoingToWait() {
        return isGoingToWait;
    }

    ModelAndView getModelAndView(final Object continuationId) {
        if (model != null) {
            model.put(FlowController.STATEID_KEY, model, continuationId);
        }
        return viewName == null ? null : new ModelAndView(viewName, new ScriptableMap(model));
    }

    public void jsFunction_include(final Scriptable scope, String scriptName) throws Exception {
        final Context cx = Context.getCurrentContext();
        if (scriptName.charAt(0) != '/') {
            // relative script name -- resolve it against currently executing
            // script's directory
            String pathPrefix = currentScriptDirectory;
            while (scriptName.startsWith("../")) {
                final int lastSlash = pathPrefix.lastIndexOf('/');
                if (lastSlash == -1) {
                    throw new FileNotFoundException("script:" + currentScriptDirectory + '/' + scriptName);
                }
                scriptName = scriptName.substring(3);
                pathPrefix = pathPrefix.substring(0, lastSlash);
            }
            scriptName = pathPrefix + '/' + scriptName;
        } else {
            // strip off leading slash
            scriptName = scriptName.substring(1);
        }
        final Script script = scriptStorage.getScript(scriptName);
        if (script == null) {
            throw new FileNotFoundException("script:" + scriptName);
        }
        try {
            final String oldScriptDirectory = currentScriptDirectory;
            currentScriptDirectory = getDirectoryForScript(scriptName);
            try {
                script.exec(cx, scope);
            } finally {
                currentScriptDirectory = oldScriptDirectory;
            }
        } finally {
        }
    }

    static String getDirectoryForScript(final String scriptName) {
        final int lastSlash = scriptName.lastIndexOf('/');
        return lastSlash == -1 ? "" : scriptName.substring(0, lastSlash);
    }
}
