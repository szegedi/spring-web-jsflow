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
package org.szegedi.spring.web.jsflow.support;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InvalidObjectException;
import java.lang.reflect.Field;
import java.lang.reflect.UndeclaredThrowableException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.mozilla.javascript.continuations.Continuation;

/**
 * A class capable of calculating MD5 fingerprint sequences for continuations.
 * A MD5 fingerprint sequence consists of the MD5 fingerprint for each function
 * on the continuation's stack. A MD5 fingerprint for a function is calculated
 * from the ICode for that function. As the data structures we need to reach are
 * private parts of Rhino, we're using reflection with overriding accessibility
 * to get to them. 
 * @author Attila Szegedi
 * @version $Id: $
 */
class FunctionFingerprintManager
{
    private static final Field CALL_FRAME_PARENT;
    private static final Field CALL_FRAME_IDATA;
    private static final Field IDATA_ITS_NAME;
    private static final Field IDATA_ITS_SOURCE_FILE;
    private static final Field IDATA_ITS_ICODE;
    
    static
    {
        try
        {
            Class callFrameClass = Class.forName("org.mozilla.javascript.Interpreter$CallFrame"); 
            CALL_FRAME_PARENT = getField(callFrameClass, "parentFrame");
            CALL_FRAME_IDATA = getField(callFrameClass, "idata");
            
            Class idataClass = Class.forName("org.mozilla.javascript.InterpreterData");
            IDATA_ITS_NAME = getField(idataClass, "itsName");
            IDATA_ITS_SOURCE_FILE = getField(idataClass, "itsSourceFile");
            IDATA_ITS_ICODE = getField(idataClass, "itsICode");
        }
        catch(RuntimeException e)
        {
            throw e;
        }
        catch(Exception e)
        {
            throw new UndeclaredThrowableException(e);
        }
    }
    
    private static Map fingerprints = Collections.EMPTY_MAP;
    private static final Object lock = new Object();
    
    private FunctionFingerprintManager()
    {
    }
    
    private static Field getField(Class clazz, String name) throws Exception
    {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
    
    static Object getFingerprints(Continuation c) throws Exception
    {
        Object callFrame = c.getImplementation();
        List l = new ArrayList();
        while(callFrame != null)
        {
            l.add(getFingerprint(CALL_FRAME_IDATA.get(callFrame)));
            callFrame = CALL_FRAME_PARENT.get(callFrame);
        }
        return l.toArray(new long[l.size()][]);
    }
    
    static void checkFingerprints(Continuation c, Object objfingerprints) 
    throws Exception
    {
        long[][] fingerprints = (long[][])objfingerprints;
        Object callFrame = c.getImplementation();
        int i = 0;
        while(callFrame != null)
        {
            Object idata = CALL_FRAME_IDATA.get(callFrame);
            if(!Arrays.equals(fingerprints[i++], getFingerprint(idata)))
            {
                throw new InvalidObjectException("The function " + 
                        IDATA_ITS_NAME.get(idata) + " in script " + 
                        IDATA_ITS_SOURCE_FILE.get(idata) + " has changed");
            }
            callFrame = CALL_FRAME_PARENT.get(callFrame);
        }
    }
    
    private static long[] getFingerprint(Object idata) throws Exception
    {
        long[] fingerprint = (long[])fingerprints.get(idata);
        if(fingerprint != null)
        {
            return fingerprint;
        }
        synchronized(lock)
        {
            fingerprint = (long[])fingerprints.get(idata);
            if(fingerprint != null)
            {
                return fingerprint;
            }
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bfingerprint = digest.digest((byte[])IDATA_ITS_ICODE.get(idata));
            fingerprint = new long[bfingerprint.length / 8];
            DataInputStream din = new DataInputStream(new ByteArrayInputStream(bfingerprint));
            for (int i = 0; i < fingerprint.length; i++)
            {
                fingerprint[i] = din.readLong();
            }
            Map newFingerprints = new WeakHashMap(fingerprints);
            newFingerprints.put(idata, fingerprint);
            fingerprints = newFingerprints;
        }
        return fingerprint;
    }
}
