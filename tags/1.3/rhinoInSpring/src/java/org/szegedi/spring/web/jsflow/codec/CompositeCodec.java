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

import java.util.List;

import org.szegedi.spring.web.jsflow.codec.support.OneWayCodec;

/**
 * A codec that composes several other codecs. You will typically use it to
 * compose a {@link org.szegedi.spring.web.jsflow.codec.CompressionCodec},
 * {@link org.szegedi.spring.web.jsflow.codec.ConfidentialityCodec} and a
 * {@link org.szegedi.spring.web.jsflow.codec.IntegrityCodec} into a single 
 * codec (in this order). Note that any of these are optional, although if you 
 * are using the {@link org.szegedi.spring.web.jsflow.ClientSideFlowStateStorage}, 
 * you should at least use integrity codec to prevent the client from tampering 
 * with the state. If you use the confidentiality codec to encrypt the state as 
 * well because you are concerned about leaking out any confidential 
 * information, then use the compression codec as well as compression improves 
 * the security of the encryption. Also consider wrapping this codec into a
 * {@link org.szegedi.spring.web.jsflow.codec.PooledCodec} if you use either
 * confidentiality or integrity, as their coding operations can have high 
 * initialization overhead.
 * @author Attila Szegedi
 * @version $Id$
 */
public class CompositeCodec implements BinaryStateCodec
{
    private BinaryStateCodec[] codecs;
    
    /**
     * Sets the component codecs of this codec - each of them instance of 
     * {@link BinaryStateCodec}. The codecs are applied in the specified order
     * when encoding, and in reverse order when decoding.
     * @param codecs the component codecs.
     */
    public void setCodecs(List codecs)
    {
        this.codecs = (BinaryStateCodec[]) codecs.toArray(
                new BinaryStateCodec[codecs.size()]);
    }
    
    public OneWayCodec createDecoder() throws Exception
    {
        OneWayCodec[] oneways = new OneWayCodec[codecs.length];
        for(int i = 0; i < codecs.length; ++i)
        {
            oneways[codecs.length - i - 1] = codecs[i].createDecoder();
        }
        
        return compositeOneWayCodec(oneways);
    }
    
    public OneWayCodec createEncoder() throws Exception
    {
        OneWayCodec[] oneways = new OneWayCodec[codecs.length];
        for(int i = 0; i < codecs.length; ++i)
        {
            oneways[i] = codecs[i].createEncoder();
        }
        
        return compositeOneWayCodec(oneways);
    }

    private static OneWayCodec compositeOneWayCodec(final OneWayCodec[] codecs)
    {
        return new OneWayCodec()
        {
            public byte[] code(byte[] data) throws Exception
            {
                for(int i = 0; i < codecs.length; ++i)
                {
                    data = codecs[i].code(data);
                }
                return data;
            }
        };
    }
}
