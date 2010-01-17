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

import java.security.AlgorithmParameters;
import java.security.Key;

import javax.crypto.Cipher;

import org.springframework.beans.factory.InitializingBean;
import org.szegedi.spring.web.jsflow.codec.support.OneWayCodec;

/**
 * A codec that will encrypt the flowstate when encoding, and decrypt it upon 
 * decoding. It can be used with 
 * {@link org.szegedi.spring.web.jsflow.ClientSideFlowStateStorage} when there 
 * is a concern that confidential information might be contained in the flow 
 * state. If you use it, then it is recommended to put it into a 
 * {@link org.szegedi.spring.web.jsflow.codec.CompositeCodec} and precede it 
 * with a {@link org.szegedi.spring.web.jsflow.codec.CompressionCodec}, as 
 * compression improves the security of encryption. You might also want to 
 * consider enclosing it (or the composite codec) into a 
 * {@link org.szegedi.spring.web.jsflow.codec.PooledCodec} to improve 
 * performance, especially when using some sort of password-based encryption as
 * it has high cipher initialization time requirements. Note however that if
 * you are not concerned about secrecy, but just want to prevent the client from
 * tampering with or forging a false flowstate, then you should use an
 * {@link org.szegedi.spring.web.jsflow.codec.IntegrityCodec} instead.
 * @author Attila Szegedi
 * @version $Id$
 */
public class ConfidentialityCodec implements BinaryStateCodec, InitializingBean
{
    private Key secretKey;
    private AlgorithmParameters algorithmParameters;
    private String provider;
    private String algorithm;

    /**
     * Sets the secret key used for encryption and decryption. You can obtain
     * a key using a {@link org.szegedi.spring.crypto.GeneratedSecretKeyFactory}
     * or better yet a {@link org.szegedi.spring.crypto.KeySpecSecretKeyFactory}
     * @param secretKey the secret key
     */
    public void setSecretKey(Key secretKey)
    {
        this.secretKey = secretKey;
    }
    
    /**
     * Sets any optional algorithm parameters
     * @param algorithmParameters the algorithm parameters
     */
    public void setAlgorithmParameters(AlgorithmParameters algorithmParameters)
    {
        this.algorithmParameters = algorithmParameters;
    }
    
    /**
     * Sets the name of the security provider to use. If not set, the default
     * provider is used.
     * @param provider the name of the security provider to use or null.
     */
    public void setProvider(String provider)
    {
        this.provider = provider;
    }
    
    /**
     * Sets the block chaining and padding mode, i.e. "CBC/PKCS5Padding". If not
     * set, the key's algorithm defaults are used.
     * @param chainingAndPadding the block chaining and padding mode, or null
     * for algorithm defaults.
     */
    public void setChainingAndPadding(String chainingAndPadding)
    {
        this.algorithm = chainingAndPadding;
    }
    
    public void afterPropertiesSet() throws Exception
    {
        if(algorithm == null)
        {
            algorithm = secretKey.getAlgorithm();
        }
        else
        {
            algorithm = secretKey.getAlgorithm() + "/" + algorithm;
        }
    }
    
    public OneWayCodec createDecoder() throws Exception
    {
        return createCoder(Cipher.DECRYPT_MODE);
    }
    
    public OneWayCodec createEncoder() throws Exception
    {
        return createCoder(Cipher.ENCRYPT_MODE);
    }
    
    private OneWayCodec createCoder(int mode) throws Exception
    {
        final Cipher cipher;
        if(provider == null)
        {
            cipher = Cipher.getInstance(algorithm);
        }
        else
        {
            cipher = Cipher.getInstance(algorithm, provider);
        }
        if(algorithmParameters == null)
        {
            cipher.init(mode, secretKey);
        }
        else
        {
            cipher.init(mode, secretKey, algorithmParameters);
        }
        
        return new OneWayCodec()
        {
            public byte[] code(byte[] data) throws Exception
            {
                return cipher.doFinal(data);
            }
        };
    }
}
