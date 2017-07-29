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

import java.util.AbstractList;
import org.mozilla.javascript.Scriptable;

/**
 * @author Attila Szegedi
 * @version $Id$
 */
class ScriptableList extends AbstractList
{
    private final Scriptable s;
    private int size = -1;
    
    ScriptableList(Scriptable s)
    {
        this.s = s;
    }
    
    public int size()
    {
        synchronized(this)
        {
            if(size == -1)
            {
                Object[] ids = s.getIds();
                int idsl = ids.length;
                int max = 0;
                for(int i = 0; i < idsl; ++i)
                {
                    Object id = ids[i];
                    if(id instanceof Number)
                    {
                        int nid = ((Number)id).intValue();
                        if(nid > max)
                        {
                            max = nid;
                        }
                        
                    }
                }
                size = max + 1;
            }
        }
        return size;
    }
    
    public Object get(int index)
    {
        return ScriptableConverter.jsToJava(s.get(index, s));
    }
}
