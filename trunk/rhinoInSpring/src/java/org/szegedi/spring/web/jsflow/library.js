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
/**
 * @author Attila Szegedi
 * @version $Id: $
 */

var __terminate_interpreter__ = new Continuation();

function respondAndWait(viewName, model)
{
    this.respond(viewName, model);
    this.wait();
}

function respond(viewName, model)
{
    this.__host__.respond(viewName, model == null ? {} : model);
}

function wait()
{
    this.__host__.wait(new Continuation());
    this.__terminate_interpreter__(null);
}

/**
 * @return true if the script is about to go waiting. Currently, all open 
 * finally blocks are executed in Rhino whenever wait() is called. This function
 * can be used in finally blocks to prevent them from undesirably executing when
 * the script goes waiting. I.e. you would use:
 * try
 * {
 *     ...
 *     wait();
 *     ...
 * }
 * finally
 * {
 *     if(!isGoingToWait())
 *     {
 *         ...
 *     }
 * }
 * 
 */
function isGoingToWait()
{
    return this.__host__.hasContinuation;
}

function include(scriptPath)
{
    this.__host__.include(this, scriptPath);
}