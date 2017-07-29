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
package org.szegedi.spring.web.jsflow.codec;

import java.lang.ref.Reference;
import org.szegedi.spring.support.SoftPooledFactory;
import org.szegedi.spring.web.jsflow.codec.support.OneWayCodec;

/**
 * A codec that can pool and reuse {@link OneWayCodec} instances of another
 * codec it wraps. The pool uses soft references, and therefore plays nicely
 * with regard to memory. It is recommended with codecs that can have high 
 * initialization overhead, i.e. a 
 * {@link org.szegedi.spring.web.jsflow.codec.ConfidentialityCodec} using 
 * password-based encryption, or a 
 * {@link org.szegedi.spring.web.jsflow.codec.IntegrityCodec}.
 * When more than one of these are combined into a 
 * {@link org.szegedi.spring.web.jsflow.codec.CompositeCodec}, it is a good idea
 * to wrap the composite codec with a pooled codec, instead of wrapping the
 * individual component codecs.
 * @author Attila Szegedi
 * @version $Id$
 */
public class PooledCodec implements BinaryStateCodec
{
    private BinaryStateCodec binaryStateCodec;
    
    public void setBinaryStateCodec(final BinaryStateCodec binaryStateCodec)
    {
        this.binaryStateCodec = binaryStateCodec;
    }
    
    private final SoftPooledFactory decoderFactory = new SoftPooledFactory()
    {
        protected Object create() throws Exception
        {
            return binaryStateCodec.createDecoder();
        }
    };

    private final SoftPooledFactory encoderFactory = new SoftPooledFactory()
    {
        protected Object create() throws Exception
        {
            return binaryStateCodec.createEncoder();
        }
    };
    
    public OneWayCodec createDecoder() throws Exception
    {
        return new OneWayCodec()
        {
            public byte[] code(final byte[] data) throws Exception
            {
                return transcode(decoderFactory, data);
            }
        };
    }
    
    public OneWayCodec createEncoder() throws Exception
    {
        return new OneWayCodec()
        {
            public byte[] code(final byte[] data) throws Exception
            {
                return transcode(encoderFactory, data);
            }
        };
    }
    
    private static byte[] transcode(final SoftPooledFactory factory, final byte[] data) throws Exception
    {
        for(;;)
        {
            final Reference ref = factory.get();
            final OneWayCodec codec = (OneWayCodec)ref.get();
            if(codec != null)
            {
                try
                {
                    return codec.code(data);
                }
                finally
                {
                    factory.put(ref);
                }
            }
        }
    }
}
