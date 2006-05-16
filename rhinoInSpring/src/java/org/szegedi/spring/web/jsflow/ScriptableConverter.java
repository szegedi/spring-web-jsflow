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
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Wrapper;

/**
 * @author Attila Szegedi
 * @version $Id: $
 */
class ScriptableConverter
{
    static Object jsToJava(Object retval)
    {
        if(retval instanceof Scriptable)
        {
            retval = Context.jsToJava(retval, Object.class);
            if(retval instanceof NativeArray)
            {
                return new ScriptableList((NativeArray)retval);
            }
            if(retval instanceof Scriptable)
            {
                return new ScriptableMap((Scriptable)retval);
            }
            return retval;
        }
        if(retval instanceof Wrapper)
        {
            return ((Wrapper)retval).unwrap();
        }
        if(retval == Scriptable.NOT_FOUND)
        {
            return null;
        }
        return retval;
    }
}
