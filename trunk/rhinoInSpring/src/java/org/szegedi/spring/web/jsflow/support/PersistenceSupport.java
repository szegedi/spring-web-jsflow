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

import java.io.IOException;

import org.mozilla.javascript.ScriptableObject;
import org.szegedi.spring.web.jsflow.ScriptStorage;

/**
 * An implementation class, used to expose implementation details required for
 * persistence from {@link org.szegedi.spring.web.jsflow.ScriptStorage}. Not
 * intended for client use.
 * @author Attila Szegedi
 * @version $Id: $
 */
public abstract class PersistenceSupport
{
    protected abstract ScriptableObject getLibrary();
    protected abstract Object getFunctionStub(Object function);
    protected abstract Object resolveFunctionStub(Object stub) throws IOException;
}
