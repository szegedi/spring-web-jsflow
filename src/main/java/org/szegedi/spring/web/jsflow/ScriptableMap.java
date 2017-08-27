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
 *
 * @author Attila Szegedi
 * @version $Id$
 */
@SuppressWarnings("rawtypes")
class ScriptableMap extends AbstractMap {
    private final Scriptable s;
    private Set keys;
    private Set entries;

    public ScriptableMap(final Scriptable s) {
        this.s = s;
    }

    @Override
    public boolean containsKey(final Object key) {
        if (key instanceof Number) {
            return ScriptableObject.hasProperty(s, ((Number) key).intValue());
        }
        return ScriptableObject.hasProperty(s, String.valueOf(key));
    }

    @Override
    public Set entrySet() {
        if (entries == null) {
            entries = createEntrySet();
        }
        return entries;
    }

    private Set createEntrySet() {
        return new AbstractSet() {
            @Override
            public int size() {
                return keySet().size();
            }

            @Override
            public Iterator iterator() {
                return new Iterator() {
                    private final Iterator i = keySet().iterator();

                    @Override
                    public boolean hasNext() {
                        return i.hasNext();
                    }

                    @Override
                    public Object next() {
                        final Object key = i.next();
                        return new Map.Entry() {
                            @Override
                            public Object getKey() {
                                return key;
                            }

                            @Override
                            public Object getValue() {
                                return ScriptableMap.this.get(key);
                            }

                            @Override
                            public Object setValue(final Object value) {
                                return ScriptableMap.this.put(key, value);
                            }

                            @Override
                            public boolean equals(final Object o) {
                                if (o instanceof Map.Entry) {
                                    final Map.Entry e = (Map.Entry) o;
                                    return isEqual(key, e.getKey()) && isEqual(getValue(), e.getValue());
                                }
                                return false;
                            }

                            @Override
                            public int hashCode() {
                                return key == null ? 0 : key.hashCode();
                            }
                        };
                    }

                    @Override
                    public void remove() {
                        i.remove();
                    }
                };
            }
        };
    }

    @Override
    public Object get(final Object key) {
        Object retval;
        if (key instanceof Number) {
            retval = ScriptableObject.getProperty(s, ((Number) key).intValue());
        } else {
            retval = ScriptableObject.getProperty(s, String.valueOf(key));
        }
        return ScriptableConverter.jsToJava(retval);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object put(final Object key, final Object value) {
        keySet().add(key);
        final Object oldValue = get(key);
        if (key instanceof Number) {
            ScriptableObject.putProperty(s, ((Number) key).intValue(), value);
        } else {
            ScriptableObject.putProperty(s, String.valueOf(key), value);
        }
        return oldValue;
    }

    @Override
    public Object remove(final Object key) {
        final Object oldValue = get(key);
        keySet().remove(key);
        return oldValue;
    }

    @Override
    public Set keySet() {
        if (keys == null) {
            keys = createKeySet();
        }
        return keys;
    }

    @SuppressWarnings("unchecked")
    private Set createKeySet() {
        return new AbstractSet() {
            private final Set ikeys = new HashSet(Arrays.asList(s.getIds()));

            @Override
            public boolean add(final Object o) {
                return ikeys.add(o);
            }

            @Override
            public boolean contains(final Object o) {
                return ikeys.contains(o);
            }

            @Override
            public Iterator iterator() {
                return new Iterator() {
                    private final Iterator i = ikeys.iterator();
                    private Object lastKey;

                    @Override
                    public boolean hasNext() {
                        return i.hasNext();
                    }

                    @Override
                    public Object next() {
                        return lastKey = i.next();
                    }

                    @Override
                    public void remove() {
                        i.remove();
                        removeInternal(lastKey);
                    }
                };
            }

            @Override
            public boolean remove(final Object key) {
                final boolean x = ikeys.remove(key);
                removeInternal(key);
                return x;
            }

            private void removeInternal(final Object key) {
                if (key instanceof Number) {
                    ScriptableObject.deleteProperty(s, ((Number) key).intValue());
                } else {
                    ScriptableObject.deleteProperty(s, String.valueOf(key));
                }
            }

            @Override
            public int size() {
                return ikeys.size();
            }
        };
    }

    private static boolean isEqual(final Object o1, final Object o2) {
        if (o1 == null) {
            return o2 == null;
        }
        return o1.equals(o2);
    }
}
