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

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * A Map interface for Scriptables. Used to convert a Scriptable model into a 
 * Map, as Spring MVC expects models to be Maps.  
 * @author Attila Szegedi
 * @version $Id$
 */
class ScriptableMap extends AbstractMap
{
    private final Scriptable s;
    private Set keys;
    private Set entries;
    
    public ScriptableMap(Scriptable s)
    {
        this.s = s;
    }
    
    public boolean containsKey(Object key)
    {
        if(key instanceof Number)
        {
            return ScriptableObject.hasProperty(s, ((Number)key).intValue());
        }
        return ScriptableObject.hasProperty(s, String.valueOf(key));
    }

    public Set entrySet()
    {
        if(entries == null)
        {
            entries = createEntrySet();
        }
        return entries;
    }
    
    private Set createEntrySet()
    {
        return new AbstractSet()
        {
            public int size()
            {
                return keySet().size();
            }
            
            public Iterator iterator()
            {
                return new Iterator()
                {
                    private final Iterator i = keySet().iterator();
                    
                    public boolean hasNext()
                    {
                        return i.hasNext();
                    }
                    
                    public Object next()
                    {
                        final Object key = i.next();
                        return new Map.Entry()
                        {
                            public Object getKey()
                            {
                                return key;
                            }
                            
                            public Object getValue()
                            {
                                return ScriptableMap.this.get(key);
                            }
                            
                            public Object setValue(Object value)
                            {
                                return ScriptableMap.this.put(key, value);
                            }
                            
                            public boolean equals(Object o)
                            {
                                if(o instanceof Map.Entry)
                                {
                                    Map.Entry e = (Map.Entry)o;
                                    return isEqual(key, e.getKey()) && isEqual(
                                            getValue(), e.getValue());
                                }
                                return false;
                            }
                            
                            public int hashCode()
                            {
                                return key == null ? 0 : key.hashCode();
                            }
                        };
                    }
                    
                    public void remove()
                    {
                        i.remove();
                    }
                };
            }
        };
    }

    public Object get(Object key)
    {
        Object retval;
        if(key instanceof Number)
        {
            retval = ScriptableObject.getProperty(s, ((Number)key).intValue());
        }
        else
        {
            retval = ScriptableObject.getProperty(s, String.valueOf(key));
        }
        return ScriptableConverter.jsToJava(retval);
    }
    
    public Object put(Object key, Object value)
    {
        keySet().add(key);
        Object oldValue = get(key);
        if(key instanceof Number)
        {
            ScriptableObject.putProperty(s, ((Number)key).intValue(), value);
        }
        else
        {
            ScriptableObject.putProperty(s, String.valueOf(key), value);
        }
        return oldValue;
    }
    
    public Object remove(Object key)
    {
        Object oldValue = get(key);
        keySet().remove(key);
        return oldValue;
   }
    
    public Set keySet()
    {
        if(keys == null)
        {
            return new AbstractSet()
            {
                private final Set ikeys = new HashSet(Arrays.asList(s.getIds()));
                
                public boolean add(Object o)
                {
                    return ikeys.add(o);
                }
                
                public boolean contains(Object o)
                {
                    return ikeys.contains(o);
                }
                
                public Iterator iterator()
                {
                    return new Iterator()
                    {
                        private final Iterator i = ikeys.iterator();
                        private Object lastKey;
                        
                        public boolean hasNext()
                        {
                            return i.hasNext();
                        }
                        
                        public Object next()
                        {
                            return lastKey = i.next();
                        }
                        
                        public void remove()
                        {
                            i.remove();
                            removeInternal(lastKey);
                        }
                    };
                }
                
                public boolean remove(Object key)
                {
                    boolean x = ikeys.remove(key);
                    removeInternal(key);
                    return x;
                }
                
                private void removeInternal(Object key)
                {
                    if(key instanceof Number)
                    {
                        ScriptableObject.deleteProperty(s, ((Number)key).intValue());
                    }
                    else
                    {
                        ScriptableObject.deleteProperty(s, String.valueOf(key));
                    }
                }
                
                public int size()
                {
                    return ikeys.size();
                }
            };
        }
        return keys;
    }
    
    private static boolean isEqual(Object o1, Object o2)
    {
        if(o1 == null)
        {
            return o2 == null;
        }
        return o1.equals(o2);
    }
}
