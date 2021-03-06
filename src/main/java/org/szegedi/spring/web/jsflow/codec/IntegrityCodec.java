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

import java.security.KeyPair;
import java.security.Signature;
import java.util.Random;
import org.springframework.beans.factory.InitializingBean;
import org.szegedi.spring.web.jsflow.FlowStateStorageException;
import org.szegedi.spring.web.jsflow.codec.support.OneWayCodec;

/**
 * A codec that will add a digital signature to the flowstate when encoding, and
 * check the validity of the signature (and strip it) upon decoding. If the
 * signature is not valid, it will throw a
 * {@link org.szegedi.spring.web.jsflow.FlowStateStorageException}. It is highly
 * recommended to use this codec with
 * {@link org.szegedi.spring.web.jsflow.ClientSideFlowStateStorage} as it
 * prevents the client from tampering the state.
 * 
 * @author Attila Szegedi
 * @version $Id$
 */
public class IntegrityCodec implements BinaryStateCodec, InitializingBean {
    private KeyPair keyPair;
    private String signatureAlgorithmName;
    private int signatureLength;

    /**
     * Sets the pair of a matching private and public key used to sign the
     * serialized webflow states and to check the signature validity. You can
     * use a {@link org.szegedi.spring.crypto.GeneratedKeyPairFactory}, or even
     * better a {@link org.szegedi.spring.crypto.KeyStoreKeyPairFactory} to
     * obtain a key pair.
     * 
     * @param keyPair
     *            the signing/verifying keypair.
     */
    public void setKeyPair(final KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    /**
     * Sets the name of the signature algorithm. Defaults to "SHA1With" + the
     * key algorithm name, i.e. "SHA1WithRSA".
     * 
     * @param signatureAlgorithmName
     *            the signature algorithm name
     */
    public void setSignatureAlgorithmName(final String signatureAlgorithmName) {
        this.signatureAlgorithmName = signatureAlgorithmName;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (signatureAlgorithmName == null) {
            signatureAlgorithmName = "SHA1With" + keyPair.getPublic().getAlgorithm();
        }
        testKeys();
    }

    private void testKeys() throws Exception {
        final Random r = new Random();
        final byte[] b = new byte[1024];
        r.nextBytes(b);

        final Signature sign = Signature.getInstance(signatureAlgorithmName);
        sign.initSign(keyPair.getPrivate());
        sign.update(b);

        final Signature verify = Signature.getInstance(signatureAlgorithmName);
        verify.initVerify(keyPair.getPublic());
        verify.update(b);

        final byte[] signature = sign.sign();
        if (!verify.verify(signature)) {
            throw new IllegalArgumentException("Public and private key don't match");
        }
        signatureLength = signature.length;
    }

    @Override
    public OneWayCodec createDecoder() throws Exception {
        final Signature signature = Signature.getInstance(signatureAlgorithmName);
        signature.initVerify(keyPair.getPublic());

        return new OneWayCodec() {

            @Override
            public byte[] code(final byte[] data) throws Exception {
                final int dataLen = data.length - signatureLength;
                signature.update(data, 0, dataLen);
                if (!signature.verify(data, dataLen, signatureLength)) {
                    throw new FlowStateStorageException("Invalid signature");
                }
                final byte[] b = new byte[dataLen];
                System.arraycopy(data, 0, b, 0, dataLen);
                return b;
            }
        };
    }

    @Override
    public OneWayCodec createEncoder() throws Exception {
        final Signature signature = Signature.getInstance(signatureAlgorithmName);
        signature.initSign(keyPair.getPrivate());

        return new OneWayCodec() {

            @Override
            public byte[] code(final byte[] data) throws Exception {
                final int dataLen = data.length;
                final byte[] b = new byte[dataLen + signatureLength];
                System.arraycopy(data, 0, b, 0, dataLen);
                signature.update(data);
                signature.sign(b, dataLen, signatureLength);
                return b;
            }
        };
    }
}
