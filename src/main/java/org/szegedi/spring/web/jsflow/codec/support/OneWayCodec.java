/*
   Copyright 2006, 2007 Attila Szegedi

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
package org.szegedi.spring.web.jsflow.codec.support;

/**
 * Basic worker interface, encapsulating a single encoding or decoding
 * operation.
 * @author Attila Szegedi
 * @version $Id$
 */
public interface OneWayCodec
{
    /**
     * A single encoding or decoding operation. This method will always be
     * invoked on a single thread at a time (although not always on the same
     * thread). It may be invoked multiple times.
     * @param data the data to encode or decode
     * @return the encoded or decoded data
     * @throws Exception
     */
    public byte[] code(byte[] data) throws Exception;
}
